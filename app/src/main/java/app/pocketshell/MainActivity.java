package app.pocketshell;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * The launcher activity. It owns the four layers (Home, drawer, quick settings, settings) and the
 * shared state, and exposes both to the views through {@link Host}.
 */
public final class MainActivity extends Activity implements Host {
    private Prefs prefs;
    private AppRepo repo;
    private HomeModel model;
    private Theme theme;

    private FrameLayout root;
    private HomeView home;
    private DrawerView drawer;
    private QuickView quick;
    private SettingsView settings;
    private AgentView agent;

    private int insetTop, insetBottom;
    private boolean drawerOpen, settingsOpen, agentOpen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        repo = new AppRepo(this);
        model = new HomeModel(prefs);
        theme = Theme.from(this, prefs);

        root = new FrameLayout(this);
        home = new HomeView(this, this);
        drawer = new DrawerView(this, this);
        quick = new QuickView(this, this);
        settings = new SettingsView(this, this);
        agent = new AgentView(this, this);
        drawer.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);
        ViewGroup.LayoutParams match = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(home, match);
        root.addView(drawer, new FrameLayout.LayoutParams(match));
        root.addView(quick, new FrameLayout.LayoutParams(match));
        root.addView(settings, new FrameLayout.LayoutParams(match));
        root.addView(agent, new FrameLayout.LayoutParams(match));
        setContentView(root);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                insetTop = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
                applyInsets();
                return insets;
            }
        });

        model.setOnChange(new Runnable() {
            @Override
            public void run() {
                home.rebuild();
            }
        });
        updateSystemBars();
        root.requestApplyInsets();

        repo.start(new AppRepo.Listener() {
            @Override
            public void onAppsChanged() {
                seedDefaultsIfNeeded();
                home.rebuild();
                drawer.refresh();
            }

            @Override
            public void onPackageRemoved(String packageName) {
                model.removePackage(packageName);
            }
        });
    }

    @Override
    protected void onDestroy() {
        repo.stop();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button while already on Home: dismiss overlays and return to the first page.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            closeOverlays();
            home.goHome(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (prefs.themeMode() == Prefs.MODE_SYSTEM) settingsChanged();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (agentOpen) {
            closeAgent();
        } else if (settingsOpen) {
            closeSettings();
        } else if (quick.isOpen()) {
            closeQuickSettings();
        } else if (drawerOpen) {
            if (!drawer.handleBack()) closeDrawer();
        } else {
            home.goHome(true);
        }
    }

    // ---- insets and system bars ----

    private void applyInsets() {
        home.setInsets(insetTop, insetBottom);
        drawer.setInsets(insetTop, insetBottom);
        quick.setInsets(insetTop, insetBottom);
        settings.setInsets(insetTop, insetBottom);
        agent.setInsets(insetTop, insetBottom);
    }

    @SuppressWarnings("deprecation")
    private void updateSystemBars() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        // Dark bar icons only while an opaque light surface is under them.
        boolean lightSurface = !theme.dark && (drawerOpen || settingsOpen || quick.isOpen() || agentOpen);
        if (lightSurface) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
    }

    // ---- first run ----

    /** Puts the default phone, browser, messages and camera apps in the dock, once. */
    private void seedDefaultsIfNeeded() {
        if (prefs.seeded() || !repo.isLoaded()) return;
        prefs.setSeeded(true);
        Intent[] probes = {
                new Intent(Intent.ACTION_DIAL),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        };
        PackageManager pm = getPackageManager();
        for (Intent probe : probes) {
            ResolveInfo ri = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals("android")) continue; // a chooser, so there is no default
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            ComponentName cn = launch == null ? null : launch.getComponent();
            if (cn != null && repo.get(cn.flattenToString()) != null) {
                model.addToDock(cn.flattenToString());
            }
        }
    }

    // ---- Host ----

    @Override public Activity activity() { return this; }
    @Override public Theme theme() { return theme; }
    @Override public Prefs prefs() { return prefs; }
    @Override public AppRepo apps() { return repo; }
    @Override public HomeModel home() { return model; }

    @Override
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void launch(AppInfo app, View from) {
        prefs.recordLaunch(app.key);
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            if (from != null && from.getWidth() > 0) {
                startActivity(i, ActivityOptions.makeScaleUpAnimation(
                        from, 0, 0, from.getWidth(), from.getHeight()).toBundle());
            } else {
                startActivity(i);
            }
        } catch (Exception e) {
            toast("Couldn't open " + app.label);
            return;
        }
        closeOverlays();
    }

    @Override
    public void openDrawer(boolean editMode) {
        closeQuickSettings();
        drawer.reset(editMode);
        drawerOpen = true;
        slideIn(drawer, true);
        updateSystemBars();
    }

    @Override
    public void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.hideKeyboard();
        slideOut(drawer, true, new Runnable() {
            @Override
            public void run() {
                if (!drawerOpen) drawer.setVisibility(View.GONE);
            }
        });
        updateSystemBars();
    }

    @Override
    public void openQuickSettings() {
        quick.open();
        updateSystemBars();
    }

    @Override
    public void closeQuickSettings() {
        quick.close();
        updateSystemBars();
    }

    @Override
    public void openSettings() {
        quick.close();
        settingsOpen = true;
        slideIn(settings, false);
        updateSystemBars();
    }

    @Override
    public void closeSettings() {
        if (!settingsOpen) return;
        settingsOpen = false;
        slideOut(settings, false, new Runnable() {
            @Override
            public void run() {
                if (!settingsOpen) settings.setVisibility(View.GONE);
            }
        });
        updateSystemBars();
    }

    @Override
    public void openAgent() {
        closeQuickSettings();
        agent.reset();
        agentOpen = true;
        slideIn(agent, false);
        updateSystemBars();
    }

    @Override
    public void closeAgent() {
        if (!agentOpen) return;
        agentOpen = false;
        agent.hideKeyboard();
        slideOut(agent, false, new Runnable() {
            @Override
            public void run() {
                if (!agentOpen) agent.setVisibility(View.GONE);
            }
        });
        updateSystemBars();
    }

    @Override
    public void closeOverlays() {
        closeSettings();
        closeAgent();
        closeQuickSettings();
        closeDrawer();
    }

    @Override
    public void settingsChanged() {
        theme = Theme.from(this, prefs);
        home.rebuild();
        drawer.rebuild();
        quick.rebuild();
        settings.rebuild();
        applyInsets();
        updateSystemBars();
    }

    @Override
    public void resetLayout() {
        model.clear();
        prefs.setSeeded(false);
        seedDefaultsIfNeeded();
        home.goHome(false);
        toast("Home layout reset");
    }

    // ---- animation ----

    private void slideIn(View v, boolean fromBottom) {
        v.setVisibility(View.VISIBLE);
        v.setTranslationX(fromBottom ? 0 : root.getWidth());
        v.setTranslationY(fromBottom ? root.getHeight() : 0);
        v.animate().translationX(0).translationY(0).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();
    }

    private void slideOut(View v, boolean toBottom, Runnable end) {
        v.animate().translationX(toBottom ? 0 : root.getWidth())
                .translationY(toBottom ? root.getHeight() : 0).setDuration(200)
                .setInterpolator(new AccelerateInterpolator()).withEndAction(end).start();
    }
}
