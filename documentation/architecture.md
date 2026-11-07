# Shortcut Tray architecture

- `LawnchairLauncher.handleHomeTap` opens `ShortcutTrayView` when the tray preference is enabled. `OpenShortcutTrayGestureHandler` provides the gesture entry point.
- `ShortcutTrayView` is a top-anchored `AbstractFloatingView` in the launcher drag layer. Its `ComposeView` renders `ShortcutTrayContent` and its scrim handles outside taps.
- `ShortcutTrayContent` observes `launcher.appsView.appsStore` using a composition-scoped update listener. The store starts empty and later publishes model loads and package updates.
- `ShortcutTrayRepository` converts `AppInfo` entries to tray entries, collapsing duplicate activities within each package/profile while retaining both personal and work-profile copies. Frequent and Recent use usage statistics; New/Updated uses package install/update timestamps.
- Search filters the current tab and searches the full tray list only if that tab has no matches. Results are capped at 14 with scrolling disabled.
- The tray forces a light Compose theme. `SystemUiController.UI_STATE_SHORTCUT_TRAY` requests dark status icons until detach, leaving navigation-bar appearance to existing states.
- Long press uses a temporary `BubbleTextView` anchor to start Launcher3's workspace drag. Dropping an `AppInfo` on the workspace converts it into a persistent home-screen shortcut; the tray hides for the drag and removes the anchor when the drop completes.
- Launcher3's all-apps container hides the IME on its first tabbed rebind (`ActivityAllAppsContainerView.onActivePageChanged`). The tray re-requests focus and the keyboard when its app list first becomes non-empty.
