package app.lawnchair.shortcuttray

import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo

/**
 * A single app entry shown in the Shortcut Tray. [info] is retained so the app can be launched
 * through the launcher's normal [com.android.launcher3.Launcher.startActivitySafely] path.
 */
data class ShortcutTrayApp(
    val packageName: String,
    val label: String,
    val info: AppInfo,
)

enum class ShortcutTrayTab(@StringRes val labelRes: Int) {
    FREQUENT(R.string.shortcut_tray_tab_frequent),
    RECENT(R.string.shortcut_tray_tab_recent),
    NEW_UPDATED(R.string.shortcut_tray_tab_new_updated),
    ;

    /** Whether populating this tab needs the user to have granted usage access. */
    val needsUsageAccess: Boolean get() = this == FREQUENT || this == RECENT
}
