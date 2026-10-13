package io.github.codenextdoor.pdfunlocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretBoxTest {
    private val alias = "test-box-${UUID.randomUUID()}"
    private val box = KeystoreSecretBox(alias)

    @After
    fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @Test fun sealThenOpenGivesTheSameBytes() {
        val secret = "hunter2".toByteArray()
        assertArrayEquals(secret, box.open(box.seal(secret)))
    }

    @Test fun sealedTextDoesNotContainTheSecret() = assertFalse(box.seal("hunter2".toByteArray()).contains("hunter2"))

    @Test fun openAfterKeyDeletedThrows() {
        val sealed = box.seal("hunter2".toByteArray())
        deleteKey()
        try {
            box.open(sealed)
            fail("expected an exception")
        } catch (_: IllegalStateException) {
        }
    }
}
