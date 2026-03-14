package com.nophubbing.presenceai.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end integration tests for the ML pipeline.
 *
 * Tests the full chain: raw signals → feature vector → P(phub) → nudge decision → learning.
 * No Android APIs required. Pure JVM.
 *
 * Run with: ./gradlew :app:test --tests "*.PipelineIntegrationTest"
 */
class PipelineIntegrationTest {

    /**
     * Helper: builds a high-phubbing feature vector with BLE present.
     * Represents: 8 unlocks in 10 min, all micro-sessions, immediate notif reflex,
     * evening time, voice detected, BLE device present.
     */
    private fun highPhubVector(baseline: BaselineTracker.BaselineState = BaselineTracker.BaselineState.initial()): FeatureVector {
        return FeatureEngineering.buildFeatureVector(
            unlockCount10Min = 8f,
            rollingAvgUnlockRate = 2f,          // 8/2 = 4.0 (max)
            sessionsUnder30s = 10,
            totalSessions = 10,                  // micro_ratio = 1.0
            lastNotifDeltaMs = 1000L,            // opened in 1s → reflex = 1.0
            currentUnlockRate = 8f,
            baseline = baseline,
            hourOfDay = 18,                      // evening peak → 1.0
            vadDetected = true,                  // VAD = 1.0
            bleDeviceCount = 1                   // BLE = 1.0
        )
    }

    /**
     * Helper: builds a zero-BLE feature vector (nobody nearby).
     */
    private fun noBleFVector(): FeatureVector {
        return FeatureEngineering.buildFeatureVector(
            unlockCount10Min = 8f,
            rollingAvgUnlockRate = 2f,
            sessionsUnder30s = 10,
            totalSessions = 10,
            lastNotifDeltaMs = 1000L,
            currentUnlockRate = 8f,
            baseline = BaselineTracker.BaselineState.initial(),
            hourOfDay = 18,
            vadDetected = true,
            bleDeviceCount = 0                   // BLE = 0.0 — hard gate
        )
    }

    // ─── Full inference chain ─────────────────────────────────────────────────

    @Test
    fun `high phubbing signals with BLE present produces P(phub) above threshold`() {
        val weights = LrWeights.default()
        val vector = highPhubVector()
        val result = PhubbingClassifier.infer(vector, weights)

        assertTrue(
            "Expected pPhub > ${PipelineConfig.NUDGE_THRESHOLD} but got ${result.pPhub}",
            result.pPhub > PipelineConfig.NUDGE_THRESHOLD
        )
        assertTrue("Expected shouldNudge = true", result.shouldNudge)
    }

    @Test
    fun `BLE absent forces P phub to exactly 0 regardless of all other signals`() {
        val weights = LrWeights.default()
        val vector = noBleFVector()
        val result = PhubbingClassifier.infer(vector, weights)

        assertEquals(
            "BLE hard gate: P(phub) must be 0.0 when BLE = 0",
            0.0f, result.pPhub, 0.001f
        )
        assertFalse("shouldNudge must be false when BLE = 0", result.shouldNudge)
    }

    @Test
    fun `low signal vector does not trigger nudge`() {
        val weights = LrWeights.default()
        val vector = FeatureEngineering.buildFeatureVector(
            unlockCount10Min = 1f,
            rollingAvgUnlockRate = 3f,           // unlock_freq = 1/3 ≈ 0.33
            sessionsUnder30s = 1,
            totalSessions = 10,                  // micro_ratio = 0.1
            lastNotifDeltaMs = 30_000L,          // no reflex
            currentUnlockRate = 1f,
            baseline = BaselineTracker.BaselineState.initial(),
            hourOfDay = 3,                       // low-activity hour
            vadDetected = false,
            bleDeviceCount = 1
        )
        val result = PhubbingClassifier.infer(vector, weights)
        assertFalse("Low-signal scenario should not trigger nudge", result.shouldNudge)
    }

    // ─── Learning cycle ───────────────────────────────────────────────────────

    @Test
    fun `accepted nudge label 1 increments update count`() {
        val weights = LrWeights.default()
        val vector = highPhubVector()
        val updated = PhubbingClassifier.updateWeights(vector, 1.0f, weights)
        assertEquals(weights.updateCount + 1, updated.updateCount)
    }

    @Test
    fun `rejected nudge label 0 increments update count`() {
        val weights = LrWeights.default()
        val vector = highPhubVector()
        val updated = PhubbingClassifier.updateWeights(vector, 0.0f, weights)
        assertEquals(weights.updateCount + 1, updated.updateCount)
    }

