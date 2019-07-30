package no.nav.dagpenger.innsyn.restapi

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.mockk.mockk
import no.nav.dagpenger.innsyn.innsynAPI
import no.nav.dagpenger.innsyn.lookup.AktoerRegisterLookup
import no.nav.dagpenger.innsyn.lookup.InnsynProducer
import no.nav.dagpenger.innsyn.lookup.objects.PacketStore
import no.nav.dagpenger.innsyn.monitoring.HealthCheck

internal fun MockApi(
    packetStore: PacketStore = mockk(),
    kafkaProducer: InnsynProducer = mockk(),
    jwkProvider: JwkProvider = mockk(),
    healthChecks: List<HealthCheck> = mockk(),
    aktoerRegisterLookup: AktoerRegisterLookup = mockk()
): Application.() -> Unit {
    return fun Application.() {
        innsynAPI(packetStore, kafkaProducer, jwkProvider, healthChecks, aktoerRegisterLookup)
    }
}