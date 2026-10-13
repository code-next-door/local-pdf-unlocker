package io.github.codenextdoor.pdfunlocker

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every step of the screen passes the Accessibility Test Framework's checks (touch targets, contrast,
 * labels) at double text size, in light and dark theme, and still shows its key text.
 */
@RunWith(AndroidJUnit4::class)
class UnlockScreenTest {
    @get:Rule val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Each state with the text that must be on screen for it. */
    private val states = listOf(
        UnlockState.Pick() to R.string.pick_intro,
        UnlockState.Pick(R.string.error_not_pdf) to R.string.error_not_pdf,
        UnlockState.Password() to R.string.password_intro,
        UnlockState.Password(wrong = true) to R.string.error_wrong_password,
        UnlockState.ReadyToSave(notLocked = true) to R.string.not_locked,
        UnlockState.ReadyToSave(notLocked = false) to R.string.unlocked,
        UnlockState.ReadyToSave(notLocked = false, saveFailed = true) to R.string.error_save_failed,
        UnlockState.Saved to R.string.saved,
    )

    @Test
    fun everyStepPassesAccessibilityChecksAtDoubleTextInLightAndDark() {
        rule.enableAccessibilityChecks()
        var state by mutableStateOf<UnlockState>(UnlockState.Pick())
        var dark by mutableStateOf(false)
        rule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                AppTheme(darkTheme = dark) {
                    Surface {
                        UnlockScreen(state, remember { TextFieldState() }, onPick = {}, onUnlock = {}, onSave = {}, onStartOver = {})
                    }
                }
            }
        }
        for (darkTheme in listOf(false, true)) {
            for ((s, text) in states) {
                dark = darkTheme
                state = s
                rule.onNodeWithText(context.getString(text)).performScrollTo().assertIsDisplayed()
                rule.onRoot().tryPerformAccessibilityChecks()
            }
            state = UnlockState.Working
            rule.onNodeWithContentDescription(context.getString(R.string.working)).assertIsDisplayed()
            rule.onRoot().tryPerformAccessibilityChecks()
        }
    }
}
