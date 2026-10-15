package dev.goodwy.rphone.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAutoIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun resetComponent() {
        AndroidAutoIntegration.setEnabled(context, false)
        stopKoin()
    }

    @Test
    fun `integration is off until user explicitly enables it`() {
        AndroidAutoIntegration.setEnabled(context, false)
        assertFalse(AndroidAutoIntegration.isEnabled(context))

        AndroidAutoIntegration.setEnabled(context, true)

        assertTrue(AndroidAutoIntegration.isEnabled(context))
    }
}
