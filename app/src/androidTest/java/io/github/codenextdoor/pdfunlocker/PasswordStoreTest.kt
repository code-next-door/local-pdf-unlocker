package io.github.codenextdoor.pdfunlocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordStoreTest {
    private val alias = "test-store-${UUID.randomUUID()}"
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, alias).apply { mkdirs() }
    private val file = File(dir, "saved-passwords.bin")
    private val store = PasswordStore(file, KeystoreSecretBox(alias))

    @After
    fun cleanUp() {
        dir.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun emptyAtFirst() = runTest { assertEquals(emptyList<String>(), store.load()) }

    @Test fun rememberPutsNewestFirst() = runTest {
        store.remember("a")
        store.remember("b")
        assertEquals(listOf("b", "a"), store.load())
    }

    @Test fun rememberKnownMovesItToTopWithoutDuplicate() = runTest {
        store.remember("a")
        store.remember("b")
        store.remember("a")
        assertEquals(listOf("a", "b"), store.load())
    }

    @Test fun removeDeletesOnlyThatOne() = runTest {
        store.remember("a")
        store.remember("b")
        store.remove("a")
        assertEquals(listOf("b"), store.load())
    }

    @Test fun spacesAreKept() = runTest {
        store.remember(" pw ")
        assertEquals(listOf(" pw "), store.load())
    }

    @Test fun unusualCharactersRoundTrip() = runTest {
        val odd = listOf("a\"b\\c", "pässwört 密码 🔒")
        odd.forEach { store.remember(it) }
        assertEquals(odd.reversed(), store.load())
    }

    @Test fun fileIsNotPlainText() = runTest {
        store.remember("hunter2")
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("hunter2"))
    }

    @Test fun damagedFileLoadsEmptyAndIsDeleted() = runTest {
        file.writeText("garbage")
        assertEquals(emptyList<String>(), store.load())
        assertFalse(file.exists())
        assertTrue(store.wasCleared)
    }

    @Test fun noTempFileLeftAfterSave() = runTest {
        store.remember("a")
        assertEquals(listOf(file.name), dir.list()!!.toList())
    }
}
