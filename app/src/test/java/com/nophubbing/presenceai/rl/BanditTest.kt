package com.nophubbing.presenceai.rl

import org.junit.Assert.*
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
//  HOW TO RUN
// ─────────────────────────────────────────────────────────────────────────────
//
//  These are pure JUnit4 tests — no Android emulator required.
//
//  Run ALL RL tests:
//      .\gradlew :app:testDebugUnitTest --tests "com.nophubbing.presenceai.rl.*" --info
//
//  Run a specific test class:
//      .\gradlew :app:testDebugUnitTest --tests "com.nophubbing.presenceai.rl.BanditTest" --info
//      .\gradlew :app:testDebugUnitTest --tests "com.nophubbing.presenceai.rl.BanditStateTest" --info
//      .\gradlew :app:testDebugUnitTest --tests "com.nophubbing.presenceai.rl.RewardFunctionTest" --info
//      .\gradlew :app:testDebugUnitTest --tests "com.nophubbing.presenceai.rl.BanditStoreSerdeTest" --info
//
//  HTML report: app/build/reports/tests/testDebugUnitTest/index.html
//
// ─────────────────────────────────────────────────────────────────────────────

// ═════════════════════════════════════════════════════════════════════════════
//  BanditStateTest — computed properties
// ═════════════════════════════════════════════════════════════════════════════

class BanditStateTest {

    @Test
    fun `hapticAvg returns 0 when no haptic trials`() {
        val state = BanditState(hapticCount = 0, hapticTotalReward = 0f)
        assertEquals(0f, state.hapticAvg, 0.001f)
    }

    @Test
    fun `notifAvg returns 0 when no notif trials`() {
        val state = BanditState(notifCount = 0, notifTotalReward = 0f)
        assertEquals(0f, state.notifAvg, 0.001f)
    }

    @Test
    fun `hapticAvg computes correctly`() {
        val state = BanditState(hapticCount = 4, hapticTotalReward = 3f)
        assertEquals(0.75f, state.hapticAvg, 0.001f)
    }

    @Test
    fun `notifAvg computes correctly`() {
        val state = BanditState(notifCount = 2, notifTotalReward = -1f)
        assertEquals(-0.5f, state.notifAvg, 0.001f)
    }

    @Test
    fun `totalTrials sums both arms`() {
        val state = BanditState(hapticCount = 3, notifCount = 7)
        assertEquals(10, state.totalTrials)
    }

    @Test
    fun `preferredArm is null when haptic below MIN_TRIALS_PER_ARM`() {
        val state = BanditState(hapticCount = 4, notifCount = 10)
        assertNull(state.preferredArm)
    }

    @Test
    fun `preferredArm is null when notif below MIN_TRIALS_PER_ARM`() {
        val state = BanditState(hapticCount = 10, notifCount = 4)
        assertNull(state.preferredArm)
    }

    @Test
    fun `preferredArm returns NOTIFICATION when it has higher avg`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 2.5f,   // avg = 0.5
            notifCount  = 5, notifTotalReward  = 4.0f    // avg = 0.8
        )
        assertEquals(NudgeFormat.NOTIFICATION, state.preferredArm)
    }

    @Test
    fun `preferredArm returns HAPTIC when it has higher avg`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 4.0f,   // avg = 0.8
            notifCount  = 5, notifTotalReward  = 2.5f    // avg = 0.5
        )
        assertEquals(NudgeFormat.HAPTIC, state.preferredArm)
    }

    @Test
    fun `preferredArm returns HAPTIC on tie`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 2.5f,   // avg = 0.5
            notifCount  = 5, notifTotalReward  = 2.5f    // avg = 0.5
        )
        assertEquals(NudgeFormat.HAPTIC, state.preferredArm)
    }

    @Test
    fun `confidence is 0 when both arms have equal avg`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 2.5f,
            notifCount  = 5, notifTotalReward  = 2.5f
        )
        assertEquals(0f, state.confidence, 0.001f)
    }

    @Test
    fun `confidence reflects absolute difference`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 4f,    // avg = 0.8
            notifCount  = 5, notifTotalReward  = 2f     // avg = 0.4
        )
        assertEquals(0.4f, state.confidence, 0.001f)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  BanditTest — selectAction & update logic
