package dev.goodwy.rphone.controller.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/** Existing grants only, read-only acquisition; never launch a consent UI or write a contact. */
@RunWith(AndroidJUnit4::class)
class ContactsOptimizerSourceDeviceTest {
    @Test fun inspectAlreadyAuthorizedSourceConnectivity() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runOptimizerSourceProbe") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=GlobalContext.get().get<PreferenceManager>()
        val account=requireNotNull(prefs.getString(GoogleContactPhotoPreferences.ACCOUNT,null))
        val profiles=ContactOptimizationProfiles(context)
        val result=JSONObject().put("contactWrites",0).put("consentChanges",0).put("carrierCalls",0)
        if (profiles.googleAllowed(account) && prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED,false)) {
            try {
                val source=GlobalContext.get().get<CallPhotoHydrationManager>().previewSource(account)
                val outcome=source?.loadPhoto("","+40700000939")
                result.put("google",when(outcome) {
                    null, GooglePeoplePhotoResult.AuthorizationExpired -> "authorization_needed"
                    is GooglePeoplePhotoResult.Failed -> "source_failed"
                    is GooglePeoplePhotoResult.Found -> "connected_photo_available"
                    GooglePeoplePhotoResult.Ambiguous -> "connected_ambiguous"
                    GooglePeoplePhotoResult.NotFound -> "connected_no_match"
                })
            } catch(error:Exception) { result.put("google","failed_"+error.javaClass.simpleName) }
        } else result.put("google","disabled_or_not_authorized")
        if (profiles.gravatarAllowed(account)) {
            try { result.put("gravatar",if(GravatarPhotoSource().load("probe239@example.test")==null) "connected_no_match" else "connected_photo_available") }
            catch(error:Exception) { result.put("gravatar","failed_"+error.javaClass.simpleName) }
        } else result.put("gravatar","disabled_or_not_authorized")
        // A company-site request is allowed only for an actual stored eligible website.
        if(profiles.businessAllowed(account)) {
            try {
                val contacts=WhatsAppAvatarContactStore(context)
                val identities=contacts.accountIdentities(account)
                var site:String?=null
                for ((rawId,contactId) in identities) {
                    val target=contacts.chooseContact(contactId,account)?.takeIf { it.rawContactId==rawId } ?: continue
                    site=contacts.businessInfo(target)?.second
                    if(site!=null)break
                }
                result.put("company",if(site==null) "no_eligible_stored_site" else
                    if(OfficialCompanyLogoSource().load(site)==null) "no_square_icon_available" else "connected_square_icon_available")
            } catch(error:Exception) { result.put("company","failed_"+error.javaClass.simpleName) }
        } else result.put("company","disabled_or_not_authorized")
        File(context.cacheDir,"optimizer-sources-v239.json").writeText(result.toString())
    }
}
