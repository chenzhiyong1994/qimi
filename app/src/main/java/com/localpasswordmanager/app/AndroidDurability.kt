package com.localpasswordmanager.app

import android.system.Os
import android.system.OsConstants
import java.io.File

/** Persist both the rename and creation of the vault directory on Android's private filesystem. */
fun syncVaultDirectory(directory: File) {
    for (target in listOfNotNull(directory, directory.parentFile)) {
        check(target.isDirectory)
        val descriptor = Os.open(target.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}
