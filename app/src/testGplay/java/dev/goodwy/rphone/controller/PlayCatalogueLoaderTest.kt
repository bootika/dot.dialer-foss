package dev.goodwy.rphone.controller

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import org.junit.Assert.*
import org.junit.Test

class PlayCatalogueLoaderTest {
    @Test fun emptyCatalogueCompletesOnceAfterBothSectionsAreInitialized() {
        val initialized=mutableListOf<String>();var completed=0
        PlayCatalogueLoader {_,_,_->error("No request expected")}.load(emptyList(),emptyList(),
            {initialized.add("inapp")},{initialized.add("subs")},{assertEquals(2,initialized.size);completed++})
        assertEquals(1,completed)
    }
    @Test fun duplicateCallbacksCannotLaunchRepeatedOwnershipQueriesOrReplaceFirstCatalogue() {
        val callbacks=mutableMapOf<String,(List<ProductDetails>)->Unit>()
        var inapps=0;var subs=0;var complete=0
        PlayCatalogueLoader {_,type,done->callbacks[type]=done}.load(listOf("one"),listOf("two"),
            {inapps++},{subs++},{complete++})
        callbacks.getValue(BillingClient.ProductType.SUBS)(emptyList())
        callbacks.getValue(BillingClient.ProductType.SUBS)(emptyList())
        assertEquals(0,complete)
        callbacks.getValue(BillingClient.ProductType.INAPP)(emptyList())
        callbacks.getValue(BillingClient.ProductType.INAPP)(emptyList())
        assertEquals(1,inapps);assertEquals(1,subs);assertEquals(1,complete)
    }
}
