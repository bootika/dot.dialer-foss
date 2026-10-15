package dev.goodwy.rphone.view.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class RillIconButtonAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconButtonHasLabelActionAndMinimumTouchTarget() {
        composeRule.setContent {
            MaterialTheme {
                RillIconButton(
                    onClick = {},
                    imageVector = Icons.Outlined.Call,
                    contentDescription = "Call test contact",
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Call test contact")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
