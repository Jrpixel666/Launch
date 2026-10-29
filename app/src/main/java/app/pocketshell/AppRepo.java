package app.pocketshell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Loads the launchable app catalogue off the main thread and keeps it current. */
final class AppRepo {
    interface Listener {
        void onAppsChanged();

        void onPackageRemoved(String packageName);
    }

    private final Context ctx;
    private final PackageManager pm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private volatile List<AppInfo> apps = Collections.emptyList();
    private volatile Map<String, AppInfo> byKey = Collections.emptyMap();
    private Listener listener;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    && intent.getData() != null && listener != null) {
                listener.onPackageRemoved(intent.getData().getSchemeSpecificPart());
            }
            // Package events arrive in bursts (updates, component toggles); coalesce them.
            main.removeCallbacks(reloadTask);
            main.postDelayed(reloadTask, 400);
        }
    };

    private final Runnable reloadTask = new Runnable() {
        @Override
        public void run() {
            reload();
        }
    };

    AppRepo(Context c) {
        ctx = c.getApplicationContext();
        pm = ctx.getPackageManager();
    }

    void start(Listener l) {
        listener = l;
        if (!registered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addDataScheme("package");
            ctx.registerReceiver(receiver, f);
            registered = true;
        }
        reload();
    }

    void stop() {
        if (registered) {
            ctx.unregisterReceiver(receiver);
            registered = false;
        }
    }

    boolean isLoaded() {
        return !apps.isEmpty();
    }

    List<AppInfo> all() {
        return apps;
    }

    AppInfo get(String key) {
        return byKey.get(key);
    }

    void reload() {
        final int gen = generation.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
                final List<AppInfo> list = new ArrayList<>(found.size());
                for (ResolveInfo ri : found) {
                    if (ri.activityInfo == null) continue;
                    if (ri.activityInfo.packageName.equals(ctx.getPackageName())) continue;
                    CharSequence label = ri.loadLabel(pm);
                    list.add(new AppInfo(
                            label == null ? ri.activityInfo.packageName : label.toString(),
                            new android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                            ri.loadIcon(pm)));
                }
                final Collator collator = Collator.getInstance();
                Collections.sort(list, new Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo a, AppInfo b) {
                        return collator.compare(a.label, b.label);
                    }
                });
                final Map<String, AppInfo> map = new HashMap<>();
                for (AppInfo a : list) map.put(a.key, a);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation.get()) return; // a newer load is in flight
                        apps = list;
                        byKey = map;
                        if (listener != null) listener.onAppsChanged();
                    }
                });
            }
        }, "app-scan").start();
    }
}
