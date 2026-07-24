package com.example.todotimer.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backup container for Max Delta database snapshots.
 *
 * The header is intentionally small and versioned. Database contents and the GCM authentication
 * tag are encrypted; the salt, IV, KDF work factor, and format version are not secret.
 */
object EncryptedBackupCodec {
    private val magic = "MAXDELTA".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val KDF_ITERATIONS = 210_000
    private const val MIN_KDF_ITERATIONS = 100_000
    private const val MAX_KDF_ITERATIONS = 2_000_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val BUFFER_BYTES = 32 * 1024

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        secureRandom: SecureRandom = SecureRandom(),
    ) {
        require(password.isNotEmpty()) { "A backup password is required" }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = createCipher(Cipher.ENCRYPT_MODE, password, salt, iv, KDF_ITERATIONS)
        val dataOutput = DataOutputStream(output)
        dataOutput.write(magic)
        dataOutput.writeInt(FORMAT_VERSION)
        dataOutput.writeInt(KDF_ITERATIONS)
        dataOutput.write(salt)
        dataOutput.write(iv)
        transform(input, dataOutput, cipher)
        dataOutput.flush()
    }

    @Throws(BackupException::class)
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray) {
        require(password.isNotEmpty()) { "A backup password is required" }
        try {
            val dataInput = DataInputStream(input)
            val actualMagic = ByteArray(magic.size)
            dataInput.readFully(actualMagic)
            if (!actualMagic.contentEquals(magic)) {
                throw BackupException("This is not a Max Delta backup.")
            }
            val version = dataInput.readInt()
            if (version != FORMAT_VERSION) {
                throw BackupException("This backup format is not supported.")
            }
            val iterations = dataInput.readInt()
            if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) {
                throw BackupException("This backup has invalid security parameters.")
            }
            val salt = ByteArray(SALT_BYTES).also { dataInput.readFully(it) }
            val iv = ByteArray(IV_BYTES).also { dataInput.readFully(it) }
            val cipher = createCipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations)
            transform(dataInput, output, cipher)
            output.flush()
        } catch (error: BackupException) {
            throw error
        } catch (error: EOFException) {
            throw BackupException("The backup file is incomplete.", error)
        } catch (error: GeneralSecurityException) {
            throw BackupException("The password is incorrect or the backup is damaged.", error)
        } catch (error: IOException) {
            throw BackupException("The backup could not be read.", error)
        }
    }

    private fun createCipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
    ): Cipher {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
        } finally {
            keySpec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    mode,
                    SecretKeySpec(keyBytes, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, iv),
                )
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun transform(input: InputStream, output: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                cipher.update(buffer, 0, count)?.let { output.write(it) }
            }
            output.write(cipher.doFinal())
        } finally {
            buffer.fill(0)
        }
    }
}

class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)
