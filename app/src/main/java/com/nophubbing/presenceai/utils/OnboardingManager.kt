package com.nophubbing.presenceai.utils

import android.content.Context
import android.content.SharedPreferences

object OnboardingManager {

    private const val PREF_NAME = "presenceai_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboardingComplete(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingComplete(context: Context) {
        prefs(context)
            .edit()
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
    }
}