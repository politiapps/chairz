package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.shortcuttray.UsageAccess
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.lifecycleState
import com.android.launcher3.R

@Composable
fun ShortcutTrayPreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(id = R.string.shortcut_tray_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val prefs2 = preferenceManager2()
        val enabledAdapter = prefs2.shortcutTrayEnabled.getAdapter()

        PreferenceGroup(heading = stringResource(id = R.string.shortcut_tray_label)) {
            SwitchPreference(
                adapter = enabledAdapter,
                label = stringResource(id = R.string.shortcut_tray_enable),
                description = stringResource(id = R.string.shortcut_tray_enable_description),
            )
        }

        ExpandAndShrink(visible = enabledAdapter.state.value) {
            // Re-read on resume so returning from the system settings screen updates the row.
            val lifecycle = lifecycleState()
            val granted = remember(lifecycle) {
                lifecycle.isAtLeast(Lifecycle.State.RESUMED) && UsageAccess.isGranted(context)
            }

            PreferenceGroup(heading = stringResource(id = R.string.shortcut_tray_usage_access_title)) {
                ClickablePreference(
                    label = stringResource(
                        id = if (granted) {
                            R.string.shortcut_tray_usage_access_granted
                        } else {
                            R.string.shortcut_tray_usage_access_action
                        },
                    ),
                    subtitle = stringResource(id = R.string.shortcut_tray_usage_access_body),
                    onClick = { UsageAccess.requestAccess(context) },
                )
            }
        }
    }
}
