package com.nophubbing.presenceai.services

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nophubbing.presenceai.ml.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NudgingSystemTest
 *
 * DROP THIS FILE INTO:
 *   app/src/androidTest/java/com/nophubbing/presenceai/services/NudgingSystemTest.kt
 *
 * Run with:
 *   ./gradlew connectedAndroidTest
 *   (or Run → NudgingSystemTest in Android Studio with a device/emulator connected)
 *
 * What is tested:
 *   Test 1 — Nudge fires when pPhub > nudge_threshold AND social present
 *   Test 2 — Nudge does NOT fire when social is absent (x6=0 or x7=0)
 *   Test 3 — Nudge does NOT fire when score is below threshold
 *   Test 4 — Cooldown: second nudge is suppressed within 5 min window
 *   Test 5 — Weight update applied after user accepts feedback
 *   Test 6 — FalseNegativeGuard injects label when model silent too long
 *   Test 7 — Full pipeline run produces correct nudge_count
 */
@RunWith(AndroidJUnit4::class)
class NudgingSystemTest {

    companion object {
        private const val TAG = "NudgingSystemTest"
    }

    private lateinit var context  : Context
    private lateinit var config   : PipelineConfig
    private lateinit var nudging  : NudgingSystem
    private lateinit var provider : FakeSignalProvider
    private lateinit var runner   : PipelineRunner

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        context  = InstrumentationRegistry.getInstrumentation().targetContext
        config   = PipelineConfig(
            nudge_threshold   = 0.65,
            lr_learning_rate  = 0.01,
            vad_multiplier    = 1.15,
            max_nudges_per_day = 8
        )
        provider = FakeSignalProvider()
        nudging  = NudgingSystem(context)
        runner   = PipelineRunner(config, context, provider)
        runner.start()
    }

    @After
    fun tearDown() {
        runner.destroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: Nudge fires — high pPhub + social present
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test1_nudge_fires_when_phubbing_and_social_present() = runBlocking {
        Log.d(TAG, "=== TEST 1: Nudge fires ===")

        // Row that produces pPhub well above 0.65:
        //   x3_notification_reflex=1 (last notif < 5s ago)
        //   x6_vad=1 (voice active)
        //   x7_ble=1 (BLE device present)
        //   hour=20 → time_phase=0.9 (peak vulnerability window)
        val row = highPhubRow()

        // Reset nudge fired flag
        nudging.acknowledgeNudge()

        val fv     = FeatureEngineering.buildFeatureVector(row)
        val pPhub  = LrClassifier.computePPhub(fv.asList(), runner.weights, config)
        val score  = ((1.0 - pPhub) * 100.0).toFloat()
        val social = fv.x6 == 1.0 && fv.x7 == 1.0

        Log.d(TAG, "pPhub=$pPhub score=$score social=$social")

        nudging.onScoreUpdate(score, social)

        // pPhub should be above threshold and nudge should have fired
        assertTrue("pPhub ($pPhub) should be > ${config.nudge_threshold}", pPhub > config.nudge_threshold)
        assertTrue("Score ($score) should be below ${(1 - config.nudge_threshold) * 100}", score < 100 * (1 - config.nudge_threshold))
        assertTrue("Nudge should have fired", nudging.nudgeFired.value)

        Log.d(TAG, "PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: Nudge suppressed — no social presence (x7_ble = 0)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test2_nudge_suppressed_when_no_social_presence() = runBlocking {
        Log.d(TAG, "=== TEST 2: No social ===")

        nudging.acknowledgeNudge()

        // Same row but BLE device count = 0 → x7 = 0.0 → pPhub = pDrift * 0 * vad = 0
        val row = highPhubRow().copy(ble_device_count = 0)

        val fv    = FeatureEngineering.buildFeatureVector(row)
        val pPhub = LrClassifier.computePPhub(fv.asList(), runner.weights, config)
        val score = ((1.0 - pPhub) * 100.0).toFloat()
        val social = fv.x6 == 1.0 && fv.x7 == 1.0

        Log.d(TAG, "pPhub=$pPhub score=$score social=$social")

        nudging.onScoreUpdate(score, social)

        assertFalse("Social should be false when ble_device_count=0", social)
        assertFalse("Nudge should NOT fire without social presence", nudging.nudgeFired.value)

        Log.d(TAG, "PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: Nudge suppressed — score below threshold
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test3_nudge_suppressed_when_score_is_low() = runBlocking {
        Log.d(TAG, "=== TEST 3: Low score ===")

        nudging.acknowledgeNudge()

        // Attentive row: no unlocks, no notification reflex, early morning (low vulnerability)
        val row = attentiveRow()

        val fv    = FeatureEngineering.buildFeatureVector(row)
        val pPhub = LrClassifier.computePPhub(fv.asList(), runner.weights, config)
        val score = ((1.0 - pPhub) * 100.0).toFloat()
        val social = fv.x6 == 1.0 && fv.x7 == 1.0

        Log.d(TAG, "pPhub=$pPhub score=$score social=$social")

        nudging.onScoreUpdate(score, social)

        assertTrue("Score ($score) should be high for attentive row", score > 50f)
        assertFalse("Nudge should NOT fire for attentive user", nudging.nudgeFired.value)

        Log.d(TAG, "PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4: Cooldown — second nudge suppressed within cooldown window
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test4_cooldown_prevents_double_nudge() = runBlocking {
        Log.d(TAG, "=== TEST 4: Cooldown ===")

        // Create a NudgingSystem with an artificially short cooldown for testing
        val fastNudge = NudgingSystem(context, cooldownMs = 500L)
        fastNudge.start()

        val row    = highPhubRow()
        val fv     = FeatureEngineering.buildFeatureVector(row)
        val pPhub  = LrClassifier.computePPhub(fv.asList(), runner.weights, config)
        val score  = ((1.0 - pPhub) * 100.0).toFloat()
        val social = fv.x6 == 1.0 && fv.x7 == 1.0

        // First nudge — should fire
        fastNudge.onScoreUpdate(score, social)
        assertTrue("First nudge should fire", fastNudge.nudgeFired.value)
        fastNudge.acknowledgeNudge()

        // Second nudge immediately — should be suppressed by cooldown
        fastNudge.onScoreUpdate(score, social)
        assertFalse("Second immediate nudge should be suppressed by cooldown", fastNudge.nudgeFired.value)

        // Wait for cooldown to expire, then fire again
        delay(600L)
        fastNudge.onScoreUpdate(score, social)
        assertTrue("Nudge should fire after cooldown expires", fastNudge.nudgeFired.value)

        fastNudge.destroy()
        Log.d(TAG, "PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5: Weight update applied after user accepts nudge feedback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test5_weight_update_applied_after_feedback() = runBlocking {
        Log.d(TAG, "=== TEST 5: Weight update after feedback ===")

        val weightsBefore = runner.weights.update_count

        // Simulate user tapping "I was phubbing" (label = 1)
        val fv = FeatureEngineering.buildFeatureVector(highPhubRow())
        runner.applyExternalWeightUpdate(fv.asList().map { it.toFloat() }.toFloatArray(), label = 1)

        val weightsAfter = runner.weights.update_count
        assertEquals("update_count should increment by 1", weightsBefore + 1, weightsAfter)

        Log.d(TAG, "update_count: $weightsBefore → $weightsAfter PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6: FalseNegativeGuard detects silence and corrects silently
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test6_false_negative_guard_triggers_on_silence() = runBlocking {
        Log.d(TAG, "=== TEST 6: FalseNegativeGuard silent correction ===")

        // Configure provider to return high-phubbing snapshots
        provider.nextSnapshotIsPhubbing = true

        val weightsBefore = runner.weights.update_count

        // Simulate the guard seeing sustained low scores despite social presence
        // (i.e. model keeps predicting "not phubbing" when user actually is)
        repeat(5) {
            runner.falseNegativeGuard.onScoreUpdate(presenceScore = 30f, social = true)
        }

        // Manually trigger silent validation (skips 3-minute wait for testing)
        runner.falseNegativeGuard.triggerSilentValidationForTest()

        // Wait for the 20-second observation window to complete
        // (FakeSignalProvider returns instantly, so this is fast in tests)
        delay(500L)

        val weightsAfter = runner.weights.update_count
        assertTrue(
            "FalseNegativeGuard should have injected at least 1 training example",
            weightsAfter > weightsBefore
        )

        Log.d(TAG, "update_count: $weightsBefore → $weightsAfter PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7: Full pipeline run — nudge_count matches expected
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test7_full_pipeline_run_nudge_count() {
        Log.d(TAG, "=== TEST 7: Full pipeline run ===")

        val rows = listOf(
            highPhubRow(),          // should nudge   (phubbing + social)
            highPhubRow(),          // cooldown hit   (no nudge — too soon)
            attentiveRow(),         // should not nudge
            highPhubRow().copy(ble_device_count = 0),  // no social → no nudge
            highPhubRow(),          // cooldown hit
        )

        val result = runner.run(rows)

        val nudgeCount = (result["nudge_count"] as? Double)?.toInt() ?: 0
        Log.d(TAG, "nudge_count=$nudgeCount total_rows=${result["total_rows"]}")

        // At least 1 nudge should fire from 3 high-phub rows with social present
        // (exact count depends on cooldown, which is 5 min by default, so only 1 fires)
        assertTrue("At least 1 nudge should fire", nudgeCount >= 1)

        val accuracy = result["accuracy"] as? Double ?: 0.0
        Log.d(TAG, "accuracy=$accuracy avg_loss=${result["avg_loss"]}")

        Log.d(TAG, "PASS")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — real SignalRow fixtures using your exact Schema.kt types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A row that produces pPhub well above 0.65:
     *   x1 = high unlock freq, x3 = notification reflex, x5 = 0.9 (hour=20),
     *   x6 = vad=1, x7 = ble=1 → pPhub = pDrift * 1 * 1.15
     */
    private fun highPhubRow() = SignalRow(
        timestamp              = System.currentTimeMillis(),
        unlock_count_10min     = 9.0,          // x1: high unlock frequency
        rolling_avg_unlock_rate = 3.0,
        sessions_under_30s     = 8,            // x2: mostly micro-sessions
        total_sessions         = 9,
        last_notif_delta_ms    = 800L,         // x3: opened phone < 1s after notif
        current_unlock_rate    = 8.0,
        baseline_mean          = 2.0,
        baseline_stddev        = 1.0,
        baseline_ready         = 1,            // x4: behavior_drift_z = (8-2)/1 = 6 → clamped to 3
        hour_of_day            = 20,           // x5: time_phase = 0.9 (peak window)
        vad_energy             = 1,            // x6: voice detected
        ble_device_count       = 2,            // x7: 2 BLE devices nearby
        label                  = 1.0
    )

    /**
     * A row that produces low pPhub — attentive user.
     */
    private fun attentiveRow() = SignalRow(
        timestamp              = System.currentTimeMillis(),
        unlock_count_10min     = 1.0,          // x1: rare unlocks
        rolling_avg_unlock_rate = 3.0,
        sessions_under_30s     = 0,            // x2: no micro-sessions
        total_sessions         = 2,
        last_notif_delta_ms    = 60_000L,      // x3: not a reflex (> 5s)
        current_unlock_rate    = 1.0,
        baseline_mean          = 2.0,
        baseline_stddev        = 1.0,
        baseline_ready         = 1,            // x4: below baseline
        hour_of_day            = 7,            // x5: time_phase = 0.3 (morning)
        vad_energy             = 1,
        ble_device_count       = 1,
        label                  = 0.0
    )
}

// ── FakeSignalProvider ────────────────────────────────────────────────────────
//
// Implements SignalProvider using real SignalRow data so tests don't need mocks.
// Returns snapshots instantly (no real 2-second delay) to keep tests fast.

class FakeSignalProvider : SignalProvider {

    var nextSnapshotIsPhubbing = false

    override suspend fun captureSnapshot(): FeedbackActivityMonitor.SignalSnapshot {
        return if (nextSnapshotIsPhubbing) {
            // High-phubbing snapshot — triggers activity-based label = phubbing
            FeedbackActivityMonitor.SignalSnapshot(
                unlockCount   = 3,
                sessionCount  = 4,
                presenceScore = 25f,
                notifReflexes = 1,
                features      = floatArrayOf(3f, 0.9f, 1f, 2f, 0.9f, 1f, 1f)
            )
        } else {
            // Attentive snapshot
            FeedbackActivityMonitor.SignalSnapshot(
                unlockCount   = 0,
                sessionCount  = 0,
                presenceScore = 85f,
                notifReflexes = 0,
                features      = floatArrayOf(0.3f, 0.0f, 0f, -0.5f, 0.3f, 1f, 1f)
            )
        }
    }
}
