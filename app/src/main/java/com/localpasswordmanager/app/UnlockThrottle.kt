package com.localpasswordmanager.app

/** Process-local cooldown; it does not claim to resist offline guessing of the encrypted file. */
class UnlockThrottle(private val now: () -> Long) {
    private var failures = 0
    private var retryAt = 0L

    fun remainingSeconds(): Int = ((retryAt - now()).coerceAtLeast(0) + 999).div(1_000).toInt()
    fun failed() {
        failures++
        val seconds = (1 shl (failures - 1).coerceAtMost(5)).coerceAtMost(30)
        retryAt = now() + seconds * 1_000L
    }
    fun succeeded() { failures = 0; retryAt = 0 }
}
