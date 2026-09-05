package com.example.brain.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages versioned first-run onboarding state.
 * Survives process death and app updates.
 */
class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("second_brain_onboarding_prefs", Context.MODE_PRIVATE)

    companion object {
        const val CURRENT_ONBOARDING_VERSION = 1
        private const val KEY_COMPLETED = "onboarding_completed"
        private const val KEY_VERSION = "onboarding_version"
        private const val KEY_COMPLETED_AT = "onboarding_completed_at"
    }

    fun isOnboardingCompleted(): Boolean {
        val completed = prefs.getBoolean(KEY_COMPLETED, false)
        val version = prefs.getInt(KEY_VERSION, 0)
        return completed && version >= CURRENT_ONBOARDING_VERSION
    }

    fun setOnboardingCompleted() {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putInt(KEY_VERSION, CURRENT_ONBOARDING_VERSION)
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .apply()
    }

    fun resetOnboarding() {
        prefs.edit().clear().apply()
    }

    fun getCompletedAt(): Long {
        return prefs.getLong(KEY_COMPLETED_AT, 0L)
    }
}
