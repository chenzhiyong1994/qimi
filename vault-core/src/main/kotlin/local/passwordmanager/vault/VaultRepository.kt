package local.passwordmanager.vault

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.constants.MemoryProtectionFlag
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.models.CustomDataValue
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.TimeData
import java.time.Instant
import java.text.Normalizer
import java.net.URI

data class EntryInput(
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
) {
    override fun toString() = "EntryInput(redacted)"
}

data class VaultEntry(
    val id: String,
    val version: Long,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
) {
    override fun toString() = "VaultEntry(redacted)"
}

enum class VaultFailure {
    ALREADY_EXISTS, NOT_FOUND, INVALID_INPUT, LOCKED, CONFLICT, UNLOCK_FAILED,
    STORAGE_FAILURE, UNSUPPORTED_FORMAT,
}

class VaultException(val reason: VaultFailure) : Exception(reason.name)

/** All blocking operations must run outside the Android main thread. */
class VaultRepository(
    file: File,
    private val commitFault: CommitFault = CommitFault { },
    private val directorySync: (File) -> Unit = { },
) {
    private val file = file.absoluteFile
    private val gate = gates.computeIfAbsent(file.canonicalPath) { Any() }

    fun exists(): Boolean = file.exists()

    /** Caller owns the password array and should clear it after this method returns. */
    fun create(masterPassword: CharArray): VaultSession = synchronized(gate) {
        if (file.exists()) throw VaultException(VaultFailure.ALREADY_EXISTS)
        val credentials = credentials(masterPassword)
        val initial = KeePassDatabase.Ver4x.create(
            rootName = "Vault",
            meta = Meta(generator = "LocalPasswordManager", memoryProtection = MemoryProtectionFlag.entries.toSet()),
            credentials = credentials,
        )
        val parameters = initial.header.kdfParameters as KdfParameters.Argon2
        val database = initial.copy(header = initial.header.copy(
            kdfParameters = parameters.copy(variant = KdfParameters.Argon2.Variant.Argon2id),
        ))
        val committed = persist(database, null)
        VaultSession(this, committed.first, committed.second)
    }

    fun unlock(masterPassword: CharArray): VaultSession = synchronized(gate) {
        if (!file.exists()) throw VaultException(VaultFailure.NOT_FOUND)
        val credentials = credentials(masterPassword)
        val bytes = readFile()
        VaultSession(this, KdbxCodec.decode(bytes, credentials), digest(bytes))
    }

    internal fun <T> serialized(block: () -> T): T = synchronized(gate, block)

    internal fun persist(database: KeePassDatabase.Ver4x, baseline: ByteArray?): Pair<KeePassDatabase.Ver4x, ByteArray> {
        val current = if (file.exists()) readFile() else null
        if ((baseline == null && current != null) ||
            (baseline != null && (current == null || !digest(current).contentEquals(baseline)))) {
            throw VaultException(VaultFailure.CONFLICT)
        }
        val encoded = KdbxCodec.encode(database)
        var verified: KeePassDatabase.Ver4x? = null
        AtomicVaultFile(file, commitFault, directorySync).commit(encoded, current) { candidate ->
            val decoded = KdbxCodec.decode(candidate, database.credentials)
            if (decoded.verificationSnapshot() != database.verificationSnapshot()) {
                throw VaultException(VaultFailure.STORAGE_FAILURE)
            }
            verified = decoded
        }
        return verified!! to digest(encoded)
    }

    private fun readFile(): ByteArray = try {
        if (file.length() !in 1..KdbxCodec.MAX_BYTES.toLong()) throw VaultException(VaultFailure.UNLOCK_FAILED)
        file.readBytes().also { if (it.size > KdbxCodec.MAX_BYTES) throw VaultException(VaultFailure.UNLOCK_FAILED) }
    } catch (error: VaultException) {
        throw error
    } catch (_: Exception) {
        throw VaultException(VaultFailure.STORAGE_FAILURE)
    }

    companion object {
        private val gates = ConcurrentHashMap<String, Any>()
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun credentials(password: CharArray): Credentials {
            val value = String(password)
            if (value.codePointCount(0, value.length) !in 8..128 || !value.isValidUnicode()) {
                throw VaultException(VaultFailure.INVALID_INPUT)
            }
            return Credentials.from(EncryptedValue.fromString(value))
        }
    }
}

