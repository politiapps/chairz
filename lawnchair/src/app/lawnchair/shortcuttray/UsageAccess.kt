package app.lawnchair.shortcuttray

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Usage access (`PACKAGE_USAGE_STATS`) is an appop-backed permission. It is already declared in
 * `lawnchair/AndroidManifest.xml`, but for a non-privileged (sideloaded) build
 * `checkCallingOrSelfPermission` always reports DENIED regardless of whether the user granted it —
 * so the grant must be read through [AppOpsManager] instead. The existing
 * `checkCallingOrSelfPermission` call sites in this repo are inherited AOSP code that assumes a
 * privileged launcher; don't copy them here.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens the system usage-access settings page. There is no runtime-permission dialog for this
     * grant. Some OEM builds reject the per-package deep link, so fall back to the plain screen.
     */
    fun requestAccess(context: Context) {
        val perApp = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(perApp)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: ActivityNotFoundException) {
                // Device has no usage-access screen at all; nothing further we can do.
            }
        }
    }
}
