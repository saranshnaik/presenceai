package com.nophubbing.presenceai.ml

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PhubbingClassifier].
 *
 * Pure JVM — no Android APIs required.
 * Run with: ./gradlew :app:test --tests "*.PhubbingClassifierTest"
 */
class PhubbingClassifierTest {

    // ─── sigmoid ───────────────────────────────────────────────────────────────

    @Test
    fun `sigmoid of 0 returns 0 5`() {
        assertEquals(0.5f, PhubbingClassifier.sigmoid(0f), 0.001f)
    }

    @Test
    fun `sigmoid of positive large returns close to 1`() {
        val result = PhubbingClassifier.sigmoid(10f)
        assertTrue("Expected near 1.0 but got $result", result > 0.99f)
    }

    @Test
    fun `sigmoid of negative large returns close to 0`() {
        val result = PhubbingClassifier.sigmoid(-10f)
        assertTrue("Expected near 0.0 but got $result", result < 0.01f)
    }

    @Test
    fun `sigmoid handles extreme positive without overflow`() {
        val result = PhubbingClassifier.sigmoid(1000f)
        assertFalse("sigmoid(1000) should not be NaN", result.isNaN())
        assertFalse("sigmoid(1000) should not be Inf", result.isInfinite())
        assertEquals(1.0f, result, 0.001f)
    }

    @Test
    fun `sigmoid handles extreme negative without underflow`() {
        val result = PhubbingClassifier.sigmoid(-1000f)
        assertFalse("sigmoid(-1000) should not be NaN", result.isNaN())
        assertFalse("sigmoid(-1000) should not be Inf", result.isInfinite())
        assertEquals(0.0f, result, 0.001f)
    }

    // ─── pDrift ────────────────────────────────────────────────────────────────

