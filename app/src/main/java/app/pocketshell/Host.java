package app.pocketshell;

import android.app.Activity;
import android.view.View;

/** What the views need from the activity. Keeps the views free of activity internals. */
interface Host {
    Activity activity();

    Theme theme();

    Prefs prefs();

    AppRepo apps();

    HomeModel home();

    void launch(AppInfo app, View from);

    void openDrawer(boolean editMode);

    void closeDrawer();

    void openQuickSettings();

    void closeQuickSettings();

    void openSettings();

    void closeSettings();

    void openAgent();

    void closeAgent();

    void closeOverlays();

    /** Called after any preference that affects appearance or layout changes. */
    void settingsChanged();

    void resetLayout();

    void toast(String message);
}
