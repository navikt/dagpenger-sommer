package data.json

import java.time.YearMonth

data class ProcessedRequest(
        val totalIncome: Double,
        val employerSummaries: List<EmployerSummary>,
        val monthsIncomeInformation: List<MonthIncomeInformation>
)

data class EmployerSummary(
        val name: String,
        val orgID: String,
        val income: Double,
        val startMonth: YearMonth,
        val endMonth: YearMonth
)

data class MonthIncomeInformation(
        val month: YearMonth,
        val employers: List<Employer>
)

data class Employer(
        val name: String,
        val orgID: String,
        val income: Double
)