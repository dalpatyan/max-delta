package com.example.todotimer.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedBackupCodecTest {
    private val password = "correct horse battery staple".toCharArray()
    private val content = ByteArray(128 * 1024) { index -> (index * 31).toByte() }

    @Test
    fun roundTripPreservesEveryByte() {
        val encrypted = encrypt(content, password)
        val restored = ByteArrayOutputStream()

        EncryptedBackupCodec.decrypt(
            ByteArrayInputStream(encrypted),
            restored,
            password,
        )

        assertArrayEquals(content, restored.toByteArray())
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = encrypt(content, password)

        assertThrows(BackupException::class.java) {
            EncryptedBackupCodec.decrypt(
                ByteArrayInputStream(encrypted),
                ByteArrayOutputStream(),
                "not the password".toCharArray(),
            )
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = encrypt(content, password)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        assertThrows(BackupException::class.java) {
            EncryptedBackupCodec.decrypt(
                ByteArrayInputStream(encrypted),
                ByteArrayOutputStream(),
                password,
            )
        }
    }

    @Test
    fun unrelatedFileIsRejected() {
        assertThrows(BackupException::class.java) {
            EncryptedBackupCodec.decrypt(
                ByteArrayInputStream("not-a-backup".toByteArray()),
                ByteArrayOutputStream(),
                password,
            )
        }
    }

    private fun encrypt(bytes: ByteArray, passphrase: CharArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            EncryptedBackupCodec.encrypt(
                ByteArrayInputStream(bytes),
                output,
                passphrase,
            )
        }.toByteArray()
}
