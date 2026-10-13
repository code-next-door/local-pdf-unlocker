package io.github.codenextdoor.pdfunlocker

import android.content.Context
import java.io.File
import java.io.IOException
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * Passwords that unlocked a PDF, most recently used first, sealed with a Keystore key in one file in the
 * app's private folder. Never logged; excluded from backups (data_extraction_rules.xml).
 */
class PasswordStore(private val file: File, private val box: SecretBox) {
    private val mutex = Mutex()

    /** Whether the last load() found a file it couldn't read and cleared it (shown once on the list). */
    var wasCleared: Boolean = false
        private set

    /** Empty also when the list can't be read right now (e.g. a passing Keystore error); it's kept. */
    suspend fun load(): List<String> = mutex.withLock {
        wasCleared = false
        read() ?: emptyList()
    }

    /** Adds [password] at the top, or moves it there if it's already saved. Throws if the list can't be read. */
    suspend fun remember(password: String) = mutex.withLock { write(listOf(password) + (readOrThrow() - password)) }

    suspend fun remove(password: String) = mutex.withLock { write(readOrThrow() - password) }

    // Never write over a list that only failed to load for now: that would lose every saved password.
    private suspend fun readOrThrow() = read() ?: throw IOException("Saved passwords can't be read right now")

    /** null: couldn't read it right now, but it may be fine (the file is kept). */
    private suspend fun read(): List<String>? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val json = JSONArray(String(box.open(file.readText()), Charsets.UTF_8))
            List(json.length()) { json.getString(it) }
        } catch (e: Exception) {
            if (!isUnreadableForGood(e)) return@withContext null
            file.delete()
            wasCleared = true
            emptyList()
        }
    }

    /**
     * Key gone (e.g. after some phone resets: SecretBox's check), wrong key or damaged file (GCM tag),
     * bad Base64 or bad JSON: it will never read back. Anything else (a busy Keystore, a disk hiccup) might.
     */
    private fun isUnreadableForGood(e: Exception) =
        e is AEADBadTagException || e is IllegalStateException || e is IllegalArgumentException || e is JSONException

    private suspend fun write(passwords: List<String>) = withContext(Dispatchers.IO) {
        val sealed = box.seal(JSONArray(passwords).toString().toByteArray(Charsets.UTF_8))
        // Write then rename, so a crash can't leave half a file (which would lose the whole list).
        val temp = File(file.path + ".tmp")
        temp.writeText(sealed)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Couldn't save the passwords")
        }
    }

    companion object {
        fun forApp(context: Context) =
            PasswordStore(File(context.filesDir, "saved-passwords.bin"), KeystoreSecretBox("saved-passwords"))
    }
}
