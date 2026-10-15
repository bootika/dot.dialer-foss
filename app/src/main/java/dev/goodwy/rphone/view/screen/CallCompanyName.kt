package dev.goodwy.rphone.view.screen

/** Keep nullable text handling outside the large composable's generated register allocation. */
internal fun displayedCallCompanyName(companyName: String?, contactName: String): String? {
    val company = companyName.orEmpty()
    if (company.isBlank() || company.equals(contactName, ignoreCase = true)) return null
    return company
}
