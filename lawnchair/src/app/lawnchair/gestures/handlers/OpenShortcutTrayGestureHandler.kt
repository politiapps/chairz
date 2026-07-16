package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.shortcuttray.ShortcutTrayView

class OpenShortcutTrayGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        ShortcutTrayView.show(launcher)
    }
}
