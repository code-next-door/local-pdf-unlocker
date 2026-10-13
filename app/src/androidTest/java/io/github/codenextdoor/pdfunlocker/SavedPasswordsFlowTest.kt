package io.github.codenextdoor.pdfunlocker

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The saved-password list behind the real system prompt. A throwaway PIN is set on the test emulator for
 * each test and removed afterwards (also first, in case an earlier run died half-way).
 */
@RunWith(AndroidJUnit4::class)
class SavedPasswordsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = PasswordStore.forApp(context)

    @Before
    fun setUp() {
        clearPin()
        File(context.filesDir, "saved-passwords.bin").delete()
        device.executeShellCommand("locksettings set-pin $PIN")
    }

    @After
    fun tearDown() {
        clearPin()
        File(context.filesDir, "saved-passwords.bin").delete()
    }

    @Test
    fun listNeedsTheScreenLock() {
        runBlocking { store.remember("user123") }
        openList()
        waitForText("user123")
    }

    @Test
    fun cancelledPromptStaysOnStart() {
        runBlocking { store.remember("user123") }
        tap(R.string.saved_passwords)
        device.wait(Until.findObject(By.pkg("com.android.systemui")), 10_000)
        device.pressBack()
        waitFor(R.string.pick_intro)
        assertEquals(0, rule.onAllNodes(hasText("user123")).fetchSemanticsNodes().size)
    }

    @Test
    fun addAndDelete() {
        openList()
        waitFor(R.string.saved_passwords_empty)
        rule.onNode(hasSetTextAction()).performTextReplacement("new pw")
        tap(R.string.add_button)
        waitForText("new pw")
        assertEquals(listOf("new pw"), runBlocking { store.load() })
        rule.onNode(hasContentDescription(string(R.string.delete)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.delete_confirm)
        rule.onNode(hasText(string(R.string.delete)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.saved_passwords_empty)
        assertEquals(emptyList<String>(), runBlocking { store.load() })
    }

    @Test
    fun leavingTheAppClosesTheList() {
        runBlocking { store.remember("user123") }
        openList()
        waitForText("user123")
        // Stopped (in the background) and back, without a rotation.
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitFor(R.string.pick_intro)
        assertEquals(0, rule.onAllNodes(hasText("user123")).fetchSemanticsNodes().size)
    }

    @Test
    fun rotationKeepsListOpen() {
        runBlocking { store.remember("user123") }
        openList()
        waitForText("user123")
        rule.activityRule.scenario.recreate()
        waitForText("user123")
    }

    @Test
    fun noScreenLockExplains() {
        clearPin()
        tap(R.string.saved_passwords)
        waitFor(R.string.no_screen_lock)
    }

    private fun openList() {
        tap(R.string.saved_passwords)
        enterPinInPrompt()
        waitFor(R.string.done)
    }

    /**
     * The system prompt is another app's window: UI Automator taps the PIN there. On API 37 it's a PIN pad
     * (buttons 0-9 and Enter), not a text field.
     */
    private fun enterPinInPrompt() {
        device.wait(Until.findObject(By.res("cred_pin_pad")), 10_000) ?: run {
            val screen = java.io.ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
            screen.chunked(3_000).forEach { android.util.Log.i("SavedPasswordsFlowTest", it) }
            throw AssertionError("PIN pad of the system prompt not found (screen logged)")
        }
        PIN.forEach { digit ->
            device.findObject(By.pkg(SYSTEM_UI).clazz("android.widget.Button").text(digit.toString())).click()
        }
        device.findObject(By.res(SYSTEM_UI, "key_enter")).click()
    }

    private fun clearPin() {
        device.executeShellCommand("locksettings clear --old $PIN")
    }

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun tap(@StringRes label: Int) {
        waitFor(label)
        rule.onNode(hasText(string(label)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun waitFor(@StringRes id: Int) = waitForText(string(id))

    private fun waitForText(text: String) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("\"$text\" not shown within 10 s", e)
        }
    }

    private companion object {
        const val PIN = "1357"
        const val SYSTEM_UI = "com.android.systemui"
    }
}
