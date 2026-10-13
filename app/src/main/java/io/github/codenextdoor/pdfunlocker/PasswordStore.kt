package io.github.codenextdoor.pdfunlocker

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Passwords that unlocked a PDF, most recently used first, sealed with a Keystore key in one file in the
 * app's private folder. Never logged; excluded from backups (data_extraction_rules.xml).
 */
class PasswordStore(private val file: File, private val box: SecretBox) {
    private val mutex = Mutex()

    /** True once load() found a file it couldn't read and cleared it (shown once on the list). */
    var wasCleared: Boolean = false
        private set

    suspend fun load(): List<String> = mutex.withLock { read() }

    /** Adds [password] at the top, or moves it there if it's already saved. */
    suspend fun remember(password: String) = mutex.withLock { write(listOf(password) + (read() - password)) }

    suspend fun remove(password: String) = mutex.withLock { write(read() - password) }

    private suspend fun read(): List<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        try {
            val json = JSONArray(String(box.open(file.readText()), Charsets.UTF_8))
            List(json.length()) { json.getString(it) }
        } catch (_: Exception) {
            // Key gone (e.g. after some phone resets), damaged file or bad format: it can't be read back.
            file.delete()
            wasCleared = true
            emptyList()
        }
    }

    private suspend fun write(passwords: List<String>) = withContext(Dispatchers.IO) {
        val sealed = box.seal(JSONArray(passwords).toString().toByteArray(Charsets.UTF_8))
        // Write then rename, so a crash can't leave half a file (which would lose the whole list).
        val temp = File(file.path + ".tmp")
        temp.writeText(sealed)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("Couldn't save the passwords")
        }
    }

    companion object {
        fun forApp(context: Context) =
            PasswordStore(File(context.filesDir, "saved-passwords.bin"), KeystoreSecretBox("saved-passwords"))
    }
}
