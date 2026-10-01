package com.localpasswordmanager.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

class SensitiveClipboard(context: Context) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var ownedId: String? = null
    private var ownedTimestamp = 0L
    private var expiresAt = 0L
    private var foreground = false

    fun resumed() {
        foreground = true
        if (android.os.SystemClock.elapsedRealtime() >= expiresAt) clearIfOwned()
    }

    fun paused() {
        clearIfOwned()
        foreground = false
    }

    fun copy(value: String): Boolean {
        if (!foreground) return false
        val id = UUID.randomUUID().toString()
        val clip = ClipData.newPlainText("密码库复制", value)
        clip.description.extras = PersistableBundle().apply {
            val sensitiveKey = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE
                else "android.content.extra.IS_SENSITIVE"
            putBoolean(sensitiveKey, true)
            putString(CLIP_ID, id)
        }
        try { clipboard.setPrimaryClip(clip) } catch (_: SecurityException) { return false }
        ownedId = id
        val description = clipboard.primaryClipDescription
        ownedTimestamp = description?.timestamp ?: 0L
        expiresAt = android.os.SystemClock.elapsedRealtime() + 30_000
        handler.postDelayed({ if (ownedId == id) clearIfOwned() }, 30_000)
        return description?.extras?.getString(CLIP_ID) == id
    }

    fun clearIfOwned() {
        val id = ownedId ?: return
        // Do not read the clipboard in the background. A unique copy ID protects later equal-text copies.
        if (!foreground) return
        val current = try { clipboard.primaryClipDescription } catch (_: SecurityException) { return }
        if (current == null) {
            // A background/focus restriction is indistinguishable from an empty clipboard here.
            // Retain the token so a later foreground check can make a reliable decision.
            return
        }
        val currentId = current.extras?.getString(CLIP_ID)
        if (currentId == id && current.timestamp == ownedTimestamp) {
            try { clipboard.clearPrimaryClip() } catch (_: SecurityException) { return }
        }
        ownedId = null
    }

    companion object { const val CLIP_ID = "com.localpasswordmanager.clip_id" }
}