class VaultSession internal constructor(
    private val repository: VaultRepository,
    database: KeePassDatabase.Ver4x,
    private var baseline: ByteArray,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    @Volatile private var database: KeePassDatabase.Ver4x? = database

    fun listEntries(query: String = ""): List<VaultEntry> = repository.serialized {
        val needle = query.searchValue()
        active().content.group.entries.map { it.publicEntry() }.filter { entry ->
            listOf(entry.title, entry.username, entry.url.urlHost()).any { it.searchValue().contains(needle) }
        }
    }

    fun getEntry(id: String): VaultEntry? = repository.serialized {
        active().content.group.entries.firstOrNull { it.uuid.toString() == id }?.publicEntry()
    }

    /** Updates require the version returned by getEntry/listEntries. No matching by title or username. */
    fun saveEntry(input: EntryInput, id: String? = null, expectedVersion: Long? = null): VaultEntry = repository.serialized {
        val current = active()
        validateInput(input)
        val old = if (id == null) null else current.content.group.entries.firstOrNull { it.uuid.toString() == id }
            ?: throw VaultException(VaultFailure.CONFLICT)
        if ((old == null && expectedVersion != null) ||
            (old != null && expectedVersion != old.version())) throw VaultException(VaultFailure.CONFLICT)
        val version = (old?.version() ?: 0) + 1
        val entry = Entry(
            uuid = old?.uuid ?: UUID.randomUUID(),
            fields = EntryFields.of(
                BasicField.Title() to protected(input.title),
                BasicField.UserName() to protected(input.username),
                BasicField.Password() to protected(input.password),
                BasicField.Url() to protected(input.url),
                BasicField.Notes() to protected(input.notes),
            ),
            customData = mapOf(VERSION_KEY to CustomDataValue(version.toString())),
            times = old?.times?.copy(lastModificationTime = Instant.now()) ?: TimeData.create(),
            history = old?.let { (it.history + it.copy(history = emptyList())).takeLast(5) } ?: emptyList(),
        )
        val entries = current.content.group.entries.filterNot { it.uuid == entry.uuid } + entry
        val next = current.copy(content = current.content.copy(group = current.content.group.copy(entries = entries)))
        val committed = repository.persist(next, baseline)
        baseline = committed.second
        // close() can revoke a session while disk I/O is in progress. Never restore its reference.
        if (!closed.get()) database = committed.first
        if (closed.get()) database = null
        entry.publicEntry()
    }

    /** Immediately revokes access, including when an already-started write is finishing safely. */
    override fun close() {
        closed.set(true)
        database = null
    }

    private fun active(): KeePassDatabase.Ver4x {
        if (closed.get()) throw VaultException(VaultFailure.LOCKED)
        return database ?: throw VaultException(VaultFailure.LOCKED)
    }

    private fun protected(value: String) = EntryValue.Encrypted(EncryptedValue.fromString(value))
    private fun Entry.version() = customData[VERSION_KEY]?.value?.toLongOrNull()
        ?: throw VaultException(VaultFailure.UNSUPPORTED_FORMAT)
    private fun Entry.publicEntry() = VaultEntry(
        uuid.toString(), version(), fields.title?.content.orEmpty(), fields.userName?.content.orEmpty(),
        fields.password?.content.orEmpty(), fields.url?.content.orEmpty(), fields.notes?.content.orEmpty(),
    )

    private fun validateInput(input: EntryInput) {
        if (input.title.isEmpty() || input.password.isEmpty()) throw VaultException(VaultFailure.INVALID_INPUT)
        listOf(input.title to 100, input.username to 256, input.password to 1024, input.url to 2048, input.notes to 10000)
            .forEach { (value, maximum) ->
                if (!value.isValidUnicode() || value.codePointCount(0, value.length) > maximum) {
                    throw VaultException(VaultFailure.INVALID_INPUT)
                }
            }
    }

    companion object { private const val VERSION_KEY = "LocalPasswordManager.EntryVersion" }
}

private fun String.isValidUnicode(): Boolean {
    var offset = 0
    while (offset < length) {
        val char = this[offset++]
        if (Character.isHighSurrogate(char)) {
            if (offset >= length || !Character.isLowSurrogate(this[offset++])) return false
        } else if (Character.isLowSurrogate(char)) return false
    }
    return true
}

private fun String.searchValue() = Normalizer.normalize(this, Normalizer.Form.NFC).lowercase(Locale.ROOT)
private fun String.urlHost(): String = try {
    val raw = trim()
    URI(if (raw.contains("://")) raw else "https://$raw").host.orEmpty()
} catch (_: Exception) { "" }

// KDBX serializes times to seconds and regenerates memory-protection salts.
// Compare supported application content, including field bytes, encrypted version data and history.
private fun KeePassDatabase.Ver4x.verificationSnapshot(): List<Any?> = listOf(
    content.meta.generator, content.meta.name, content.meta.description, content.meta.defaultUser,
    content.meta.customData.mapValues { it.value.value }, content.group.uuid, content.group.name,
    content.group.groups.size, content.deletedObjects,
    content.group.entries.map { it.verificationSnapshot() },
)

private fun Entry.verificationSnapshot(): List<Any?> = listOf(
    uuid, fields.entries.associate { it.key to it.value.content }, customData.mapValues { it.value.value },
    times?.let { listOf(it.creationTime?.epochSecond, it.lastAccessTime?.epochSecond,
        it.lastModificationTime?.epochSecond, it.locationChanged?.epochSecond,
        it.expiryTime?.epochSecond, it.expires, it.usageCount) },
    history.map { it.verificationSnapshot() },
)
