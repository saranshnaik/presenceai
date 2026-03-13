package com.nophubbing.presenceai.utils

import android.content.Context

object OnboardingManager {

    private const val PREF_NAME = "presenceai_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    fun isOnboardingComplete(context: Context): Boolean {

        val prefs =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingComplete(context: Context) {

        val prefs =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
    }
}