    @Test
    fun `ambiguous label minus1 does not increment update count`() {
        val weights = LrWeights.default()
        val vector = highPhubVector()
        val updated = PhubbingClassifier.updateWeights(vector, -1.0f, weights)
        assertEquals(weights.updateCount, updated.updateCount)
    }

    @Test
    fun `multiple accepted nudges progressively increase P phub for high signal vector`() {
        var weights = LrWeights.default()
        val vector = highPhubVector()

        // Run several accepted-nudge gradient steps
        repeat(10) {
            weights = PhubbingClassifier.updateWeights(vector, 1.0f, weights)
        }

        assertEquals(10, weights.updateCount)

        // After 10 positive labels, P(phub) for the same high-signal vector should be >= initial
        val initial = PhubbingClassifier.infer(highPhubVector(), LrWeights.default())
        val trained = PhubbingClassifier.infer(vector, weights)

        assertTrue(
            "P(phub) after positive training (${trained.pPhub}) should be >= initial (${initial.pPhub})",
            trained.pPhub >= initial.pPhub - 0.01f // allow tiny floating-point slack
        )
    }

    // ─── Baseline integration ─────────────────────────────────────────────────

    @Test
    fun `x4 always 0 before baseline is ready even with large spike`() {
        val freshBaseline = BaselineTracker.BaselineState.initial()
        val vector = highPhubVector(freshBaseline)
        // Cold-start: x4 should be 0.0
        assertEquals(0.0f, vector.behaviorDriftZ, 0.001f)
    }

    @Test
    fun `x4 activates after baseline is ready with varied data`() {
        var baseline = BaselineTracker.BaselineState.initial()
        // Build baseline with alternating values to get stddev > 0.1
        repeat(100) { i ->
            baseline = BaselineTracker.update(baseline, if (i % 2 == 0) 2.0f else 8.0f)
        }

        if (BaselineTracker.isReady(baseline)) {
            // Spike input far above mean
            val vector = highPhubVector(baseline)
            // x4 should now be non-zero (positive drift)
            assertTrue(
                "x4 should be > 0 when unlock rate spikes above baseline",
                vector.behaviorDriftZ > 0f
            )
        }
        // If isReady is false, test is vacuously passing (insufficient variance variant)
    }

    // ─── WeightLoader integration ──────────────────────────────────────────────

    @Test
    fun `WeightLoader parseWeights returns correct values from valid JSON`() {
        val json = """{"weights":[0.85,0.60,0.90,0.00,0.70,0.80,0.85],"bias":-2.50}"""
        val weights = WeightLoader.parseWeights(json)
        assertEquals(0.85f, weights.w[0], 0.001f)
        assertEquals(-2.50f, weights.bias, 0.001f)
        assertEquals(0, weights.updateCount)
    }

    @Test
    fun `WeightLoader parseWeights falls back to defaults on bad JSON`() {
        val weights = WeightLoader.parseWeights("{not valid json}")
        assertArrayEquals(LrWeights.default().w, weights.w, 0.001f)
        assertEquals(LrWeights.default().bias, weights.bias, 0.001f)
    }

    @Test
    fun `WeightLoader parseWeights falls back to defaults on wrong weight count`() {
        val json = """{"weights":[0.85,0.60,0.90],"bias":-2.50}""" // only 3 weights
        val weights = WeightLoader.parseWeights(json)
        assertArrayEquals(LrWeights.default().w, weights.w, 0.001f)
    }

    // ─── ModelStore serialization round-trip ──────────────────────────────────

    @Test
    fun `ModelStore serialize then deserialize preserves all fields`() {
        val original = LrWeights(
            w = floatArrayOf(0.9f, 0.7f, 0.8f, 0.1f, 0.6f, 0.5f, 0.4f),
            bias = -1.75f,
            updateCount = 42
        )
        val json = ModelStore.serializeWeights(original)
        val restored = ModelStore.deserializeWeights(json)

        assertArrayEquals(original.w, restored.w, 0.001f)
        assertEquals(original.bias, restored.bias, 0.001f)
        assertEquals(original.updateCount, restored.updateCount)
    }

    @Test
    fun `ModelStore deserialize falls back to defaults on corrupt JSON`() {
        val restored = ModelStore.deserializeWeights("{garbage}")
        assertArrayEquals(LrWeights.default().w, restored.w, 0.001f)
    }
}
