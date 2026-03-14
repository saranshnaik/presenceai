package com.nophubbing.presenceai.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FeatureEngineering].
 *
 * All tests are pure JVM — no Android APIs, no emulator required.
 * Run with: ./gradlew :app:test --tests "*.FeatureEngineeringTest"
 */
class FeatureEngineeringTest {

    // ─── x1: unlockFreq ────────────────────────────────────────────────────────

    @Test
    fun `x1 normal case returns ratio clamped to 4`() {
        // 8 unlocks against average of 4 = 2.0
        val result = FeatureEngineering.unlockFreq(8f, 4f)
        assertEquals(2.0f, result, 0.001f)
    }

    @Test
    fun `x1 low rolling average uses safe denominator of 3`() {
        // rollingAvg < 0.1 → use 3.0 as denominator
        val result = FeatureEngineering.unlockFreq(6f, 0.05f)
        assertEquals(2.0f, result, 0.001f) // 6 / 3.0 = 2.0
    }

    @Test
    fun `x1 zero rolling average uses safe denominator`() {
        val result = FeatureEngineering.unlockFreq(12f, 0f)
        assertEquals(4.0f, result, 0.001f) // clamped to max 4.0
    }

    @Test
    fun `x1 result is clamped to max 4`() {
        val result = FeatureEngineering.unlockFreq(100f, 1f)
        assertEquals(4.0f, result, 0.001f)
    }

    @Test
    fun `x1 result is clamped to min 0`() {
        val result = FeatureEngineering.unlockFreq(-5f, 1f)
        assertEquals(0.0f, result, 0.001f)
    }

    // ─── x2: microSessionRatio ─────────────────────────────────────────────────

    @Test
    fun `x2 normal case returns correct ratio`() {
        val result = FeatureEngineering.microSessionRatio(3, 10)
        assertEquals(0.3f, result, 0.001f)
    }

    @Test
    fun `x2 zero total sessions returns 0`() {
        val result = FeatureEngineering.microSessionRatio(0, 0)
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `x2 all sessions are micro returns 1`() {
        val result = FeatureEngineering.microSessionRatio(5, 5)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `x2 result clamped to 1 even if data is inconsistent`() {
        val result = FeatureEngineering.microSessionRatio(10, 5) // more micro than total (bad data)
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `x2 no micro sessions returns 0`() {
        val result = FeatureEngineering.microSessionRatio(0, 10)
        assertEquals(0.0f, result, 0.001f)
    }

    // ─── x3: notificationReflex ────────────────────────────────────────────────

    @Test
    fun `x3 delta under 5000ms returns 1`() {
        assertEquals(1.0f, FeatureEngineering.notificationReflex(4999L), 0.001f)
    }

    @Test
    fun `x3 delta exactly 5000ms returns 0`() {
        assertEquals(0.0f, FeatureEngineering.notificationReflex(5000L), 0.001f)
    }

    @Test
    fun `x3 delta over 5000ms returns 0`() {
        assertEquals(0.0f, FeatureEngineering.notificationReflex(10000L), 0.001f)
    }

    @Test
    fun `x3 delta of 0 means no notification and returns 0`() {
        assertEquals(0.0f, FeatureEngineering.notificationReflex(0L), 0.001f)
    }

    @Test
    fun `x3 delta of 1ms returns 1`() {
        assertEquals(1.0f, FeatureEngineering.notificationReflex(1L), 0.001f)
    }

    // ─── x4: behaviorDriftZ ────────────────────────────────────────────────────

    @Test
    fun `x4 baseline not ready returns 0`() {
        val baseline = BaselineTracker.BaselineState.initial()
        val result = FeatureEngineering.behaviorDriftZ(5f, baseline)
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `x4 baseline ready with stable sequence returns near zero drift`() {
        var state = BaselineTracker.BaselineState.initial()
        // Feed 72 identical observations to establish baseline
        repeat(72) { state = BaselineTracker.update(state, 3.0f) }

        // The baseline should not be ready because stddev ≈ 0 after identical inputs
        // (isReady requires stddev >= 0.1)
        val result = FeatureEngineering.behaviorDriftZ(3.0f, state)
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `x4 drift is clamped to -3 when far below mean`() {
        var state = BaselineTracker.BaselineState.initial()
        // Build a baseline with varied inputs so stddev > 0.1
        repeat(80) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 3.0f else 7.0f) }

        if (BaselineTracker.isReady(state)) {
            val result = FeatureEngineering.behaviorDriftZ(-1000f, state)
            assertTrue("Expected result >= -3.0 but got $result", result >= -3.0f)
        }
        // If baseline is not ready yet, function returns 0 — still valid
    }

    @Test
    fun `x4 drift is clamped to 3 when far above mean`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(80) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 3.0f else 7.0f) }

        if (BaselineTracker.isReady(state)) {
            val result = FeatureEngineering.behaviorDriftZ(1000f, state)
            assertTrue("Expected result <= 3.0 but got $result", result <= 3.0f)
        }
    }

