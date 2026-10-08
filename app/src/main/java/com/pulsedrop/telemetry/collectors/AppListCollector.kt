package com.pulsedrop.telemetry.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.pulsedrop.telemetry.data.AppEvent
import com.pulsedrop.telemetry.data.InstalledApp
import com.pulsedrop.telemetry.data.UploadEnvelope
import com.pulsedrop.telemetry.utils.PrefsManager

class AppListCollector(
    private val context: Context,
    private val onData: (UploadEnvelope) -> Unit
) {
    private val prefs = PrefsManager(context)
    private val pm = context.packageManager

    fun collectAll(includeSystem: Boolean = false) {
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        packages.forEach { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) return@forEach

            try {
                val pkgInfo = pm.getPackageInfo(appInfo.packageName, 0)
                val app = InstalledApp(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    versionName = pkgInfo.versionName,
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                        pkgInfo.longVersionCode else pkgInfo.versionCode.toLong(),
                    installTime = pkgInfo.firstInstallTime,
                    isSystemApp = isSystem
                )
                onData(UploadEnvelope("installed_app", prefs.deviceId, System.currentTimeMillis(), app))
            } catch (e: Exception) { /* skip */ }
        }
    }

    // Broadcast receiver for install/uninstall events
    inner class PackageReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val event = when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> "installed"
                Intent.ACTION_PACKAGE_REMOVED -> "uninstalled"
                else -> return
            }
            val appEvent = AppEvent(
                packageName = packageName,
                event = event,
                timestamp = System.currentTimeMillis()
            )
            onData(UploadEnvelope("app_event", prefs.deviceId, System.currentTimeMillis(), appEvent))
        }
    }

    fun createFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
    }
}
