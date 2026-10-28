package com.pulse.app.debug

import androidx.activity.ComponentActivity

/**
 * Release no-op counterpart of [SleepDebugVerification]. Keeps the MainActivity
 * call site the same in both build variants while ensuring debug behavior is
 * completely absent from release builds.
 */
internal object SleepDebugVerification {
    fun trigger(activity: ComponentActivity) = Unit
}
