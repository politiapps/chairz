package app.lawnchair.shortcuttray

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.android.launcher3.model.data.AppInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the per-tab app lists for the Shortcut Tray.
 *
 * The base list always comes from the launcher's own [com.android.launcher3.allapps.AllAppsStore]
 * (passed in as [AppInfo]s) so that icons, titles and work-profile handling match the rest of the
 * launcher. The tabs only reorder and filter that list.
 */
object ShortcutTrayRepository {

    /** Window used for the Frequent/Recent tabs. */
    private val USAGE_WINDOW_MS = TimeUnit.DAYS.toMillis(30)

    /**
     * Flattens the launcher's [AppInfo] list into the tray's model, collapsing to **one entry per
     * package**.
     *
     * [com.android.launcher3.allapps.AllAppsStore] can hold several [AppInfo]s for the same package
     * — a second launcher activity, or a work-profile copy — which would otherwise show the app
     * twice (and, worse, let the user launch a secondary entry point that isn't signed in). For each
     * package we keep the personal-profile main launcher activity where we can identify it, so a tap
     * always opens the same thing the home screen would.
     */
    fun toTrayApps(context: Context, apps: Array<AppInfo>): List<ShortcutTrayApp> {
        val personalUser = Process.myUserHandle()
        val pm = context.packageManager
        return apps
            .mapNotNull { info ->
                val pkg = info.targetComponent?.packageName ?: return@mapNotNull null
                val label = info.title?.toString().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                ShortcutTrayApp(packageName = pkg, label = label, info = info)
            }
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                if (entries.size == 1) return@map entries[0]
                val mainComponent = try {
                    pm.getLaunchIntentForPackage(pkg)?.component
                } catch (_: Exception) {
                    null
                }
                entries.firstOrNull { it.info.user == personalUser && it.info.targetComponent == mainComponent }
                    ?: entries.firstOrNull { it.info.targetComponent == mainComponent }
                    ?: entries.firstOrNull { it.info.user == personalUser }
                    ?: entries[0]
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Apps ordered by most recently installed or updated. Uses only [PackageManager], so this needs
     * no special permission.
     */
    suspend fun newUpdated(context: Context, apps: List<ShortcutTrayApp>): List<ShortcutTrayApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        apps
            .mapNotNull { app ->
                val time = try {
                    val pkgInfo = pm.getPackageInfo(app.packageName, 0)
                    maxOf(pkgInfo.firstInstallTime, pkgInfo.lastUpdateTime)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                app to time
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Apps ordered by total foreground time over the last 30 days.
     *
     * Note: [UsageStats] exposes no *public* launch count (`mAppLaunchCount` is hidden), so
     * foreground time is the closest public proxy for "frequent".
     */
    suspend fun frequent(context: Context, apps: List<ShortcutTrayApp>): List<ShortcutTrayApp> = rankByUsage(context, apps) { it.totalTimeInForeground }

    /** Apps ordered by when they were last used. */
    suspend fun recent(context: Context, apps: List<ShortcutTrayApp>): List<ShortcutTrayApp> = rankByUsage(context, apps) { it.lastTimeUsed }

    private suspend fun rankByUsage(
        context: Context,
        apps: List<ShortcutTrayApp>,
        selector: (UsageStats) -> Long,
    ): List<ShortcutTrayApp> = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return@withContext emptyList()
        val end = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(end - USAGE_WINDOW_MS, end)

        apps
            .mapNotNull { app ->
                if (app.packageName == context.packageName) return@mapNotNull null
                val value = stats[app.packageName]?.let(selector) ?: return@mapNotNull null
                if (value <= 0L) return@mapNotNull null
                app to value
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Case-insensitive substring filter over the currently-visible tab. */
    fun filter(apps: List<ShortcutTrayApp>, query: String): List<ShortcutTrayApp> {
        if (query.isBlank()) return apps
        val q = query.trim().lowercase()
        return apps.filter { it.label.lowercase().contains(q) }
    }
}
