package io.github.codenextdoor.pdfunlocker

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The saved-password list passes the accessibility checks at double text, light and dark. */
@RunWith(AndroidJUnit4::class)
class SavedPasswordsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun listPassesAccessibilityChecksAtDoubleTextInLightAndDark() {
        rule.enableAccessibilityChecks()
        var passwords by mutableStateOf(emptyList<String>())
        var cleared by mutableStateOf(false)
        var dark by mutableStateOf(false)
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                AppTheme(darkTheme = dark) {
                    Surface {
                        SavedPasswordsScreen(passwords, cleared, remember { TextFieldState() }, onAdd = {}, onDelete = {}, onDone = {})
                    }
                }
            }
        }
        for (darkTheme in listOf(false, true)) {
            dark = darkTheme
            passwords = emptyList()
            cleared = false
            rule.onNodeWithText(context.getString(R.string.saved_passwords_empty)).performScrollTo().assertIsDisplayed()
            rule.onRoot().tryPerformAccessibilityChecks()

            passwords = listOf("first pw", "second pw")
            rule.onNodeWithText("first pw").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("second pw").performScrollTo().assertIsDisplayed()
            rule.onAllNodesWithContentDescription(context.getString(R.string.delete)).assertCountEquals(2)
            rule.onRoot().tryPerformAccessibilityChecks()

            cleared = true
            rule.onNodeWithText(context.getString(R.string.saved_cleared)).performScrollTo().assertIsDisplayed()
            rule.onRoot().tryPerformAccessibilityChecks()
        }
    }
}
