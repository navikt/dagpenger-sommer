package processing


import com.beust.klaxon.Klaxon
import data.inntekt.InntektsInformasjon
import data.objects.Opptjeningsperiode
import parsing.doubleParser
import parsing.yearMonthParser
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.YearMonth
import kotlin.streams.toList

data class ArbeidsgiverOgInntekt(val arbeidsgiver: String, val inntekt: Double)
data class ArbeidsgiverOgPeriode(val arbeidsgiver: String, val perioder: List<Pair<YearMonth, YearMonth>>)

fun getInntektForFirstMonth(inntektData: InntektsInformasjon): Double? {
    return inntektData.inntekt.arbeidsInntektMaaned
            .first().arbeidsInntektInformasjon.inntektListe
            .first().beloep
}


fun getInntektForOneMonth(inntektData: InntektsInformasjon, yearMonth: YearMonth): Double {
    return inntektData.inntekt.arbeidsInntektMaaned
            .filter { arbeidsInntektMaaned -> arbeidsInntektMaaned.aarMaaned.equals(yearMonth) }
            .first().arbeidsInntektInformasjon.inntektListe
            .sumByDouble { inntektListe -> inntektListe.beloep }
}

fun getPeriodForEachEmployer(inntektData: InntektsInformasjon): List<ArbeidsgiverOgPeriode>? {
    return inntektData.inntekt.arbeidsInntektMaaned
            .filter { arbeidsInntektMaaned -> arbeidsInntektMaaned.aarMaaned in Opptjeningsperiode(LocalDate.now()).get36MonthRange() }
            .flatMap { arbeidsInntektMaaned ->
                arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe
                        .map { inntektListe -> Pair(inntektListe.virksomhet.identifikator, arbeidsInntektMaaned.aarMaaned) }
                        .toList()
            }
            .groupBy { pair -> pair.first }
            .mapValues { element -> element.value.map { pair -> pair.second }.toList() }
            .map { element -> ArbeidsgiverOgPeriode(element.key, groupYearMonthIntoPeriods(element.value)) }
}

fun groupYearMonthIntoPeriods(yearMonths: List<YearMonth>): List<Pair<YearMonth, YearMonth>> {
    return yearMonths
            .sorted()
            .drop(1)
            .fold(listOf(Pair(yearMonths.first(), yearMonths.first())), { list, yearMonth ->
                if (list.last().second.plusMonths(1).equals(yearMonth))
                    list.dropLast(1) + Pair(list.last().first, yearMonth)
                else list + Pair(yearMonth, yearMonth)
            })
}


fun getInntektForTheLast36LastMoths(inntektData: InntektsInformasjon): Double {
    return inntektData.inntekt.arbeidsInntektMaaned
            .filter { it.aarMaaned in Opptjeningsperiode(LocalDate.now()).get36MonthRange() }
            .sumByDouble {
                it.arbeidsInntektInformasjon.inntektListe
                        .sumByDouble { it.beloep }
            }
}

fun getInntektPerArbeidsgiverList(inntektData: InntektsInformasjon): List<ArbeidsgiverOgInntekt> {
    return inntektData.inntekt.arbeidsInntektMaaned.stream()
            .filter { it.aarMaaned in Opptjeningsperiode(LocalDate.now()).get36MonthRange() }
            .map { arbeidsInntektMaaned -> arbeidsInntektMaaned.arbeidsInntektInformasjon.inntektListe }
            .flatMap { inntektListe -> inntektListe.stream() }
            .filter { inntektListe -> inntektListe.header == "Total lønnsinntekt" }
            .map { inntektListe -> ArbeidsgiverOgInntekt(inntektListe.virksomhet.identifikator, inntektListe.beloep) }
            .toList()
}

fun getTotalInntektPerArbeidsgiver(inntektData: InntektsInformasjon): List<ArbeidsgiverOgInntekt> {
    return getInntektPerArbeidsgiverList(inntektData)
            .groupBy { it.arbeidsgiver }
            .mapValues { values ->
                values.value.stream()
                        .map { arbeidsgiverOgInntekt -> arbeidsgiverOgInntekt.inntekt }
                        .reduce { sum, inntekt -> sum + inntekt }
            }
            .mapValues { values -> values.value.get() }
            .map { (arbeidsgiver, inntekt) -> ArbeidsgiverOgInntekt(arbeidsgiver, inntekt) }
            .toList()
}

fun main() {
    println(getPeriodForEachEmployer(getJSONParsed("Peter")))
    println(getPeriodForEachEmployer(getJSONParsed("Bob")))
    println(getPeriodForEachEmployer(getJSONParsed("Gabriel")))
}

fun getJSONParsed(userName: String): InntektsInformasjon {
    return Klaxon()
            .fieldConverter(parsing.YearMonth::class, yearMonthParser)
            .fieldConverter(parsing.Double::class, doubleParser)
            .parse<InntektsInformasjon>(InputStreamReader(Files
                    .newInputStream(Paths
                            .get(("src%stest%sresources%sresults%sjson%sExpectedJSONResultForUser%s.json"
                                    .format(File.separator, File.separator, File.separator, File.separator, File.separator, userName))))))!!
}





