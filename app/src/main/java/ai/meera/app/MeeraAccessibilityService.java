package ai.meera.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class MeeraAccessibilityService extends AccessibilityService {

    public static MeeraAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    // ── Screen Read ──────────────────────────────────────────────────────────
    public static String readScreen() {
        if (instance == null) return "Accessibility service OFF hai";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "Screen read nahi ho sakی";
        StringBuilder sb = new StringBuilder();
        readNode(root, sb);
        return sb.length() > 0 ? sb.toString() : "Screen empty hai";
    }

    private static void readNode(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) sb.append(text).append(" | ");
        if (desc != null && desc.length() > 0) sb.append(desc).append(" | ");
        for (int i = 0; i < node.getChildCount(); i++) {
            readNode(node.getChild(i), sb);
        }
    }

    // ── Tap by Text ──────────────────────────────────────────────────────────
    public static String tapByText(String target) {
        if (instance == null) return "Service OFF";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "Root null";
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.get(0);
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return "Tapped: " + target;
        }
        return target + " nahi mila screen par";
    }

    // ── Tap by Coords ────────────────────────────────────────────────────────
    public static void tapAt(float x, float y) {
        if (instance == null) return;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        instance.dispatchGesture(builder.build(), null, null);
    }

    // ── Scroll ───────────────────────────────────────────────────────────────
    public static void scroll(String direction) {
        if (instance == null) return;
        float cx = 540, top = 400, bottom = 1600;
        Path path = new Path();
        if (direction.equals("down")) {
            path.moveTo(cx, bottom); path.lineTo(cx, top);
        } else if (direction.equals("up")) {
            path.moveTo(cx, top); path.lineTo(cx, bottom);
        } else if (direction.equals("left")) {
            path.moveTo(900, 800); path.lineTo(180, 800);
        } else {
            path.moveTo(180, 800); path.lineTo(900, 800);
        }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
        instance.dispatchGesture(builder.build(), null, null);
    }

    // ── Type Text ────────────────────────────────────────────────────────────
    public static String typeText(String text) {
        if (instance == null) return "Service OFF";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "Root null";
        AccessibilityNodeInfo focused = findFocused(root);
        if (focused != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            return "Typed: " + text;
        }
        return "Koi input field focus nahi hai";
    }

    private static AccessibilityNodeInfo findFocused(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isFocused()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findFocused(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    // ── Press Back ───────────────────────────────────────────────────────────
    public static void pressBack() {
        if (instance != null)
            instance.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    // ── Press Home ───────────────────────────────────────────────────────────
    public static void pressHome() {
        if (instance != null)
            instance.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    // ── Recent Apps ──────────────────────────────────────────────────────────
    public static void pressRecents() {
        if (instance != null)
            instance.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    // ── Notifications ────────────────────────────────────────────────────────
    public static void openNotifications() {
        if (instance != null)
            instance.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }
}
