package local.passwordmanager.vault

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class VaultRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val master get() = "  Synthetic 口令 A1!  ".toCharArray()
    private fun repository() = VaultRepository(temporary.root.resolve("vault.kdbx"))
    private fun entry(title: String = "Synthetic Entry") = EntryInput(title, "SyntheticUser", "SyntheticPassword!42")

    @Test fun masterPasswordLengthBoundariesCreateAndUnlockWithoutChangingOriginalValues() {
        // 合成口令：最短 ASCII、含空格/补充平面字符的 8 code points，以及保留的 128 上限。
        listOf("Synt8!ab", " 😀合成8!a ", "A".repeat(128)).forEachIndexed { index, value ->
            val repository = VaultRepository(temporary.newFolder("master-boundary-$index").resolve("vault.kdbx"))
            val password = value.toCharArray()
            try {
                val saved = repository.create(password).use { it.saveEntry(entry()) }
                repository.unlock(password).use { assertEquals(listOf(saved), it.listEntries()) }
            } finally { password.fill('\u0000') }
        }
    }

    companion object {
        // 公开合成测试字段，不是真实账号或凭据；边界长度和非法 Unicode 用于原值校验。
        private const val EDITED_ENTRY_FIXTURE = "Changed Synthetic Password!42"
        private const val MALFORMED_ENTRY_FIXTURE = "Synthetic\uD800unpaired"
        private val OVERSIZE_ENTRY_FIXTURE = "A".repeat(1025)
        private val BOUNDARY_ENTRY_FIXTURE = "A".repeat(1024)
    }

    @Test fun `saved entries survive close and reopen without changing any field`() {
        val repository = repository()
        assertFalse(repository.exists())
        val expected = EntryInput(
            "合成条目 Synthetic title 7q", "  Synthetic.User@Example.invalid  ",
            "  密码 e\u0301 É 👩🏽‍💻\nSecond Line\r\n\t  ",
            " https://example.invalid/Case?q=Case#片段 ", "  第一行\n第二行\r\n  ",
        )
        val saved = repository.create(master).use { it.saveEntry(expected) }
        assertTrue(repository.exists())
        repository.unlock(master).use { session ->
            assertEquals(listOf(saved), session.listEntries())
            assertEquals(expected.title, session.getEntry(saved.id)!!.title)
            assertEquals(expected.username, saved.username)
            assertEquals(expected.password, saved.password)
            assertEquals(expected.url, saved.url)
            assertEquals(expected.notes, saved.notes)
            assertEquals(1L, saved.version)
        }
    }

    @Test fun `search uses normalized title account and URL host without indexing notes or URL path`() {
        repository().create(master).use { session ->
            val saved = session.saveEntry(EntryInput(
                "Synthetic Cafe\u0301", "MixedCaseSyntheticUser", "SyntheticPassword!42",
                "https://search.example.invalid/OnlyPathProbe?q=OnlyQueryProbe", "OnlyNotesProbe",
            ))
            assertEquals(listOf(saved), session.listEntries("CAFÉ"))
            assertEquals(listOf(saved), session.listEntries("mixedcasesyntheticuser"))
            assertEquals(listOf(saved), session.listEntries("SEARCH.EXAMPLE.INVALID"))
            assertEquals(emptyList(), session.listEntries("OnlyNotesProbe"))
            assertEquals(emptyList(), session.listEntries("OnlyPathProbe"))
            assertEquals(emptyList(), session.listEntries("OnlyQueryProbe"))
            assertEquals(emptyList(), session.listEntries("SyntheticPassword!42"))
            assertEquals("Synthetic Cafe\u0301", session.getEntry(saved.id)!!.title)
        }
    }

    @Test fun `wrong password rejects unlock and cannot overwrite a valid vault`() {
        val repository = repository()
        val saved = repository.create(master).use { it.saveEntry(entry()) }
        val before = temporary.root.resolve("vault.kdbx").readBytes()
        val exception = assertFailsWith<VaultException> { repository.unlock("Wrong Synthetic password!42".toCharArray()) }
        assertEquals(VaultFailure.UNLOCK_FAILED, exception.reason)
        assertEquals(VaultFailure.UNLOCK_FAILED,
            assertFailsWith<VaultException> { repository.unlock(String(master).trim().toCharArray()) }.reason)
        assertEquals(VaultFailure.ALREADY_EXISTS, assertFailsWith<VaultException> { repository.create(master) }.reason)
        assertTrue(before.contentEquals(temporary.root.resolve("vault.kdbx").readBytes()))
        repository.unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
    }

    @Test fun `disk artifacts do not contain readable credentials or sensitive metadata`() {
        val input = EntryInput("UniqueSyntheticTitle87", "UniqueSyntheticUser87", "UniqueSyntheticPassword87",
            "https://uniquesynthetic87.invalid", "UniqueSyntheticNote87")
        repository().create(master).use { it.saveEntry(input) }
        val bytes = temporary.root.resolve("vault.kdbx").readBytes()
        listOf(input.title, input.username, input.password, input.url, input.notes, String(master)).forEach { sensitive ->
            assertFalse(String(bytes, Charsets.ISO_8859_1).contains(sensitive))
        }
        assertEquals(listOf("vault.kdbx"), temporary.root.listFiles()!!.map { it.name })
    }

    @Test fun `directory sync failure cannot claim saved and leaves the previous vault readable`() {
        val repository = repository()
        val saved = repository.create(master).use { it.saveEntry(entry()) }
        val file = temporary.root.resolve("vault.kdbx")
        val before = file.readBytes()
        val failing = VaultRepository(file, directorySync = { throw java.io.IOException("Synthetic storage failure") })
        failing.unlock(master).use { session ->
            assertEquals(VaultFailure.STORAGE_FAILURE,
                assertFailsWith<VaultException> { session.saveEntry(entry("Uncommitted Synthetic Entry")) }.reason)
            assertEquals(listOf(saved), session.listEntries())
        }
        assertTrue(before.contentEquals(file.readBytes()))
        repository.unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
    }

    @Test fun `altered ciphertext header final MAC truncation and appended bytes reject without modifying the original`() {
        val file = temporary.root.resolve("vault.kdbx")
        val repository = repository()
        val saved = repository.create(master).use { it.saveEntry(entry()) }
        val before = file.readBytes()
        val corrupted = listOf(
            before.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() },
            before.copyOf().also { val offset = it.size / 2; it[offset] = (it[offset].toInt() xor 1).toByte() },
            before.copyOf().also { val offset = it.size - 36; it[offset] = (it[offset].toInt() xor 1).toByte() },
            before.copyOf().also { for (offset in it.size - 4 until it.size) it[offset] = 0xff.toByte() },
            before.copyOf(before.size - 1),
            before.copyOf(before.size - 36),
            before + byteArrayOf(42),
            excessiveKdfMemory(before),
        )
        corrupted.forEachIndexed { index, bytes ->
            val copy = temporary.root.resolve("synthetic-corrupt-$index.kdbx").apply { writeBytes(bytes) }
            val failure = assertFailsWith<VaultException> { VaultRepository(copy).unlock(master) }
            assertTrue(failure.reason in setOf(VaultFailure.UNLOCK_FAILED, VaultFailure.UNSUPPORTED_FORMAT))
            assertTrue(bytes.contentEquals(copy.readBytes()))
        }
        assertTrue(before.contentEquals(file.readBytes()))
        repository.unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
    }

    @Test fun `all documented persistence fault points preserve the last successful disk and session values`() {
        val file = temporary.root.resolve("vault.kdbx")
        val saved = repository().create(master).use { it.saveEntry(entry()) }
        val before = file.readBytes()
        CommitStage.entries.forEach { failingStage ->
            val failing = VaultRepository(file, CommitFault { if (it == failingStage) throw IOException("Synthetic full disk") })
            failing.unlock(master).use { session ->
                val result = assertFailsWith<VaultException> { session.saveEntry(entry("Never committed $failingStage")) }
                assertEquals(VaultFailure.STORAGE_FAILURE, result.reason)
                assertEquals(listOf(saved), session.listEntries())
            }
            assertTrue(before.contentEquals(file.readBytes()))
            assertEquals(listOf("vault.kdbx"), temporary.root.listFiles()!!.map { it.name })
            repository().unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
        }
    }

    @Test fun `failed creation cannot masquerade as an initialized vault`() {
        val file = temporary.root.resolve("vault.kdbx")
        CommitStage.entries.forEach { stage ->
            val failing = VaultRepository(file, CommitFault { if (it == stage) throw IOException("Synthetic failure") })
            assertEquals(VaultFailure.STORAGE_FAILURE, assertFailsWith<VaultException> { failing.create(master) }.reason)
            assertFalse(failing.exists())
            assertTrue(temporary.root.listFiles()!!.isEmpty())
        }
        repository().create(master).use { assertEquals(emptyList(), it.listEntries()) }
    }

    @Test fun `sync failure after replace rolls back and repeated rollback sync failure retains an encrypted recovery file`() {
        val file = temporary.root.resolve("vault.kdbx")
        val saved = repository().create(master).use { it.saveEntry(entry()) }
        val before = file.readBytes()
        val calls = AtomicInteger()
        val failing = VaultRepository(file, directorySync = {
            if (calls.incrementAndGet() >= 2) throw IOException("Synthetic persistent flush failure")
        })
        failing.unlock(master).use { session ->
            assertEquals(VaultFailure.STORAGE_FAILURE,
                assertFailsWith<VaultException> { session.saveEntry(entry("Uncommitted replacement")) }.reason)
        }
        assertTrue(before.contentEquals(file.readBytes()))
        val recovery = temporary.root.listFiles()!!.filter { it.name.endsWith(".previous") }
        assertEquals(1, recovery.size)
        assertTrue(before.contentEquals(recovery.single().readBytes()))
        VaultRepository(recovery.single()).unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
        repository().unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
    }

    @Test fun `duplicate identities remain separate and stale session or entry versions cannot overwrite changes`() {
        val file = temporary.root.resolve("vault.kdbx")
        val repository = repository()
        repository.create(master).use { first ->
            val one = first.saveEntry(entry())
            repository.unlock(master).use { stale ->
                val two = first.saveEntry(entry())
                assertTrue(one.id != two.id)
                assertEquals(2, first.listEntries().size)
                val before = file.readBytes()
                assertEquals(VaultFailure.CONFLICT,
                    assertFailsWith<VaultException> { stale.saveEntry(entry("Lost stale update")) }.reason)
                assertTrue(before.contentEquals(file.readBytes()))
            }
            val updated = first.saveEntry(entry("Edited title").copy(password = EDITED_ENTRY_FIXTURE), one.id, one.version)
            assertEquals(2L, updated.version)
            val before = file.readBytes()
            assertEquals(VaultFailure.CONFLICT,
                assertFailsWith<VaultException> { first.saveEntry(entry("Stale edit"), one.id, one.version) }.reason)
            assertTrue(before.contentEquals(file.readBytes()))
            repository.unlock(master).use { reopened ->
                assertEquals(updated, reopened.getEntry(one.id))
                assertEquals(2, reopened.listEntries().size)
            }
        }
    }

    @Test fun `over-limit and malformed Unicode input is refused without truncation or modifying saved data`() {
        val repository = repository()
        repository.create(master).use { session ->
            val saved = session.saveEntry(entry())
            val file = temporary.root.resolve("vault.kdbx")
            val before = file.readBytes()
            val invalid = listOf(entry("") , entry().copy(password = ""), entry("A".repeat(101)),
                entry().copy(username = "A".repeat(257)), entry().copy(password = OVERSIZE_ENTRY_FIXTURE),
                entry().copy(url = "A".repeat(2049)), entry().copy(notes = "A".repeat(10001)),
                entry().copy(password = MALFORMED_ENTRY_FIXTURE))
            invalid.forEach { input ->
                assertEquals(VaultFailure.INVALID_INPUT, assertFailsWith<VaultException> { session.saveEntry(input) }.reason)
            }
            assertTrue(before.contentEquals(file.readBytes()))
            assertEquals(saved, session.getEntry(saved.id))
            val boundary = entry("😀".repeat(100)).copy(username = "A".repeat(256), password = BOUNDARY_ENTRY_FIXTURE,
                url = "A".repeat(2048), notes = "A".repeat(10000))
            val atLimit = session.saveEntry(boundary)
            repository.unlock(master).use { reopened ->
                assertEquals(boundary.title, reopened.getEntry(atLimit.id)!!.title)
                assertEquals(boundary.password, reopened.getEntry(atLimit.id)!!.password)
                assertEquals(boundary.notes, reopened.getEntry(atLimit.id)!!.notes)
            }
        }
        val other = VaultRepository(temporary.newFolder("invalid-master").resolve("vault.kdbx"))
        listOf("A".repeat(7), "😀".repeat(7), "A".repeat(129), "A".repeat(8) + "\uD800").forEach { password ->
            assertEquals(VaultFailure.INVALID_INPUT, assertFailsWith<VaultException> { other.create(password.toCharArray()) }.reason)
            assertFalse(other.exists())
        }
    }

    @Test fun `locking does not wait for an admitted write and closed sessions cannot disclose or modify data`() {
        val admitted = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val armed = AtomicBoolean(false)
        val repository = VaultRepository(temporary.root.resolve("vault.kdbx"), CommitFault {
            if (armed.get() && it == CommitStage.BEFORE_REPLACE) {
                admitted.countDown()
                check(resume.await(30, TimeUnit.SECONDS))
            }
        })
        val session = repository.create(master)
        val pool = Executors.newFixedThreadPool(2)
        try {
            armed.set(true)
            val writing = pool.submit<VaultEntry> { session.saveEntry(entry()) }
            assertTrue(admitted.await(30, TimeUnit.SECONDS))
            val locking = pool.submit { session.close() }
            locking.get(1, TimeUnit.SECONDS) // This must complete while the write remains paused.
            resume.countDown()
            val saved = writing.get(30, TimeUnit.SECONDS)
            assertEquals(VaultFailure.LOCKED, assertFailsWith<VaultException> { session.listEntries() }.reason)
            assertEquals(VaultFailure.LOCKED, assertFailsWith<VaultException> { session.getEntry(saved.id) }.reason)
            assertEquals(VaultFailure.LOCKED, assertFailsWith<VaultException> { session.saveEntry(entry()) }.reason)
            repository.unlock(master).use { assertEquals(listOf(saved), it.listEntries()) }
        } finally {
            resume.countDown()
            session.close()
            pool.shutdownNow()
        }
    }

    // Mutate the standard KDBX M UInt64 parameter and repair only the public header hash.
    // The adapter must reject this resource request before attempting Argon2, even though its SHA is valid.
    private fun excessiveKdfMemory(original: ByteArray): ByteArray {
        val bytes = original.copyOf()
        val marker = byteArrayOf(5, 1, 0, 0, 0, 'M'.code.toByte(), 8, 0, 0, 0)
        val offset = (0..bytes.size - marker.size).first { index ->
            marker.indices.all { bytes[index + it] == marker[it] }
        }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(offset + marker.size, Long.MAX_VALUE)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply { position(12) }
        while (true) {
            val id = header.get().toInt()
            val length = header.int
            header.position(header.position() + length)
            if (id == 0) break
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(0, header.position()))
        hash.copyInto(bytes, header.position())
        return bytes
    }
}
