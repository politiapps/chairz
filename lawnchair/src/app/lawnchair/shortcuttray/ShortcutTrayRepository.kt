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
     * Flattens the launcher's [AppInfo] list into the tray's model, keeping one entry per package
     * and user profile. This intentionally preserves both personal and work-profile copies of an
     * app while avoiding duplicate activities within either profile.
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
            .groupBy { it.packageName to it.info.user }
            .map { (key, entries) ->
                val (pkg, user) = key
                if (entries.size == 1) return@map entries[0]
                // PackageManager's convenience lookup is for the current profile. Use it to
                // choose the normal personal entry; for work profiles retain the first launcher
                // activity supplied by AllAppsStore rather than dropping the work copy.
                val mainComponent = if (user == personalUser) {
                    try {
                        pm.getLaunchIntentForPackage(pkg)?.component
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                entries.firstOrNull { it.info.targetComponent == mainComponent } ?: entries[0]
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
