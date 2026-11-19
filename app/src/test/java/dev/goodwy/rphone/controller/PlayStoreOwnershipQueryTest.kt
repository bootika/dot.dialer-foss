package dev.goodwy.rphone.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStoreOwnershipQueryTest {
    @Test
    fun supersededGrantCannotRestoreNewerRevocation() {
        assertLatestWins(oldProducts = setOf("dot_premium_lifetime"), newProducts = emptySet())
    }

    @Test
    fun supersededRevocationCannotClearNewerGrant() {
        assertLatestWins(oldProducts = emptySet(), newProducts = setOf("dot_plus_lifetime"))
    }

    @Test
    fun failedHalfNeverReplacesLastOwnership() {
        var completed = false
        val query = PlayStoreOwnershipQuery({ true }) {
            completed = true
            assertNull(it)
        }
        query.accept(true, null)
        query.accept(false, setOf("dot_premium_lifetime"))
        assertEquals(true, completed)
    }

    @Test
    fun eitherCallbackOrderCompletesExactlyOnceWithItsOwnPair() {
        for (subscriptionFirst in listOf(false, true)) {
            val results = mutableListOf<PlayStoreOwnership?>()
            val query = PlayStoreOwnershipQuery({ true }, results::add)
            query.accept(subscriptionFirst, if (subscriptionFirst) setOf("dot_premium_yearly") else emptySet())
            assertEquals(0, results.size)
            query.accept(!subscriptionFirst, if (subscriptionFirst) emptySet() else setOf("dot_premium_yearly"))
            query.accept(subscriptionFirst, emptySet())
            assertEquals(listOf(PlayStoreOwnership(emptySet(), setOf("dot_premium_yearly"))), results)
        }
    }

    private fun assertLatestWins(oldProducts: Set<String>, newProducts: Set<String>) {
        var generation = 1
        val committed = mutableListOf<PlayStoreOwnership?>()
        val old = PlayStoreOwnershipQuery({ generation == 1 }, committed::add)
        old.accept(false, oldProducts)
        generation = 2
        val current = PlayStoreOwnershipQuery({ generation == 2 }, committed::add)
        current.accept(true, emptySet())
        old.accept(true, emptySet())
        assertEquals(0, committed.size)
        current.accept(false, newProducts)
        old.accept(false, oldProducts)
        assertEquals(listOf(PlayStoreOwnership(newProducts, emptySet())), committed)
    }
}
