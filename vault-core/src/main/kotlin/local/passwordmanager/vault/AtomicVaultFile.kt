package local.passwordmanager.vault

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Deterministic storage-failure seam for the acceptance harness. It receives no vault contents. */
fun interface CommitFault { fun at(stage: CommitStage) }
enum class CommitStage { CANDIDATE_WRITTEN, CANDIDATE_VERIFIED, BEFORE_REPLACE, AFTER_REPLACE }

internal class AtomicVaultFile(
    private val file: File,
    private val fault: CommitFault,
    private val directorySync: (File) -> Unit,
) {
    fun commit(bytes: ByteArray, previous: ByteArray?, verify: (ByteArray) -> Unit) {
        val parent = file.parentFile
        val operation = UUID.randomUUID().toString()
        val candidate = File(parent, ".${file.name}-$operation.candidate")
        val rollback = File(parent, ".${file.name}-$operation.previous")
        val restore = File(parent, ".${file.name}-$operation.restore")
        var replaced = false
        var preserve = false
        try {
            if (!parent.isDirectory && !parent.mkdirs()) throw VaultException(VaultFailure.STORAGE_FAILURE)
            writeDurably(candidate, bytes)
            fault.at(CommitStage.CANDIDATE_WRITTEN)
            verify(candidate.readBytes())
            fault.at(CommitStage.CANDIDATE_VERIFIED)
            if (previous != null) {
                writeDurably(rollback, previous)
                if (!rollback.readBytes().contentEquals(previous)) throw VaultException(VaultFailure.STORAGE_FAILURE)
            }
            directorySync(parent)
            fault.at(CommitStage.BEFORE_REPLACE)
            replace(candidate, file)
            replaced = true
            fault.at(CommitStage.AFTER_REPLACE)
            directorySync(parent)
            val final = file.readBytes()
            if (!final.contentEquals(bytes)) throw VaultException(VaultFailure.STORAGE_FAILURE)
            verify(final)
        } catch (_: Exception) {
            if (replaced) {
                try {
                    if (previous == null) {
                        if (!file.delete()) throw VaultException(VaultFailure.STORAGE_FAILURE)
                        directorySync(parent)
                    } else {
                        // Keep the verified backup until the rollback rename is also durable.
                        writeDurably(restore, previous)
                        replace(restore, file)
                        directorySync(parent)
                        if (!file.readBytes().contentEquals(previous)) throw VaultException(VaultFailure.STORAGE_FAILURE)
                    }
                } catch (_: Exception) {
                    preserve = true
                }
            }
            throw VaultException(VaultFailure.STORAGE_FAILURE)
        } finally {
            if (!preserve) {
                candidate.delete()
                rollback.delete()
                restore.delete()
            }
        }
    }

    private fun writeDurably(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
    }

    private fun replace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
