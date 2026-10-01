package local.passwordmanager.vault

import app.keemobile.kotpass.cryptography.format.KdfProvider
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.KdfParameters
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object KdbxCodec {
    const val MAX_BYTES = 32 * 1024 * 1024

    fun encode(database: KeePassDatabase.Ver4x): ByteArray = try {
        ByteArrayOutputStream().also { database.encode(it) }.toByteArray().also {
            if (it.size > MAX_BYTES) throw VaultException(VaultFailure.STORAGE_FAILURE)
        }
    } catch (_: Exception) {
        throw VaultException(VaultFailure.STORAGE_FAILURE)
    }

    fun decode(bytes: ByteArray, credentials: Credentials): KeePassDatabase.Ver4x {
        val kdf = BoundedKdf()
        try {
            val terminator = inspectFraming(bytes)
            val database = KeePassDatabase.decode(bytes.inputStream(), credentials, kdfProvider = kdf)
                as? KeePassDatabase.Ver4x ?: throw VaultException(VaultFailure.UNSUPPORTED_FORMAT)
            // Kotpass 0.13.0 authenticates positive-length blocks, but skips the final empty block.
            // Verify that block using the official KDBX construction, with the library-derived key.
            val combined = database.header.masterSeed.toByteArray() + kdf.transformed!! + byteArrayOf(1)
            val blockRoot = hash512(combined)
            combined.fill(0)
            val index = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(terminator.index).array()
            val blockKey = hash512(index + blockRoot)
            blockRoot.fill(0)
            val expected = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(blockKey, "HmacSHA256"))
                doFinal(index + ByteArray(4))
            }
            blockKey.fill(0)
            if (!MessageDigest.isEqual(expected, terminator.mac)) throw VaultException(VaultFailure.UNLOCK_FAILED)
            return database
        } catch (error: VaultException) {
            throw error
        } catch (_: Exception) {
            throw VaultException(VaultFailure.UNLOCK_FAILED)
        } finally {
            kdf.transformed?.fill(0)
        }
    }

    private class BoundedKdf : KdfProvider {
        var transformed: ByteArray? = null
        override fun transformKey(kdfParameters: KdfParameters, compositeKey: ByteArray): ByteArray {
            val parameters = kdfParameters as? KdfParameters.Argon2
                ?: throw VaultException(VaultFailure.UNSUPPORTED_FORMAT)
            if (parameters.variant != KdfParameters.Argon2.Variant.Argon2id || parameters.memory != 32UL * 1024UL * 1024UL ||
                parameters.iterations != 8UL || parameters.parallelism != 2U || parameters.version != 0x13U ||
                parameters.salt.size != 32 || parameters.secretKey != null || parameters.associatedData != null) {
                throw VaultException(VaultFailure.UNSUPPORTED_FORMAT)
            }
            return LibraryKdfBridge.transform(parameters, compositeKey).also { transformed = it.copyOf() }
        }
    }

    private data class Terminator(val index: Long, val mac: ByteArray)

    /** Size/version/structure checks happen before KDF; they do not replace library authentication. */
    private fun inspectFraming(bytes: ByteArray): Terminator {
        if (bytes.size !in 12..MAX_BYTES) throw VaultException(VaultFailure.UNLOCK_FAILED)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != 0x9AA2D903.toInt() || buffer.int != 0xB54BFB67.toInt()) throw VaultException(VaultFailure.UNLOCK_FAILED)
        if (buffer.int != 0x00040001) throw VaultException(VaultFailure.UNSUPPORTED_FORMAT)
        val fields = mutableSetOf<Int>()
        while (true) {
            val id = buffer.get().toInt() and 0xff
            val length = buffer.int
            if (!fields.add(id) || length < 0 || length > 65536 || length > buffer.remaining()) throw VaultException(VaultFailure.UNLOCK_FAILED)
            val field = ByteArray(length).also(buffer::get)
            if (id == 0) {
                if (!field.contentEquals(byteArrayOf(13, 10, 13, 10))) throw VaultException(VaultFailure.UNLOCK_FAILED)
                break
            }
        }
        val headerEnd = buffer.position()
        val expectedHash = ByteArray(32).also(buffer::get)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(0, headerEnd))
        if (!MessageDigest.isEqual(expectedHash, actualHash)) throw VaultException(VaultFailure.UNLOCK_FAILED)
        buffer.position(buffer.position() + 32) // Header HMAC is validated by Kotpass.
        var index = 0L
        while (true) {
            val mac = ByteArray(32).also(buffer::get)
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) throw VaultException(VaultFailure.UNLOCK_FAILED)
            if (length == 0) {
                if (index == 0L || buffer.hasRemaining()) throw VaultException(VaultFailure.UNLOCK_FAILED)
                return Terminator(index, mac)
            }
            buffer.position(buffer.position() + length)
            index++
        }
    }

    private fun hash512(bytes: ByteArray) = MessageDigest.getInstance("SHA-512").digest(bytes)
}
