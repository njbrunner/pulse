package com.pulse.app.debug

import androidx.activity.ComponentActivity

/**
 * Release no-op counterpart of [HeartRateDebugVerification]. Keeps the MainActivity
 * call site the same in both build variants while ensuring debug behavior is
 * completely absent from release builds.
 */
internal object HeartRateDebugVerification {
    fun trigger(activity: ComponentActivity) = Unit
}
