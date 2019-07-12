package restapi

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.exceptions.JWTDecodeException
import com.fasterxml.jackson.databind.SerializationFeature
import data.configuration.Configuration
import data.configuration.Profile
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.pipeline.PipelineContext
import mu.KLogger
import mu.KotlinLogging
import no.nav.dagpenger.streams.KafkaCredential
import org.json.simple.JSONObject
import org.slf4j.event.Level
import parsing.defaultParser
import parsing.getJSONParsed
import processing.convertInntektDataIntoProcessedRequest
import restapi.streams.Behov
import restapi.streams.InnsynProducer
import restapi.streams.InntektPond
import restapi.streams.KafkaInnsynProducer
import restapi.streams.KafkaInntektConsumer
import restapi.streams.HashMapPacketStore
import restapi.streams.PacketStore
import restapi.streams.producerConfig
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger: KLogger = KotlinLogging.logger {}

private val config = Configuration()


val lock = ReentrantLock()
val condition = lock.newCondition()
val APPLICATION_NAME = "dp-inntekt-innsyn"

fun main() {
    val jwkProvider = JwkProviderBuilder(URL(config.application.jwksUrl))
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()

    val packetStore = HashMapPacketStore(condition)

    val kafkaConsumer = KafkaInntektConsumer(config, InntektPond(packetStore)).also {
        it.start()
    }

    val kafkaProducer = KafkaInnsynProducer(producerConfig(
            APPLICATION_NAME,
            config.kafka.brokers,
            KafkaCredential("igroup", "itest")))

    val app = embeddedServer(Netty, port = config.application.httpPort) {
        innsynAPI(
                kafkaProducer,
                jwkProvider = jwkProvider,
                packetStore = packetStore
        )
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        kafkaConsumer.stop()
        app.stop(10, 60, TimeUnit.SECONDS)
    })

    app.start(wait = false)
}

fun Application.innsynAPI(
        kafkaProducer: InnsynProducer,
        jwkProvider: JwkProvider,
        packetStore: PacketStore
) {

    install(CORS) {
        method(HttpMethod.Options)
        header("MyCustomHeader")
        allowCredentials = true
        anyHost() // TODO: Don't do this in production if possible. Try to limit it.
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    if (config.application.profile == Profile.LOCAL) {
        logger.info("Not running with authentication, local build.")
    } else {
        logger.info("Running with authentication, not a local build")
        install(Authentication) {
            jwt {
                realm = "dagpenger-sommer"
                verifier(jwkProvider, config.application.jwksIssuer)
                authHeader { call ->
                    call.request.cookies["ID_token"]?.let {
                        HttpAuthHeader.Single("Bearer", it)
                    } ?: call.request.parseAuthorizationHeader()
                }
                validate { credentials ->
                        return@validate JWTPrincipal(credentials.payload)
                    }
                }
            }
        }

    install(CallLogging) {
        level = Level.INFO
    }

    routing {
        get(config.application.applicationUrl) {
            logger.info("Attempting to retrieve token")
            val idToken = call.request.cookies["nav-esso"]
            val beregningsdato: LocalDate? = try {
                LocalDate.parse(call.request.cookies["beregningsdato"], DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            catch (e: NullPointerException) { null }
            if (idToken == null) {
                logger.error("Received invalid request without ID_token", call)
                call.respond(HttpStatusCode.NotAcceptable, "Missing required cookies")
            } else if (beregningsdato == null) {
                logger.error("Received invalid request without beregningsdato", call)
                call.respond(HttpStatusCode.NotAcceptable, "Missing required cookies")
            } else if (!isValid(beregningsdato)) {
                logger.error("Submitted beregningsdato is not valid", call)
                call.respond(HttpStatusCode.NotAcceptable, "Could not validate token")
            } else {
                logger.info("Received valid ID_token, extracting actor and making requirement")
                val aktorID = getAktorIDFromIDToken(idToken, getSubject())
                mapRequestToBehov(aktorID, beregningsdato).apply {
                    logger.info(this.toString())
                    kafkaProducer.produceEvent(this)
                }.also {
                    while (!(packetStore.isDone(it.behovId))) {
                        lock.withLock {
                            condition.await(2000, TimeUnit.MILLISECONDS)
                        }
                    }
                }
                logger.info("Received a request, responding with sample text for now")
                call.respond(HttpStatusCode.OK, defaultParser.toJsonString(convertInntektDataIntoProcessedRequest(getJSONParsed("Gabriel"))))
            }
        }
    }
}

fun getAktorIDFromIDToken(idToken: String, ident: String): String {
    val response = khttp.get(
            url = config.application.aktoerregisteretUrl,
            headers = mapOf(
                    "Authorization" to idToken,
                    "Nav-Call-Id" to "dagpenger-sommer-${LocalDate.now().dayOfMonth}",
                    "Nav-Consumer-Id" to "dagpenger-sommer",
                    "Nav-Personidenter" to ident
            )
    )
    try {
        return (response.jsonObject.getJSONObject(ident).getJSONArray("identer")[0] as JSONObject).get("ident").toString()
    } catch (e: Exception) {
        logger.error("Something when wrong parsing the JSON response, e")
    }
    return ""
}

fun isValid(beregningsDato: LocalDate): Boolean {
    return beregningsDato.isAfter(LocalDate.now().minusMonths(2))
}

private fun PipelineContext<Unit, ApplicationCall>.getSubject(): String {
    return runCatching {
        call.authentication.principal?.let {
            (it as JWTPrincipal).payload.subject
        } ?: throw JWTDecodeException("Unable to get subject from JWT")
    }.getOrElse {
        logger.error(it) { "Unable to get subject" }
        return@getOrElse "UNKNOWN"
    }
}

internal fun mapRequestToBehov(aktorId: String, beregningsDato: LocalDate): Behov = Behov(
        aktørId = aktorId,
        beregningsDato = beregningsDato
)
