package io.github.codenextdoor.pdfunlocker

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Locked PDFs are first tried with saved passwords; passwords that work are remembered. */
@RunWith(AndroidJUnit4::class)
class SavedPasswordsUnlockTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = PasswordStore.forApp(context)
    private val storeFile = File(context.filesDir, "saved-passwords.bin")

    @Before
    fun setUp() {
        storeFile.delete()
        Intents.init()
        stubPick("user-locked.pdf")
    }

    @After
    fun tearDown() {
        Intents.release()
        storeFile.delete()
    }

    @Test
    fun savedPasswordOpensLockedPdfByItself() {
        runBlocking { store.remember("user123") }
        tap(R.string.pick_button)
        waitFor(R.string.unlocked_with_saved)
        assertNotShown(R.string.password_intro)
    }

    @Test
    fun noMatchingSavedPasswordAsksWithoutError() {
        runBlocking { store.remember("other") }
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        assertNotShown(R.string.error_wrong_password)
    }

    @Test
    fun typedPasswordIsRememberedForNextTime() {
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        typePassword("user123")
        tap(R.string.unlock_button)
        waitFor(R.string.unlocked)
        tap(R.string.cancel)
        tap(R.string.pick_button)
        waitFor(R.string.unlocked_with_saved)
        assertEquals(listOf("user123"), runBlocking { store.load() })
    }

    @Test
    fun wrongPasswordIsNotRemembered() {
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        typePassword("nope")
        tap(R.string.unlock_button)
        waitFor(R.string.error_wrong_password)
        assertEquals(emptyList<String>(), runBlocking { store.load() })
    }

    @Test
    fun manySavedPasswordsStillFindTheRightOne() {
        rememberRightOneOldest()
        tap(R.string.pick_button)
        waitFor(R.string.unlocked_with_saved)
        assertEquals("user123", runBlocking { store.load() }.first())
    }

    @Test
    fun damagedStoreFallsBackToPassword() {
        storeFile.writeText("garbage")
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        assertFalse(storeFile.exists())
    }

    @Test
    fun rotationDuringAutoTry() {
        rememberRightOneOldest()
        tap(R.string.pick_button)
        rule.activityRule.scenario.recreate()
        waitFor(R.string.unlocked_with_saved)
    }

    private fun rememberRightOneOldest() = runBlocking {
        store.remember("user123")
        repeat(30) { store.remember("wrong-$it") }
    }

    private fun string(@StringRes id: Int) = context.getString(id)

    private fun stubPick(name: String) {
        val file = File(context.cacheDir, name).apply {
            writeBytes(instrumentation.context.assets.open(name).use { it.readBytes() })
        }
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
    }

    private fun tap(@StringRes label: Int) {
        waitFor(label)
        rule.onNode(hasText(string(label)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun typePassword(password: String) = rule.onNode(hasSetTextAction()).performTextReplacement(password)

    private fun waitFor(@StringRes id: Int) {
        val text = string(id)
        try {
            rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("\"$text\" not shown within 10 s", e)
        }
    }

    private fun assertNotShown(@StringRes id: Int) =
        assertEquals(0, rule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().size)
}
