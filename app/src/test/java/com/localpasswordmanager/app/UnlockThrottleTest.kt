package com.localpasswordmanager.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockThrottleTest {
    @Test fun cooldownUsesElapsedTimeAndDoesNotEndEarlyAtPartialSeconds() {
        var time = 10_000L
        val throttle = UnlockThrottle { time }
        assertEquals(0, throttle.remainingSeconds())
        throttle.failed()
        time = 10_999
        assertEquals(1, throttle.remainingSeconds())
        time = 11_000
        assertEquals(0, throttle.remainingSeconds())
        throttle.failed()
        assertEquals(2, throttle.remainingSeconds())
        time = 12_001
        assertEquals(1, throttle.remainingSeconds())
        time = 13_000
        assertEquals(0, throttle.remainingSeconds())
    }

    @Test fun repeatedFailuresAreCappedAndSuccessfulAuthenticationResetsTheCooldown() {
        val throttle = UnlockThrottle { 1_000L }
        repeat(10) { throttle.failed() }
        assertEquals(30, throttle.remainingSeconds())
        throttle.succeeded()
        assertEquals(0, throttle.remainingSeconds())
        throttle.failed()
        assertEquals(1, throttle.remainingSeconds())
    }
}
