package app.pocketshell;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import java.util.Set;

/** Long-press menu shared by Home, the dock and the drawer. */
final class AppMenu {
    private static final int ADD_HOME = 1, ADD_DOCK = 2, REMOVE = 3, HIDE = 4, TO_DOCK = 5,
            TO_HOME = 6, EARLIER = 7, LATER = 8, PREV_PAGE = 9, NEXT_PAGE = 10, INFO = 11,
            UNINSTALL = 12;

    private AppMenu() {}

    static void show(final Host host, View anchor, final AppInfo app, boolean fromDrawer) {
        final HomeModel model = host.home();
        boolean onHome = model.contains(app.key);
        boolean inDock = model.inDock(app.key);
        boolean hidden = host.prefs().hidden().contains(app.key);

        PopupMenu menu = new PopupMenu(host.activity(), anchor);
        Menu m = menu.getMenu();
        if (fromDrawer) {
            if (!onHome) {
                m.add(0, ADD_HOME, 0, "Add to Home");
                m.add(0, ADD_DOCK, 1, "Add to dock");
            } else {
                m.add(0, REMOVE, 0, "Remove from Home");
            }
            m.add(0, HIDE, 2, hidden ? "Show in drawer" : "Hide from drawer");
        } else {
            m.add(0, REMOVE, 0, "Remove from Home");
            if (inDock) {
                m.add(0, TO_HOME, 1, "Move to Home");
            } else {
                m.add(0, TO_DOCK, 1, "Move to dock");
                m.add(0, PREV_PAGE, 4, "Move to previous page");
                m.add(0, NEXT_PAGE, 5, "Move to next page");
            }
            m.add(0, EARLIER, 2, "Move earlier");
            m.add(0, LATER, 3, "Move later");
        }
        m.add(0, INFO, 8, "App info");
        m.add(0, UNINSTALL, 9, "Uninstall");

        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case ADD_HOME: report(host, model.addToHome(app.key), "Added to Home"); break;
                    case ADD_DOCK: report(host, model.addToDock(app.key), "Added to dock"); break;
                    case REMOVE: model.remove(app.key); break;
                    case TO_DOCK: report(host, model.moveToDock(app.key), null); break;
                    case TO_HOME: report(host, model.moveToHome(app.key), null); break;
                    case EARLIER: model.moveWithin(app.key, -1); break;
                    case LATER: model.moveWithin(app.key, 1); break;
                    case PREV_PAGE: report(host, model.moveToPage(app.key, -1), null); break;
                    case NEXT_PAGE: report(host, model.moveToPage(app.key, 1), null); break;
                    case HIDE: toggleHidden(host, app.key); break;
                    case INFO:
                        start(host, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.packageName(), null)));
                        break;
                    case UNINSTALL:
                        start(host, new Intent(Intent.ACTION_DELETE,
                                Uri.fromParts("package", app.packageName(), null)));
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
        menu.show();
    }

    private static void report(Host host, HomeModel.Result r, String ok) {
        switch (r) {
            case OK: if (ok != null) host.toast(ok); break;
            case ALREADY: host.toast("Already on Home"); break;
            case FULL: host.toast("No room there"); break;
            default: break;
        }
    }

    static void toggleHidden(Host host, String key) {
        Set<String> hidden = host.prefs().hidden();
        if (!hidden.remove(key)) hidden.add(key);
        host.prefs().setHidden(hidden);
        host.settingsChanged();
    }

    static void start(Host host, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host.activity().startActivity(i);
        } catch (Exception e) {
            host.toast("Can't open that on this device");
        }
    }
}