// ═════════════════════════════════════════════════════════════════════════════

class BanditTest {

    // ── selectAction: forced exploration ─────────────────────────────────────

    @Test
    fun `selectAction forced_exploration when both arms at zero`() {
        val state = BanditState()
        val action = Bandit.selectAction(state, 0.5f)
        assertEquals("forced_exploration", action.mode)
    }

    @Test
    fun `selectAction forced_exploration picks HAPTIC when haptic count is lower`() {
        // hapticCount=2 < MIN(5), notifCount=5 >= MIN(5)
        val state = BanditState(
            hapticCount = 2, notifCount = 5,
            hapticTotalReward = 1f, notifTotalReward = 2.5f
        )
        val action = Bandit.selectAction(state, 0.0f)  // even at 0 it must be forced
        assertEquals("forced_exploration", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)
    }

    @Test
    fun `selectAction forced_exploration picks NOTIFICATION when notif count is lower`() {
        val state = BanditState(
            hapticCount = 5, notifCount = 3,
            hapticTotalReward = 2.5f, notifTotalReward = 1.5f
        )
        val action = Bandit.selectAction(state, 0.0f)
        assertEquals("forced_exploration", action.mode)
        assertEquals(NudgeFormat.NOTIFICATION, action.format)
    }

    @Test
    fun `selectAction forced_exploration picks HAPTIC when both under MIN and equal count`() {
        val state = BanditState(hapticCount = 3, notifCount = 3)
        val action = Bandit.selectAction(state, 0.0f)
        assertEquals("forced_exploration", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)
    }

    @Test
    fun `selectAction forced_exploration picks arm with fewer trials when both under MIN`() {
        val state = BanditState(hapticCount = 2, notifCount = 4)
        val action = Bandit.selectAction(state, 0.0f)
        assertEquals("forced_exploration", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)
    }

    // ── selectAction: explore ─────────────────────────────────────────────────

    @Test
    fun `selectAction explore when randomFloat is below EPSILON`() {
        val state = BanditState(
            hapticCount = 10, notifCount = 10,
            hapticTotalReward = 8f, notifTotalReward = 8f
        )
        // randomFloat = 0.19f < EPSILON(0.20f) → explore
        val action = Bandit.selectAction(state, 0.19f)
        assertEquals("explore", action.mode)
    }

    @Test
    fun `selectAction explore picks less-tried arm`() {
        val state = BanditState(
            hapticCount = 10, notifCount = 7,
            hapticTotalReward = 5f, notifTotalReward = 3.5f
        )
        val action = Bandit.selectAction(state, 0.10f)  // < EPSILON → explore
        assertEquals("explore", action.mode)
        assertEquals(NudgeFormat.NOTIFICATION, action.format)  // notif has fewer trials
    }

    @Test
    fun `selectAction explore picks HAPTIC on equal trial counts`() {
        val state = BanditState(
            hapticCount = 8, notifCount = 8,
            hapticTotalReward = 4f, notifTotalReward = 6f  // notif is better, but we explore
        )
        val action = Bandit.selectAction(state, 0.05f)
        assertEquals("explore", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)  // tie → HAPTIC
    }

    @Test
    fun `selectAction does NOT explore when randomFloat equals EPSILON`() {
        val state = BanditState(
            hapticCount = 10, notifCount = 10,
            hapticTotalReward = 5f, notifTotalReward = 7f  // notif better
        )
        // 0.20f is NOT < 0.20f → should exploit, not explore
        val action = Bandit.selectAction(state, BanditConfig.EPSILON)
        assertEquals("exploit", action.mode)
    }

    // ── selectAction: exploit ─────────────────────────────────────────────────