    @Test
    fun `pDrift with default weights and zeros returns expected value`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
        val result = PhubbingClassifier.pDrift(features, weights)
        // sigmoid(0 + (-2.5)) = sigmoid(-2.5)
        val expected = PhubbingClassifier.sigmoid(-2.5f)
        assertEquals(expected, result, 0.001f)
    }

    @Test
    fun `pDrift output is in 0 to 1 range`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(4f, 1f, 1f, 3f, 1f, 1f, 1f))
        val result = PhubbingClassifier.pDrift(features, weights)
        assertTrue("pDrift should be in [0,1] but got $result", result in 0f..1f)
    }

    @Test
    fun `pDrift increases with more phubbing signals`() {
        val weights = LrWeights.default()
        val lowSignal = FeatureVector(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
        val highSignal = FeatureVector(floatArrayOf(4f, 1f, 1f, 3f, 1f, 1f, 1f))
        val low = PhubbingClassifier.pDrift(lowSignal, weights)
        val high = PhubbingClassifier.pDrift(highSignal, weights)
        assertTrue("High-signal pDrift ($high) should exceed low-signal ($low)", high > low)
    }

    // ─── pPhub / BLE hard gate ─────────────────────────────────────────────────

    @Test
    fun `pPhub with BLE zero returns 0 regardless of pDrift`() {
        // BLE hard gate: ANY pDrift * 0 * anything = 0
        val result = PhubbingClassifier.pPhub(pDrift = 0.99f, bleSocial = 0f, vadEnergy = 1f)
        assertEquals(0.0f, result, 0.001f)
    }

    @Test
    fun `pPhub with BLE one and no VAD uses raw pDrift`() {
        val result = PhubbingClassifier.pPhub(pDrift = 0.7f, bleSocial = 1f, vadEnergy = 0f)
        assertEquals(0.7f, result, 0.001f)
    }

    @Test
    fun `pPhub with VAD active applies 1 15 multiplier`() {
        val base = 0.7f
        val expected = (base * 1.0f * PipelineConfig.VAD_MULTIPLIER).coerceIn(0f, 1f)
        val result = PhubbingClassifier.pPhub(pDrift = base, bleSocial = 1f, vadEnergy = 1f)
        assertEquals(expected, result, 0.001f)
    }

    @Test
    fun `pPhub is clamped to maximum 1 0`() {
        val result = PhubbingClassifier.pPhub(pDrift = 1.0f, bleSocial = 1f, vadEnergy = 1f)
        assertTrue("pPhub should be <= 1.0 but got $result", result <= 1.0f)
    }

    // ─── shouldNudge ──────────────────────────────────────────────────────────

    @Test
    fun `shouldNudge returns false exactly at threshold`() {
        assertFalse(PhubbingClassifier.shouldNudge(PipelineConfig.NUDGE_THRESHOLD))
    }

    @Test
    fun `shouldNudge returns true strictly above threshold`() {
        assertTrue(PhubbingClassifier.shouldNudge(PipelineConfig.NUDGE_THRESHOLD + 0.001f))
    }

    @Test
    fun `shouldNudge returns false below threshold`() {
        assertFalse(PhubbingClassifier.shouldNudge(0.0f))
        assertFalse(PhubbingClassifier.shouldNudge(0.5f))
    }

    // ─── updateWeights — label handling ────────────────────────────────────────

    @Test
    fun `updateWeights with label 1 shifts weights in correct direction`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(4f, 1f, 1f, 0f, 0.5f, 1f, 1f))
        val updated = PhubbingClassifier.updateWeights(features, 1.0f, weights)

        // label=1 means phone was put down; error = 1 - pDrift > 0
        // So weights should increase for positive features
        val pDrift = PhubbingClassifier.pDrift(features, weights)
        val error = 1.0f - pDrift
        if (error > 0) {
            // w1 has a positive feature (4f) so it should increase
            assertTrue(
                "w1 should increase when label=1 and x1>0, error=$error",
                updated.w[0] > weights.w[0]
            )
        }
    }

    @Test
    fun `updateWeights with label minus1 is a no-op`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(4f, 1f, 1f, 0f, 0.5f, 1f, 1f))
        val updated = PhubbingClassifier.updateWeights(features, -1.0f, weights)

        // -1.0 = ambiguous — must return the SAME weights unchanged
        assertArrayEquals(weights.w, updated.w, 0.0001f)
        assertEquals(weights.bias, updated.bias, 0.0001f)
        assertEquals(weights.updateCount, updated.updateCount)
    }

    @Test
    fun `updateWeights with label 0 produces different weights`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
        val updated = PhubbingClassifier.updateWeights(features, 0.0f, weights)
        // Even with zero features, bias changes
        assertNotEquals(weights.bias, updated.bias)
    }

    // ─── Immutability ──────────────────────────────────────────────────────────

    @Test
    fun `updateWeights never mutates the original LrWeights`() {
        val original = LrWeights.default()
        val originalW = original.w.copyOf() // snapshot
        val originalBias = original.bias
        val originalUpdateCount = original.updateCount

        val features = FeatureVector(floatArrayOf(4f, 1f, 1f, 0f, 1f, 1f, 1f))
        PhubbingClassifier.updateWeights(features, 1.0f, original)

        // original must be UNCHANGED
        assertArrayEquals(originalW, original.w, 0.0001f)
        assertEquals(originalBias, original.bias, 0.0001f)
        assertEquals(originalUpdateCount, original.updateCount)
    }

    @Test
    fun `updateWeights increments update count on real label`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(1f, 0.5f, 1f, 0f, 0.8f, 1f, 1f))
        val updated = PhubbingClassifier.updateWeights(features, 1.0f, weights)
        assertEquals(weights.updateCount + 1, updated.updateCount)
    }

    @Test
    fun `updateWeights does NOT increment update count on ambiguous label`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(1f, 0.5f, 1f, 0f, 0.8f, 1f, 1f))
        val updated = PhubbingClassifier.updateWeights(features, -1.0f, weights)
        assertEquals(weights.updateCount, updated.updateCount)
    }

    // ─── infer (convenience) ──────────────────────────────────────────────────

    @Test
    fun `infer returns non-null InferenceResult`() {
        val weights = LrWeights.default()
        val features = FeatureVector(floatArrayOf(4f, 1f, 1f, 0f, 1f, 1f, 1f))
        val result = PhubbingClassifier.infer(features, weights)
        assertFalse(result.pDrift.isNaN())
        assertFalse(result.pPhub.isNaN())
    }
}
