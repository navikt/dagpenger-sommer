package no.nav.dagpenger.innsyn.parsing

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.YearMonth

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JSONParseTestClass {

    private val testDataPeter = getJSONParsed("Peter")

    @Test
    fun parseJSONToYearMonthTest() {
    }

    @Test
    fun parseJSONToDoubleTest() {
    }
}