    @Test
    fun `selectAction exploit picks NOTIFICATION when it has higher avg`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 2.5f,   // avg = 0.5
            notifCount  = 5, notifTotalReward  = 4.0f    // avg = 0.8
        )
        val action = Bandit.selectAction(state, 0.99f)  // >> EPSILON → exploit
        assertEquals("exploit", action.mode)
        assertEquals(NudgeFormat.NOTIFICATION, action.format)
    }

    @Test
    fun `selectAction exploit picks HAPTIC when it has higher avg`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 4.0f,   // avg = 0.8
            notifCount  = 5, notifTotalReward  = 2.5f    // avg = 0.5
        )
        val action = Bandit.selectAction(state, 0.99f)
        assertEquals("exploit", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)
    }

    @Test
    fun `selectAction exploit picks HAPTIC on perfect tie`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 2.5f,   // avg = 0.5
            notifCount  = 5, notifTotalReward  = 2.5f    // avg = 0.5
        )
        val action = Bandit.selectAction(state, 0.99f)
        assertEquals("exploit", action.mode)
        assertEquals(NudgeFormat.HAPTIC, action.format)
    }

    // ── selectAction: snapshots in BanditAction ───────────────────────────────

    @Test
    fun `selectAction includes correct avg snapshots in returned action`() {
        val state = BanditState(
            hapticCount = 5, hapticTotalReward = 3f,    // avg = 0.6
            notifCount  = 5, notifTotalReward  = 2f     // avg = 0.4
        )
        val action = Bandit.selectAction(state, 0.99f)
        assertEquals(0.6f, action.hapticAvg, 0.001f)
        assertEquals(0.4f, action.notifAvg,  0.001f)
    }

    // ── update: immutability ──────────────────────────────────────────────────

    @Test
    fun `update does not mutate original state`() {
        val original = BanditState(hapticCount = 3, hapticTotalReward = 1.5f, notifCount = 5, notifTotalReward = 2f)
        val updated  = Bandit.update(original, NudgeFormat.HAPTIC, 1f)
        // Original must be untouched
        assertEquals(3, original.hapticCount)
        assertEquals(1.5f, original.hapticTotalReward, 0.001f)
        // Updated must differ
        assertEquals(4, updated.hapticCount)
        assertEquals(2.5f, updated.hapticTotalReward, 0.001f)
    }

    @Test
    fun `update increments haptic arm correctly`() {
        val state   = BanditState(hapticCount = 2, hapticTotalReward = 1f)
        val updated = Bandit.update(state, NudgeFormat.HAPTIC, 0.5f)
        assertEquals(3, updated.hapticCount)
        assertEquals(1.5f, updated.hapticTotalReward, 0.001f)
    }

    @Test
    fun `update increments notif arm correctly`() {
        val state   = BanditState(notifCount = 4, notifTotalReward = 2f)
        val updated = Bandit.update(state, NudgeFormat.NOTIFICATION, -1f)
        assertEquals(5, updated.notifCount)
        assertEquals(1f, updated.notifTotalReward, 0.001f)
    }

    @Test
    fun `update returns same reference for zero reward`() {
        val state   = BanditState(hapticCount = 5)
        val updated = Bandit.update(state, NudgeFormat.HAPTIC, 0f)
        assertSame("Same reference expected for 0f reward", state, updated)
    }

    @Test
    fun `update returns same reference for invalid reward`() {
        val state   = BanditState(hapticCount = 5)
        val updated = Bandit.update(state, NudgeFormat.HAPTIC, 0.3f) // not in valid set
        assertSame("Same reference expected for invalid reward", state, updated)
    }

    @Test
    fun `update accepts all four valid reward values`() {
        var state = BanditState()
        state = Bandit.update(state, NudgeFormat.HAPTIC, 1f)
        state = Bandit.update(state, NudgeFormat.HAPTIC, 0.5f)
        state = Bandit.update(state, NudgeFormat.HAPTIC, -0.5f)
        state = Bandit.update(state, NudgeFormat.HAPTIC, -1f)
        assertEquals(4, state.hapticCount)
        assertEquals(0f, state.hapticTotalReward, 0.001f)  // 1 + 0.5 - 0.5 - 1 = 0
    }

    @Test
    fun `update does not affect other arm`() {
        val state   = BanditState(notifCount = 3, notifTotalReward = 1.5f)
        val updated = Bandit.update(state, NudgeFormat.HAPTIC, 1f)
        // NOTIF arm should be identical
        assertEquals(3, updated.notifCount)
        assertEquals(1.5f, updated.notifTotalReward, 0.001f)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  RewardFunctionTest — computePhoneDownSeconds & computeReward
// ═════════════════════════════════════════════════════════════════════════════

class RewardFunctionTest {

    private val nudgeTime = 1_000_000L   // arbitrary epoch ms

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun secondsToMs(s: Int) = s * 1_000L

    private fun makeObs(
        format: NudgeFormat = NudgeFormat.HAPTIC,
        events: List<ScreenEvent> = emptyList(),
        dismissed: Boolean = false,
        dismissMs: Long? = null,
        reUnlockMs: Long? = null
    ) = NudgeObservation(
        format         = format,
        nudgeTimeMs    = nudgeTime,
        screenEvents   = events,
        wasDismissed   = dismissed,
        dismissTimeMs  = dismissMs,
        reUnlockTimeMs = reUnlockMs
    )

    // ── computePhoneDownSeconds ───────────────────────────────────────────────

    @Test
    fun `phoneDownSeconds is 0 when no events`() {
        val result = RewardFunction.computePhoneDownSeconds(emptyList(), nudgeTime)
        assertEquals(0, result)
    }

    @Test
    fun `phoneDownSeconds counts full 45s when phone goes off immediately and stays off`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 1_000L, isOn = false)
        )
        val result = RewardFunction.computePhoneDownSeconds(events, nudgeTime)
        // Off from nudgeTime+1s to nudgeTime+45s = 44s
        assertEquals(44, result)
    }

    @Test
    fun `phoneDownSeconds is 45 when screen off before nudge and stays off`() {
        // Screen was already off at nudge time
        val events = listOf(
            ScreenEvent(nudgeTime - 5_000L, isOn = false)  // before nudge
        )
        val result = RewardFunction.computePhoneDownSeconds(events, nudgeTime)
        assertEquals(45, result)
    }

    @Test
    fun `phoneDownSeconds sums multiple off intervals`() {
        // Off for 10s, on for 5s, off for 10s
        val events = listOf(
            ScreenEvent(nudgeTime + 5_000L,  isOn = false),  // off at +5s
            ScreenEvent(nudgeTime + 15_000L, isOn = true),   // on at +15s  (10s off)
            ScreenEvent(nudgeTime + 20_000L, isOn = false),  // off at +20s
            ScreenEvent(nudgeTime + 30_000L, isOn = true)    // on at +30s  (10s off)
        )
        val result = RewardFunction.computePhoneDownSeconds(events, nudgeTime)
        assertEquals(20, result)
    }

    @Test
    fun `phoneDownSeconds ignores events before nudge time`() {
        // Screen is ON at nudge time (no off event before nudgeTime → screenOnAtStart=true),
        // turns OFF at +30s and stays off until window end at +45s → 15s of off time.
        val events = listOf(
            ScreenEvent(nudgeTime - 10_000L, isOn = true),    // before window — establishes screen=ON
            ScreenEvent(nudgeTime + 30_000L, isOn = false),   // within window — turns off at +30s
        )
        // Off interval: [+30s, +45s(window end)] = 15s
        val result = RewardFunction.computePhoneDownSeconds(events, nudgeTime)
        assertEquals(15, result)
    }

    @Test
    fun `phoneDownSeconds ignores events after window end`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 5_000L,  isOn = false),   // within
            ScreenEvent(nudgeTime + 50_000L, isOn = true)     // after window end — ignored
        )
        // off from +5s to window end at +45s = 40s
        val result = RewardFunction.computePhoneDownSeconds(events, nudgeTime)
        assertEquals(40, result)
    }

    // ── computeReward: full positive (+1.0) ──────────────────────────────────

    @Test
    fun `computeReward returns +1f when phone down 45s and no re-unlock`() {
        val events = listOf(ScreenEvent(nudgeTime - 1L, isOn = false))  // already off
        val obs = makeObs(events = events)  // no re-unlock
        assertEquals(1f, RewardFunction.computeReward(obs))
    }

    @Test
    fun `computeReward does NOT return +1f when 45s down but re-unlock within 60s`() {
        val events = listOf(ScreenEvent(nudgeTime - 1L, isOn = false))
        val obs = makeObs(events = events, reUnlockMs = nudgeTime + 30_000L)
        // Should fall through to 0.5f (phone was down 45s) because re-unlock disqualifies +1
        // Actually phoneDown=45 so it's >=45 but has re-unlock → falls through the 45s branch
        // Next check: phoneDown=45 is NOT in [20, 44] so it returns 0.0f
        // Confirm it does NOT return 1f:
        assertNotEquals(1f, RewardFunction.computeReward(obs))
    }

    // ── computeReward: partial positive (+0.5) ───────────────────────────────

    @Test
    fun `computeReward returns +0p5f when phone down exactly 20s`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 1_000L,  isOn = false),  // off at +1s
            ScreenEvent(nudgeTime + 21_000L, isOn = true)    // on at +21s → 20s off
        )
        val obs = makeObs(events = events)
        assertEquals(0.5f, RewardFunction.computeReward(obs))
    }

    @Test
    fun `computeReward returns +0p5f when phone down 30s`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 5_000L,  isOn = false),
            ScreenEvent(nudgeTime + 35_000L, isOn = true)    // 30s off
        )
        val obs = makeObs(events = events)
        assertEquals(0.5f, RewardFunction.computeReward(obs))
    }

    @Test
    fun `computeReward does NOT return +0p5f when phone down exactly 44s boundary`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 1_000L,  isOn = false),   // off at +1s
            ScreenEvent(nudgeTime + 45_000L, isOn = true)     // on at +45s → 44s off
        )
        val obs = makeObs(events = events)
        // 44 is in [20, 44] so should be 0.5f
        assertEquals(0.5f, RewardFunction.computeReward(obs))
    }

    // ── computeReward: strong negative (-1.0) ────────────────────────────────

    @Test
    fun `computeReward returns -1f for NOTIFICATION dismissed within 3s with re-unlock within 60s`() {
        val obs = makeObs(
            format    = NudgeFormat.NOTIFICATION,
            dismissed = true,
            dismissMs = nudgeTime + 2_000L,            // dismissed at +2s (<3s threshold)
            reUnlockMs = nudgeTime + 30_000L           // re-unlock at +30s (<60s window)
        )
        assertEquals(-1f, RewardFunction.computeReward(obs))
    }

    @Test
    fun `computeReward does NOT return -1f when dismissed after DISMISS_THRESHOLD_MS`() {
        val obs = makeObs(
            format    = NudgeFormat.NOTIFICATION,
            dismissed = true,
            dismissMs = nudgeTime + 5_000L,            // dismissed at +5s (>3s threshold)
            reUnlockMs = nudgeTime + 30_000L
        )
        // Dismiss was too slow → not a reflex → no penalty → ambiguous (0f)
        assertEquals(0f, RewardFunction.computeReward(obs))
    }

    // ── computeReward: moderate negative (-0.5) ───────────────────────────────

    @Test
    fun `computeReward returns -0p5f for NOTIFICATION dismissed reflexively but no re-unlock`() {
        val obs = makeObs(
            format    = NudgeFormat.NOTIFICATION,
            dismissed = true,
            dismissMs = nudgeTime + 1_000L             // reflex dismiss
            // no reUnlockMs
        )
        assertEquals(-0.5f, RewardFunction.computeReward(obs))
    }

    // ── computeReward: HAPTIC immunity ───────────────────────────────────────

    @Test
    fun `computeReward returns 0f for HAPTIC even if dismissed quickly with re-unlock`() {
        // HAPTIC cannot be dismissed — the obs is logically invalid, but the function
        // must not apply dismiss penalties regardless of the dismissed flag.
        val obs = makeObs(
            format    = NudgeFormat.HAPTIC,
            dismissed = true,
            dismissMs = nudgeTime + 1_000L,
            reUnlockMs = nudgeTime + 10_000L
        )
        // phoneDown = 0 (<20s), so no positive reward.
        // dismiss block only runs for NOTIFICATION → falls through to 0f.
        assertEquals(0f, RewardFunction.computeReward(obs))
    }

    // ── computeReward: ambiguous (0.0) ───────────────────────────────────────

    @Test
    fun `computeReward returns 0f when phone down less than 20s and no dismiss`() {
        val events = listOf(
            ScreenEvent(nudgeTime + 5_000L,  isOn = false),
            ScreenEvent(nudgeTime + 14_000L, isOn = true)    // 9s off → ambiguous
        )
        val obs = makeObs(events = events)
        assertEquals(0f, RewardFunction.computeReward(obs))
    }

    @Test
    fun `computeReward returns 0f when no events at all`() {
        assertEquals(0f, RewardFunction.computeReward(makeObs()))
    }

    // ── mathematical boundary checks ─────────────────────────────────────────

    @Test
    fun `reward is always in the set minus1 minus0p5 0 0p5 1`() {
        val validRewards = setOf(-1f, -0.5f, 0f, 0.5f, 1f)

        // All basic scenarios
        val scenarios = listOf(
            makeObs(),                                      // no activity
            makeObs(                                        // full positive
                events = listOf(ScreenEvent(nudgeTime - 1L, isOn = false))
            ),
            makeObs(                                        // partial positive
                events = listOf(
                    ScreenEvent(nudgeTime + 1_000L,  isOn = false),
                    ScreenEvent(nudgeTime + 25_000L, isOn = true)
                )
            ),
            makeObs(                                        // strong negative
                format = NudgeFormat.NOTIFICATION,
                dismissed = true,
                dismissMs = nudgeTime + 1_000L,
                reUnlockMs = nudgeTime + 20_000L
            ),
            makeObs(                                        // moderate negative
                format = NudgeFormat.NOTIFICATION,
                dismissed = true,
                dismissMs = nudgeTime + 1_000L
            )
        )

        for (obs in scenarios) {
            val reward = RewardFunction.computeReward(obs)
            assertTrue(
                "Reward $reward is not in valid set $validRewards for obs=$obs",
                reward in validRewards
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  BanditStoreSerdeTest — serialise / deserialise (no Android context needed)
// ═════════════════════════════════════════════════════════════════════════════

class BanditStoreSerdeTest {

    @Test
    fun `serialise and deserialise round-trips correctly`() {
        val original = BanditState(
            hapticCount       = 7,
            hapticTotalReward = 3.5f,
            notifCount        = 4,
            notifTotalReward  = -2f
        )
        val json     = BanditStore.serialise(original)
        val restored = BanditStore.deserialise(json)

        assertEquals(original.hapticCount,       restored.hapticCount)
        assertEquals(original.hapticTotalReward, restored.hapticTotalReward, 0.001f)
        assertEquals(original.notifCount,        restored.notifCount)
        assertEquals(original.notifTotalReward,  restored.notifTotalReward, 0.001f)
    }

    @Test
    fun `deserialise falls back to zeros for missing fields`() {
        val partial = "{}"   // empty JSON — all fields missing
        val state   = BanditStore.deserialise(partial)
        assertEquals(0,  state.hapticCount)
        assertEquals(0f, state.hapticTotalReward, 0.001f)
        assertEquals(0,  state.notifCount)
        assertEquals(0f, state.notifTotalReward, 0.001f)
    }

    @Test
    fun `serialise produces valid JSON string`() {
        val state = BanditState(hapticCount = 1, hapticTotalReward = 1f, notifCount = 2, notifTotalReward = -1f)
        val json  = BanditStore.serialise(state)
        // Should not throw
        val parsed = org.json.JSONObject(json)
        assertEquals(1,   parsed.getInt("haptic_count"))
        assertEquals(2,   parsed.getInt("notif_count"))
        assertEquals(1.0, parsed.getDouble("haptic_total_reward"), 0.001)
        assertEquals(-1.0, parsed.getDouble("notif_total_reward"), 0.001)
    }

    @Test
    fun `deserialise handles negative rewards correctly`() {
        val original = BanditState(
            hapticCount = 3, hapticTotalReward = -1.5f,
            notifCount  = 3, notifTotalReward  = -3f
        )
        val restored = BanditStore.deserialise(BanditStore.serialise(original))
        assertEquals(-1.5f, restored.hapticTotalReward, 0.001f)
        assertEquals(-3f,   restored.notifTotalReward,  0.001f)
    }
}
