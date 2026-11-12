package com.pulse.app.debug

import androidx.activity.ComponentActivity

/**
 * Release no-op counterpart of [StepsDebugVerification]. Keeps the MainActivity
 * call site the same in both build variants while ensuring debug behavior is
 * completely absent from release builds.
 */
internal object StepsDebugVerification {
    fun trigger(activity: ComponentActivity) = Unit
}
