package io.github.codenextdoor.pdfunlocker

import android.app.Activity
import android.app.Application
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The whole journey through the real activity, with the system file pickers stubbed. */
@RunWith(AndroidJUnit4::class)
class UnlockFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val cacheDir = instrumentation.targetContext.cacheDir
    private val out = File(cacheDir, "flow-out.pdf")

    @Before
    fun setUp() {
        Intents.init()
        out.delete()
        // A saved "user123" would unlock the test PDF by itself and skip the password steps.
        File(instrumentation.targetContext.filesDir, "saved-passwords.bin").delete()
        rule.enableAccessibilityChecks()
    }

    @After
    fun tearDown() = Intents.release()

    @Test
    fun lockedFlow() {
        stubPick("user-locked.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        typePassword("nope")
        tap(R.string.unlock_button)
        waitFor(R.string.error_wrong_password)
        typePassword("user123")
        tap(R.string.unlock_button)
        waitFor(R.string.unlocked)
        stubSave(out)
        tap(R.string.save_button)
        waitFor(R.string.saved)
        intended(allOf(hasAction(Intent.ACTION_CREATE_DOCUMENT), hasExtra(Intent.EXTRA_TITLE, "user-locked-unlocked.pdf")))
        assertOpensWithoutPassword(out)
    }

    @Test
    fun plainFlow() {
        stubPick("plain.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.not_locked)
        stubSave(out)
        tap(R.string.save_button)
        waitFor(R.string.saved)
        assertOpensWithoutPassword(out)
    }

    // The private copy of the picked PDF (cacheDir/pdfs) doesn't outlive its use.
    @Test
    fun noPrivateCopyLeftAfterSaving() {
        stubPick("plain.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.not_locked)
        stubSave(out)
        tap(R.string.save_button)
        waitFor(R.string.saved)
        assertNoPrivateCopy()
    }

    @Test
    fun noPrivateCopyLeftAfterCancel() {
        stubPick("plain.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.not_locked)
        tap(R.string.cancel)
        waitFor(R.string.pick_intro)
        assertNoPrivateCopy()
    }

    private fun assertNoPrivateCopy() =
        assertEquals(emptyList<String>(), File(cacheDir, "pdfs").list()?.toList().orEmpty())

    @Test
    fun notAPdfGoesBackToPick() {
        stubPick("not-a-pdf.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.error_not_pdf)
        waitFor(R.string.pick_button)
    }

    @Test
    fun cancelPickStaysOnPick() {
        stubCancel(Intent.ACTION_OPEN_DOCUMENT)
        tap(R.string.pick_button)
        rule.waitForIdle()
        waitFor(R.string.pick_intro)
        assertNotShown(R.string.error_not_pdf)
    }

    @Test
    fun cancelSaveStaysOnReadyToSave() {
        stubPick("plain.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.not_locked)
        stubCancel(Intent.ACTION_CREATE_DOCUMENT)
        tap(R.string.save_button)
        rule.waitForIdle()
        waitFor(R.string.save_button)
        assertNotShown(R.string.error_save_failed)
    }

    @Test
    fun rotationKeepsReadyToSave() {
        stubPick("user-locked.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        typePassword("user123")
        tap(R.string.unlock_button)
        waitFor(R.string.unlocked)
        rule.activityRule.scenario.recreate()
        waitFor(R.string.unlocked)
        stubSave(out)
        tap(R.string.save_button)
        waitFor(R.string.saved)
        assertOpensWithoutPassword(out)
    }

    @Test
    fun saveFailureShowsMessageAndAllowsRetry() {
        stubPick("plain.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.not_locked)
        stubSave(cacheDir) // a directory: can't be opened for writing
        tap(R.string.save_button)
        waitFor(R.string.error_save_failed)
        stubSave(out)
        tap(R.string.save_button)
        waitFor(R.string.saved)
        assertOpensWithoutPassword(out)
    }

    // Choosing the original itself in "Save as" (to replace the locked file) must give a good unlocked
    // copy, not an emptied or broken original.
    @Test
    fun saveOverTheOriginalReplacesItWithTheUnlockedCopy() {
        // Big enough that PdfRenderer still reads the original while writing (a tiny file is read at once).
        stubPick("user-locked-big.pdf")
        tap(R.string.pick_button)
        waitFor(R.string.password_intro)
        typePassword("user123")
        tap(R.string.unlock_button)
        waitFor(R.string.unlocked)
        stubSave(File(cacheDir, "user-locked-big.pdf"))
        tap(R.string.save_button)
        waitFor(R.string.saved)
        PdfRenderer(ParcelFileDescriptor.open(File(cacheDir, "user-locked-big.pdf"), ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(400, it.pageCount)
        }
    }

    @Test
    fun saveWithoutOpenFileAsksToPickAgain() {
        // As after the app was closed while the "Save as" dialog was open: the opened file is gone.
        val viewModel = UnlockViewModel(instrumentation.targetContext.applicationContext as Application)
        viewModel.onSaveTo(Uri.fromFile(out))
        assertEquals(UnlockState.Pick(R.string.error_pick_again), viewModel.state)
    }

    private fun string(@StringRes id: Int) = instrumentation.targetContext.getString(id)

    private fun fixture(name: String) = File(cacheDir, name).apply {
        writeBytes(instrumentation.context.assets.open(name).use { it.readBytes() })
    }

    private fun stubPick(name: String) = intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(fixture(name)))))

    // Espresso-Intents uses the most recent matching stub, so a test can re-stub.
    private fun stubSave(target: File) = intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(target))))

    private fun stubCancel(action: String) = intending(hasAction(action))
        .respondWith(ActivityResult(Activity.RESULT_CANCELED, null))

    /** Clicks through the button's click action, not a touch at its position: the keyboard moves the form. */
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

    private fun assertOpensWithoutPassword(file: File) =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(2, it.pageCount)
        }
}
