package com.creation.remoteman.util

import android.content.Context
import android.content.Intent
import com.creation.remoteman.PermissionActivity
import com.creation.remoteman.service.ScreenCaptureService

object AppUtils {

    /** Clears cache and relaunches the app from the first screen, like a fresh install. */
    fun restartApp(context: Context) {
        try {
            context.cacheDir?.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {
            // best-effort; a failed cache wipe shouldn't block the restart
        }

        val intent = Intent(context, PermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /**
     * Stops any active session and kills the app's own process — the same end
     * result as Android's "Force stop" in App info, just triggered from inside
     * the app. An app can't force-stop itself silently without this: killing
     * the process is what actually tears down every running component.
     */
    fun forceStop(context: Context) {
        try {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        } catch (_: Exception) {
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
