package app.lawnchair.shortcuttray

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnLayout
import app.lawnchair.LawnchairLauncher
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.views.BaseDragLayer

/**
 * The Shortcut Tray: a compact search-and-launch panel that slides down from the top of the home
 * screen, layered over the workspace. Shown by pressing Home while already on the home screen
 * (see `LawnchairLauncher.handleHomeTap`).
 *
 * This is deliberately *not* the full-screen app drawer — both coexist.
 *
 * [com.android.launcher3.views.AbstractSlideInView] is hard-wired for bottom-anchored sheets (its
 * swipe detector treats a downward fling as "dismiss"), so this extends [AbstractFloatingView]
 * directly and does its own top-anchored slide instead.
 */
@SuppressLint("ViewConstructor")
class ShortcutTrayView(private val launcher: LawnchairLauncher) : AbstractFloatingView(launcher, null) {

    private val composeView = ComposeView(launcher)

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP
        // Allow the Compose search field inside to take focus and raise the keyboard.
        isFocusableInTouchMode = true
        layoutParams = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            .apply { ignoreInsets = true }
        setBackgroundColor(ColorTokens.WidgetsPickerScrim.resolveColor(launcher))
        addView(composeView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        composeView.setContent {
            ShortcutTrayContent(
                launcher = launcher,
                onDismiss = { close(true) },
            )
        }
    }

    fun show() {
        mIsOpen = true
        launcher.dragLayer.addView(this)

        alpha = 0f
        animate().alpha(1f).setDuration(OPEN_DURATION).start()
        composeView.doOnLayout { view ->
            view.translationY = -view.height.toFloat()
            view.animate()
                .translationY(0f)
                .setDuration(OPEN_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun handleClose(animate: Boolean) {
        launcher.hideKeyboard()
        if (!animate) {
            detach()
            return
        }
        composeView.animate()
            .translationY(-composeView.height.toFloat())
            .setDuration(CLOSE_DURATION)
            .setInterpolator(AccelerateInterpolator())
            .start()
        animate()
            .alpha(0f)
            .setDuration(CLOSE_DURATION)
            .withEndAction { detach() }
            .start()
    }

    private fun detach() {
        mIsOpen = false
        launcher.dragLayer.removeView(this)
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_COMPOSE_VIEW != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    /** Tapping the scrim outside the panel dismisses the tray. */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN &&
            !launcher.dragLayer.isEventOverView(composeView, ev)
        ) {
            close(true)
        }
        return true
    }

    companion object {
        private const val OPEN_DURATION = 250L
        private const val CLOSE_DURATION = 200L

        fun show(launcher: LawnchairLauncher) {
            if (getOpenView<ShortcutTrayView>(launcher, TYPE_COMPOSE_VIEW) != null) return
            closeAllOpenViews(launcher)
            ShortcutTrayView(launcher).show()
        }
    }
}