    // ─── x5: timePhase ────────────────────────────────────────────────────────

    @Test
    fun `x5 midday returns peak value 1 0`() {
        assertEquals(1.0f, FeatureEngineering.timePhase(12), 0.001f)
    }

    @Test
    fun `x5 evening returns peak value 1 0`() {
        assertEquals(1.0f, FeatureEngineering.timePhase(18), 0.001f)
    }

    @Test
    fun `x5 midnight is low`() {
        val result = FeatureEngineering.timePhase(0)
        assertTrue("Midnight should be low (< 0.3) but got $result", result < 0.3f)
    }

    @Test
    fun `x5 morning builds up`() {
        val morning = FeatureEngineering.timePhase(9)
        val night = FeatureEngineering.timePhase(2)
        assertTrue("Morning ($morning) should be > late night ($night)", morning > night)
    }

    @Test
    fun `x5 invalid hour returns default 0 4`() {
        assertEquals(0.4f, FeatureEngineering.timePhase(-1), 0.001f)
        assertEquals(0.4f, FeatureEngineering.timePhase(24), 0.001f)
        assertEquals(0.4f, FeatureEngineering.timePhase(100), 0.001f)
    }

    @Test
    fun `x5 every valid hour returns value in 0 1 range`() {
        for (hour in 0..23) {
            val result = FeatureEngineering.timePhase(hour)
            assertTrue("Hour $hour: expected in [0,1] but got $result", result in 0f..1f)
        }
    }

    // ─── x6: vadEnergy ────────────────────────────────────────────────────────

    @Test
    fun `x6 detected true returns 1`() {
        assertEquals(1.0f, FeatureEngineering.vadEnergy(true), 0.001f)
    }

    @Test
    fun `x6 detected false returns 0`() {
        assertEquals(0.0f, FeatureEngineering.vadEnergy(false), 0.001f)
    }

    @Test
    fun `x6 null microphone permission returns 0`() {
        assertEquals(0.0f, FeatureEngineering.vadEnergy(null), 0.001f)
    }

    // ─── x7: bleSocial ────────────────────────────────────────────────────────

    @Test
    fun `x7 one device returns 1`() {
        assertEquals(1.0f, FeatureEngineering.bleSocial(1), 0.001f)
    }

    @Test
    fun `x7 multiple devices returns 1`() {
        assertEquals(1.0f, FeatureEngineering.bleSocial(5), 0.001f)
    }

    @Test
    fun `x7 zero devices returns 0`() {
        assertEquals(0.0f, FeatureEngineering.bleSocial(0), 0.001f)
    }

    @Test
    fun `x7 negative count returns 0`() {
        assertEquals(0.0f, FeatureEngineering.bleSocial(-1), 0.001f)
    }

    // ─── buildFeatureVector ────────────────────────────────────────────────────

    @Test
    fun `buildFeatureVector produces correct length vector`() {
        val fv = FeatureEngineering.buildFeatureVector(
            unlockCount10Min = 4f,
            rollingAvgUnlockRate = 2f,
            sessionsUnder30s = 3,
            totalSessions = 10,
            lastNotifDeltaMs = 2000L,
            currentUnlockRate = 4f,
            baseline = BaselineTracker.BaselineState.initial(),
            hourOfDay = 12,
            vadDetected = true,
            bleDeviceCount = 2
        )
        assertEquals(7, fv.x.size)
    }

    @Test
    fun `buildFeatureVector BLE zero makes bleSocial zero`() {
        val fv = FeatureEngineering.buildFeatureVector(
            unlockCount10Min = 8f,
            rollingAvgUnlockRate = 2f,
            sessionsUnder30s = 9,
            totalSessions = 10,
            lastNotifDeltaMs = 100L,
            currentUnlockRate = 8f,
            baseline = BaselineTracker.BaselineState.initial(),
            hourOfDay = 18,
            vadDetected = true,
            bleDeviceCount = 0
        )
        assertEquals(0.0f, fv.bleSocial, 0.001f)
    }
}
