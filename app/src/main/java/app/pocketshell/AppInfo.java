package app.pocketshell;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

import java.util.Locale;

/** One launchable activity. */
final class AppInfo {
    final String label;
    final String lowerLabel;
    final ComponentName component;
    final String key;
    final Drawable icon;

    AppInfo(String label, ComponentName component, Drawable icon) {
        this.label = label;
        this.lowerLabel = label.toLowerCase(Locale.ROOT);
        this.component = component;
        this.key = component.flattenToString();
        this.icon = icon;
    }

    String packageName() {
        return component.getPackageName();
    }

    /** A fresh Drawable per view: sharing one instance between ImageViews corrupts its bounds. */
    Drawable newIcon() {
        Drawable.ConstantState cs = icon.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : icon;
    }

    boolean matches(String lowerQuery) {
        return lowerQuery.isEmpty()
                || lowerLabel.contains(lowerQuery)
                || component.getPackageName().toLowerCase(Locale.ROOT).contains(lowerQuery);
    }
}
