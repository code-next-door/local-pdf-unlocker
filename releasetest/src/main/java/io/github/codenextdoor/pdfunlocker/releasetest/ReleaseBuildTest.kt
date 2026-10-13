package io.github.codenextdoor.pdfunlocker.releasetest

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests of the shrunk app (package [PACKAGE]), driven from outside with UI Automator: the
 * screen, the ViewModel and unlocking through Android's PdfRenderer, as R8 left them. The system
 * "Save as" dialog is another app, so saving is covered by the debug build's tests only.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBuildTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun freshStart() {
        device.executeShellCommand("am force-stop $PACKAGE")
    }

    @Test
    fun opensOnThePickScreen() {
        device.executeShellCommand("monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1")
        find(By.text("Pick a PDF"))
    }

    @Test
    fun unlocksAPdfOpenedFromAnotherApp() {
        openWith("user-locked.pdf")
        find(By.text("This PDF is locked. Enter its password."))
        passwordField().text = "nope"
        find(By.text("Unlock")).click()
        find(By.text("That password didn't work. Check it and try again."))
        passwordField().text = "user123"
        find(By.text("Unlock")).click()
        find(By.text("Unlocked. Save a copy without the password."))
        find(By.text("Save unlocked copy"))
    }

    @Test
    fun saysWhenAPdfIsNotLocked() {
        openWith("plain.pdf")
        find(By.text("This PDF isn't locked. You can still save a copy."))
    }

    /**
     * Like Gmail's "Open with": this APK owns the file and sends it with a read grant. (From the shell
     * there's no grant, and Android hides this APK's provider from the app.)
     */
    private fun openWith(asset: String) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://io.github.codenextdoor.pdfunlocker.releasetest.pdfs/$asset"), "application/pdf")
            .setClassName(PACKAGE, "io.github.codenextdoor.pdfunlocker.MainActivity")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun passwordField(): UiObject2 = find(By.clazz("android.widget.EditText"))

    private fun find(selector: BySelector, timeoutMs: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs) ?: fail("Not on screen within $timeoutMs ms: $selector")

    /** Logs what was on screen (logcat tag "ReleaseBuildTest"), then fails. */
    private fun fail(message: String): Nothing {
        val screen = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
        screen.chunked(3_000).forEach { Log.i("ReleaseBuildTest", it) }
        throw AssertionError("$message (screen logged under tag ReleaseBuildTest)")
    }

    private companion object {
        const val PACKAGE = "io.github.codenextdoor.pdfunlocker.minified"
    }
}
