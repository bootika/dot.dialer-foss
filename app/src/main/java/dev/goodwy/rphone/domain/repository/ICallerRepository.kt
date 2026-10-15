package dev.goodwy.rphone.domain.repository

import dev.goodwy.rphone.modal.data.Contact

interface ICallerRepository {
    suspend fun getContactByNumber(number: String): Contact?
    suspend fun getContactByNumberInRegion(number: String, regionIso: String?): Contact? =
        getContactByNumber(number)
}
