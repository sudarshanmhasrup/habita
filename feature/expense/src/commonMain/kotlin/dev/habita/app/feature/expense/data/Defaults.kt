package dev.habita.app.feature.expense.data

/**
 * The categories a new install starts with. Income and every limit start at 0 (no limit), so no
 * one's budget lives in the code; set them in the app's budget screen, where they stay on the
 * phone.
 */
internal object Defaults {
    const val INCOME_PAISE = 0L

    const val OTHER_ID = "other"

    /** One-time things for the home, such as a kettle or bowls, kept out of Food. */
    const val HOUSEHOLD_ID = "household"

    val categories: List<Category> = listOf(
        "home-transfer" to "Home transfer",
        "rent" to "Rent",
        "food" to "Food",
        "loan-emi" to "Loan EMI",
        "gym" to "Gym",
        "skincare" to "Skincare",
        "light-bill" to "Light bill",
        "recharge" to "Recharge & subscriptions",
        "workstation" to "Workstation",
        "commute" to "Commute",
        HOUSEHOLD_ID to "Household",
        OTHER_ID to "Other",
    ).mapIndexed { index, (id, name) -> Category(id, name, 0L, index) }
}