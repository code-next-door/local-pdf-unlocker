package io.github.codenextdoor.pdfunlocker

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app opened from another app (Gmail's "Open with", the Share menu) with a PDF. */
@RunWith(AndroidJUnit4::class)
class OpenWithFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun openWithLockedPdfShowsPassword() = launch(
        Intent(Intent.ACTION_VIEW).setDataAndType(TestPdfProvider.uri("user-locked.pdf"), "application/pdf"),
    ) { unlockWithRightPassword() }

    @Test
    fun shareLockedPdfShowsPassword() = launch(
        Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, TestPdfProvider.uri("user-locked.pdf")),
    ) { unlockWithRightPassword() }

    @Test
    fun fileUriIsRefused() = launch(
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/a.pdf"), "application/pdf"),
    ) { waitFor(R.string.error_not_pdf) }

    @Test
    fun rotationDoesNotReopen() = launch(
        Intent(Intent.ACTION_VIEW).setDataAndType(TestPdfProvider.uri("user-locked.pdf"), "application/pdf"),
    ) { scenario ->
        unlockWithRightPassword()
        scenario.recreate()
        waitFor(R.string.unlocked) // not sent back to the password step
    }

    private fun launch(intent: Intent, steps: (ActivityScenario<MainActivity>) -> Unit) {
        intent.setClassName(context, MainActivity::class.java.name)
        ActivityScenario.launch<MainActivity>(intent).use(steps)
    }

    private fun unlockWithRightPassword() {
        waitFor(R.string.password_intro)
        rule.onNode(hasSetTextAction()).performTextReplacement("user123")
        waitFor(R.string.unlock_button)
        rule.onNode(hasText(context.getString(R.string.unlock_button)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.unlocked)
    }

    private fun waitFor(@StringRes id: Int) {
        val text = context.getString(id)
        try {
            rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("\"$text\" not shown within 10 s", e)
        }
    }
}
