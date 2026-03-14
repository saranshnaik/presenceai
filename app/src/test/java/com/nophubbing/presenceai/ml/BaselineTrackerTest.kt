package com.nophubbing.presenceai.ml

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [BaselineTracker].
 *
 * Pure JVM — no Android APIs required.
 * Run with: ./gradlew :app:test --tests "*.BaselineTrackerTest"
 */
class BaselineTrackerTest {

    // ─── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is not ready`() {
        val state = BaselineTracker.BaselineState.initial()
        assertFalse(BaselineTracker.isReady(state))
    }

    @Test
    fun `initial state has zero sample count`() {
        val state = BaselineTracker.BaselineState.initial()
        assertEquals(0, state.sampleCount)
    }

    @Test
    fun `initial stddev is zero`() {
        val state = BaselineTracker.BaselineState.initial()
        assertEquals(0f, BaselineTracker.stddev(state), 0.001f)
    }

    // ─── update — basic correctness ────────────────────────────────────────────

    @Test
    fun `first update sets mean to observation`() {
        val state = BaselineTracker.update(BaselineTracker.BaselineState.initial(), 5.0f)
        assertEquals(5.0f, state.mean, 0.001f)
        assertEquals(1, state.sampleCount)
    }

    @Test
    fun `update increments sample count`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(10) { state = BaselineTracker.update(state, 3.0f) }
        assertEquals(10, state.sampleCount)
    }

    @Test
    fun `update never produces NaN mean`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { state = BaselineTracker.update(state, it.toFloat()) }
        assertFalse("Mean should not be NaN", state.mean.isNaN())
    }

    @Test
    fun `update never produces NaN variance`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { state = BaselineTracker.update(state, it.toFloat()) }
        assertFalse("Variance should not be NaN", state.variance.isNaN())
    }

    // ─── Cold-start guard ─────────────────────────────────────────────────────

    @Test
    fun `not ready until MIN_SAMPLES_FOR_BASELINE samples with variance`() {
        var state = BaselineTracker.BaselineState.initial()
        val minSamples = PipelineConfig.MIN_SAMPLES_FOR_BASELINE
        // Feed varied data to get stddev > 0.1
        repeat(minSamples - 1) { i ->
            state = BaselineTracker.update(state, if (i % 2 == 0) 1.0f else 5.0f)
        }
        assertFalse("Should NOT be ready before $minSamples samples", BaselineTracker.isReady(state))
    }

    @Test
    fun `flat sequence never becomes ready even with enough samples`() {
        var state = BaselineTracker.BaselineState.initial()
        // All identical values = stddev ≈ 0 = not ready even with 100 samples
        repeat(100) { state = BaselineTracker.update(state, 3.0f) }
        // stddev is near 0, so isReady should return false
        assertFalse(
            "Flat sequence (stddev < 0.1) should not be ready",
            BaselineTracker.isReady(state)
        )
    }

    @Test
    fun `varied sequence with enough samples becomes ready`() {
        var state = BaselineTracker.BaselineState.initial()
        // Feed alternating values to produce meaningful variance
        repeat(100) { i ->
            state = BaselineTracker.update(state, if (i % 2 == 0) 2.0f else 8.0f)
        }
        assertTrue(
            "Varied sequence (stddev > 0.1) with 100 samples should be ready",
            BaselineTracker.isReady(state)
        )
    }

    // ─── computeDriftZ ───────────────────────────────────────────────────────

    @Test
    fun `computeDriftZ returns 0 when baseline not ready`() {
        val state = BaselineTracker.BaselineState.initial()
        assertEquals(0.0f, BaselineTracker.computeDriftZ(state, 999f), 0.001f)
    }

    @Test
    fun `computeDriftZ returns 0 when stddev is too small`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { state = BaselineTracker.update(state, 3.0f) }
        // stddev ≈ 0 → return 0
        assertEquals(0.0f, BaselineTracker.computeDriftZ(state, 100f), 0.001f)
    }

    @Test
    fun `computeDriftZ returns positive value above mean`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 2.0f else 6.0f) }
        if (BaselineTracker.isReady(state)) {
            val result = BaselineTracker.computeDriftZ(state, 50f)
            assertTrue("Far-above-mean value should give positive drift Z", result > 0f)
        }
    }

    @Test
    fun `computeDriftZ returns negative value below mean`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 4.0f else 8.0f) }
        if (BaselineTracker.isReady(state)) {
            val result = BaselineTracker.computeDriftZ(state, -100f)
            assertTrue("Far-below-mean value should give negative drift Z", result < 0f)
        }
    }

    @Test
    fun `computeDriftZ is clamped to max 3`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 3.0f else 7.0f) }
        if (BaselineTracker.isReady(state)) {
            val result = BaselineTracker.computeDriftZ(state, Float.MAX_VALUE)
            assertTrue("Max input should clamp to 3.0 but got $result", result <= 3.0f)
        }
    }

    @Test
    fun `computeDriftZ is clamped to min -3`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(100) { i -> state = BaselineTracker.update(state, if (i % 2 == 0) 3.0f else 7.0f) }
        if (BaselineTracker.isReady(state)) {
            val result = BaselineTracker.computeDriftZ(state, -Float.MAX_VALUE)
            assertTrue("Min input should clamp to -3.0 but got $result", result >= -3.0f)
        }
    }

    // ─── Immutability ─────────────────────────────────────────────────────────

    @Test
    fun `update returns NEW state and never mutates original`() {
        val original = BaselineTracker.BaselineState.initial()
        val originalCount = original.sampleCount
        val originalMean = original.mean

        val updated = BaselineTracker.update(original, 5.0f)

        // Original must be unchanged
        assertEquals(originalCount, original.sampleCount)
        assertEquals(originalMean, original.mean, 0.001f)

        // Updated must differ
        assertEquals(originalCount + 1, updated.sampleCount)
    }

    // ─── stddev ───────────────────────────────────────────────────────────────

    @Test
    fun `stddev is always non-negative`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(50) { state = BaselineTracker.update(state, it.toFloat() % 10) }
        assertTrue("stddev must be >= 0", BaselineTracker.stddev(state) >= 0f)
    }

    @Test
    fun `stddev is never NaN`() {
        var state = BaselineTracker.BaselineState.initial()
        repeat(50) { state = BaselineTracker.update(state, it.toFloat()) }
        assertFalse(BaselineTracker.stddev(state).isNaN())
    }
}
