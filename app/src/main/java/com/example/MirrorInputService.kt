package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * Lets Desktop Mode control the phone while its screen is mirrored: taps, long presses, swipes and
 * scrolling as touch gestures; Back/Home/Recents; typing into the selected text field; and the arrow
 * keys/Enter move the selection and press the selected item (D-pad style). Switched on by the user
 * in Android's Accessibility settings; it does nothing unless the browser sends a command.
 */
class MirrorInputService : AccessibilityService() {
    companion object { @Volatile var instance: MirrorInputService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun handle(j: JSONObject): Boolean {
        val sw = ScreenMirror.width.coerceAtLeast(1).toFloat(); val sh = ScreenMirror.height.coerceAtLeast(1).toFloat()
        fun px(k: String) = (j.optDouble(k, 0.0).toFloat()).coerceIn(0f, 1f) * sw
        fun py(k: String) = (j.optDouble(k, 0.0).toFloat()).coerceIn(0f, 1f) * sh
        return when (j.optString("type")) {
            "tap" -> gesture(listOf(px("x") to py("y")), if (j.optBoolean("long")) 650L else 40L)
            "swipe" -> {
                val pts = j.optJSONArray("points")
                val list = if (pts != null) (0 until pts.length()).map { val p = pts.getJSONArray(it); (p.getDouble(0).toFloat().coerceIn(0f, 1f) * sw) to (p.getDouble(1).toFloat().coerceIn(0f, 1f) * sh) }
                    else listOf(px("x1") to py("y1"), px("x2") to py("y2"))
                gesture(list, j.optLong("ms", 250).coerceIn(60, 3000))
            }
            "scroll" -> {
                val x = px("x"); val y = py("y"); val d = (if (j.optDouble("dy") > 0) -1 else 1) * sh * 0.22f
                gesture(listOf(x to y, x to (y + d).coerceIn(1f, sh - 1)), 160)
            }
            "global" -> performGlobalAction(when (j.optString("action")) {
                "home" -> GLOBAL_ACTION_HOME; "recents" -> GLOBAL_ACTION_RECENTS
                "notifications" -> GLOBAL_ACTION_NOTIFICATIONS; "quick" -> GLOBAL_ACTION_QUICK_SETTINGS
                else -> GLOBAL_ACTION_BACK
            })
            "text" -> typeText(j.optString("text"))
            "key" -> key(j.optString("key"))
            else -> false
        }
    }

    private fun gesture(points: List<Pair<Float, Float>>, ms: Long): Boolean {
        if (points.isEmpty()) return false
        val p = Path().apply { moveTo(points[0].first, points[0].second); points.drop(1).forEach { lineTo(it.first, it.second) } }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    private fun focused(): AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    private fun typeText(t: String): Boolean {
        val n = focused() ?: return false
        if (!n.isEditable) return false
        val cur = if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) "" else n.text?.toString() ?: ""
        val start = n.textSelectionStart.takeIf { it in 0..cur.length } ?: cur.length
        val end = n.textSelectionEnd.takeIf { it in start..cur.length } ?: start
        return setText(n, cur.substring(0, start) + t + cur.substring(end), start + t.length)
    }

    private fun setText(n: AccessibilityNodeInfo, text: String, cursor: Int): Boolean {
        val ok = n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
        return ok
    }

    private fun key(k: String): Boolean {
        val n = focused()
        if (n != null && n.isEditable) {
            val cur = if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText) "" else n.text?.toString() ?: ""
            val s = n.textSelectionStart.takeIf { it in 0..cur.length } ?: cur.length
            val e = n.textSelectionEnd.takeIf { it in s..cur.length } ?: s
            when (k) {
                "backspace" -> return if (e > s) setText(n, cur.removeRange(s, e), s) else if (s > 0) setText(n, cur.removeRange(s - 1, s), s - 1) else true
                "delete" -> return if (e > s) setText(n, cur.removeRange(s, e), s) else if (s < cur.length) setText(n, cur.removeRange(s, s + 1), s) else true
                "enter" -> if (Build.VERSION.SDK_INT >= 30) return n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                "left" -> if (s > 0) { n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s - 1); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, s - 1) }); return true }
                "right" -> if (e < cur.length) { n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply { putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, e + 1); putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, e + 1) }); return true }
            }
        }
        val dir = when (k) { "up" -> View.FOCUS_UP; "down" -> View.FOCUS_DOWN; "left" -> View.FOCUS_LEFT; "right" -> View.FOCUS_RIGHT; else -> 0 }
        if (dir != 0) {
            val next = n?.focusSearch(dir) ?: firstFocusable(rootInActiveWindow)
            return next?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
        }
        if (k == "enter") {
            var c = n
            while (c != null && !c.isClickable) c = c.parent
            return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
        return false
    }

    private fun firstFocusable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val q = ArrayDeque<AccessibilityNodeInfo>(); q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            if (n.isFocusable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }
}
