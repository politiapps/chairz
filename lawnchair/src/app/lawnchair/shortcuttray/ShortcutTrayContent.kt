package app.lawnchair.shortcuttray

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import app.lawnchair.LawnchairLauncher
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.ProvideLifecycleState
import app.lawnchair.util.lifecycleState
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.views.BaseDragLayer
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GRID_COLUMNS = 7
private const val GRID_ROWS = 2

/** Hard cap of two rows: 7 columns x 2 rows = 14 apps, no scrolling past them. */
private const val GRID_MAX_APPS = GRID_COLUMNS * GRID_ROWS

/** Compact two-row grid — kept short so plenty of the home screen shows beneath the tray. */
private val GRID_HEIGHT = 168.dp

/** Icon graphic is smaller than the 48dp cell so the tap target stays accessible. */
private val ICON_SIZE = 40.dp

@Composable
internal fun ShortcutTrayContent(
    launcher: LawnchairLauncher,
    onDismiss: () -> Unit,
) {
    // Force the light variant of the launcher's own theme so the tray reads as a bright card
    // (matching the reference), while still picking up the user's accent colour.
    LawnchairTheme(darkTheme = false) {
        ProvideLifecycleState {
            Surface(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TrayBody(
                    launcher = launcher,
                    onDismiss = onDismiss,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun TrayBody(
    launcher: LawnchairLauncher,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Pager drives the active tab, so a horizontal swipe anywhere over the grid switches tabs.
    val pagerState = rememberPagerState(initialPage = ShortcutTrayTab.FREQUENT.ordinal) {
        ShortcutTrayTab.entries.size
    }
    val scope = rememberCoroutineScope()
    val selectedTab = ShortcutTrayTab.entries[pagerState.currentPage]

    // Base list comes from the launcher's own store, so icons/titles match the rest of the launcher.
    val allApps = remember {
        ShortcutTrayRepository.toTrayApps(launcher, launcher.appsView.appsStore.apps)
    }

    // Re-read the usage-access grant whenever the launcher resumes, so returning from the system
    // settings screen updates the tray without the user having to reopen it.
    val lifecycle = lifecycleState()
    val hasUsageAccess = remember(lifecycle) {
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED)) UsageAccess.isGranted(launcher) else false
    }

    // Load all three tabs up front so swiping between pages doesn't flicker empty.
    var tabApps by remember { mutableStateOf<Map<ShortcutTrayTab, List<ShortcutTrayApp>>>(emptyMap()) }
    LaunchedEffect(allApps, hasUsageAccess) {
        tabApps = withContext(Dispatchers.Default) {
            ShortcutTrayTab.entries.associateWith { tab ->
                when (tab) {
                    ShortcutTrayTab.FREQUENT ->
                        if (hasUsageAccess) ShortcutTrayRepository.frequent(launcher, allApps) else emptyList()

                    ShortcutTrayTab.RECENT ->
                        if (hasUsageAccess) ShortcutTrayRepository.recent(launcher, allApps) else emptyList()

                    ShortcutTrayTab.NEW_UPDATED ->
                        ShortcutTrayRepository.newUpdated(launcher, allApps)
                }
            }
        }
    }

    // Auto-focus the search field and raise the keyboard as soon as the tray opens.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val onLaunch: (ShortcutTrayApp) -> Unit = { app ->
        launcher.startActivitySafely(null, app.info.intent, app.info)
        onDismiss()
    }

    // Long-press mirrors the home screen: show Launcher3's own app-shortcut popup (app shortcuts,
    // App info, Uninstall, Pause, …) anchored over the pressed tray icon.
    val onLongPress: (ShortcutTrayApp, Rect) -> Unit = { app, boundsInWindow ->
        launcher.hideKeyboard()
        showAppPopup(launcher, app, boundsInWindow)
    }

    Column(modifier = modifier) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onPlayStoreSearch = { launchPlayStoreSearch(context, query) },
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        TabRow(selectedTabIndex = pagerState.currentPage) {
            ShortcutTrayTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        if (query.isNotBlank()) {
            // Filter the visible tab first; if the query matches nothing there, fall through to all apps.
            val tabResults = ShortcutTrayRepository.filter(tabApps[selectedTab].orEmpty(), query)
            val fellThrough = tabResults.isEmpty()
            val results = if (fellThrough) ShortcutTrayRepository.filter(allApps, query) else tabResults
            AppGrid(apps = results, fellThrough = fellThrough, onClick = onLaunch, onLongClick = onLongPress)
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GRID_HEIGHT),
            ) { page ->
                val tab = ShortcutTrayTab.entries[page]
                if (tab.needsUsageAccess && !hasUsageAccess) {
                    UsageAccessPrompt(
                        onGrant = { UsageAccess.requestAccess(launcher) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AppGrid(
                        apps = tabApps[tab].orEmpty(),
                        fellThrough = false,
                        onClick = onLaunch,
                        onLongClick = onLongPress,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onPlayStoreSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        // Plain field with just a bottom line — no filled pill.
        shape = RoundedCornerShape(0.dp),
        placeholder = { Text(stringResource(R.string.shortcut_tray_hint)) },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.shortcut_tray_clear),
                        )
                    }
                }
                // Always visible: search the Play Store for whatever is typed (empty = plain store search).
                IconButton(onClick = onPlayStoreSearch) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_store),
                        contentDescription = stringResource(R.string.shortcut_tray_play_store),
                        tint = Color.Unspecified,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onPlayStoreSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

/**
 * Opens the Play Store searching for [query] (blank simply opens store search). Falls back to the
 * Play Store web page when no market app is installed — same convention as
 * [app.lawnchair.qsb.providers.QsbSearchProvider.launchOnAppMarket].
 */
private fun launchPlayStoreSearch(context: Context, query: String) {
    val encoded = Uri.encode(query.trim())
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded")),
        )
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=$encoded"),
                ),
            )
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.error_no_market_or_browser_installed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/**
 * Shows Launcher3's standard app-shortcut popup — the same menu you get from the home screen (app
 * shortcuts, App info, Uninstall, Pause, …) — anchored over the tray icon at [boundsInWindow].
 *
 * [PopupContainerWithArrow.showForIcon] positions itself from a real anchor View's bounds, so we add
 * a throwaway, invisible [BubbleTextView] to the drag layer over the tapped cell and use that as the
 * anchor. It is removed again as soon as the popup closes.
 */
private fun showAppPopup(launcher: LawnchairLauncher, app: ShortcutTrayApp, boundsInWindow: Rect) {
    val info: AppInfo = app.info
    val dragLayer = launcher.dragLayer

    val anchor = LayoutInflater.from(launcher)
        .inflate(R.layout.all_apps_icon, dragLayer, false) as BubbleTextView
    anchor.applyFromApplicationInfo(info)
    anchor.visibility = View.INVISIBLE

    // Convert the cell's window coordinates into drag-layer-local coordinates for the anchor.
    val dragLayerPos = IntArray(2)
    dragLayer.getLocationInWindow(dragLayerPos)
    anchor.layoutParams = BaseDragLayer.LayoutParams(
        boundsInWindow.width.roundToInt(),
        boundsInWindow.height.roundToInt(),
    ).apply {
        leftMargin = (boundsInWindow.left - dragLayerPos[0]).roundToInt()
        topMargin = (boundsInWindow.top - dragLayerPos[1]).roundToInt()
    }
    dragLayer.addView(anchor)

    // Wait one layout pass so the anchor has real bounds before the popup reads them.
    anchor.post {
        val popup = PopupContainerWithArrow.showForIcon(anchor)
        if (popup == null) {
            dragLayer.removeView(anchor)
        } else {
            popup.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    dragLayer.removeView(anchor)
                }
            })
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<ShortcutTrayApp>,
    fellThrough: Boolean,
    onClick: (ShortcutTrayApp) -> Unit,
    onLongClick: (ShortcutTrayApp, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(GRID_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.shortcut_tray_no_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (fellThrough) {
            Text(
                text = stringResource(R.string.shortcut_tray_all_apps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
        }
        // Cap to two rows and disable scrolling so there's nothing to reveal past row two.
        val visibleApps = apps.take(GRID_MAX_APPS)
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            userScrollEnabled = false,
        ) {
            items(visibleApps, key = { it.info.toComponentKey().toString() }) { app ->
                AppCell(
                    app = app,
                    onClick = { onClick(app) },
                    onLongClick = { bounds -> onLongClick(app, bounds) },
                )
            }
        }
    }
}

@Composable
private fun AppCell(
    app: ShortcutTrayApp,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
) {
    // Track the cell's on-screen bounds so a long-press can anchor the popup over this exact icon.
    var boundsInWindow by remember { mutableStateOf(Rect.Zero) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Cell padding keeps the tap target >= 48dp even though the icon graphic is 40dp.
        modifier = Modifier
            .onGloballyPositioned { boundsInWindow = it.boundsInWindow() }
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick(boundsInWindow) },
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        val icon = app.info.bitmap.icon
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun UsageAccessPrompt(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.shortcut_tray_usage_access_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.shortcut_tray_usage_access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.shortcut_tray_usage_access_action))
        }
    }
}
