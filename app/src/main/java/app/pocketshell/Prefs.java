package app.pocketshell;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All user configuration. Values are clamped on read so stale data can never break layout. */
final class Prefs {
    static final int MODE_DARK = 0;
    static final int MODE_LIGHT = 1;
    static final int MODE_SYSTEM = 2;

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("pocketshell", Context.MODE_PRIVATE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    int themeMode() { return clamp(sp.getInt("theme", MODE_SYSTEM), 0, 2); }
    void setThemeMode(int v) { sp.edit().putInt("theme", v).apply(); }

    int accent() { return clamp(sp.getInt("accent", 0), 0, Theme.ACCENTS.length - 1); }
    void setAccent(int v) { sp.edit().putInt("accent", v).apply(); }

    int style() { return clamp(sp.getInt("style", Theme.STYLE_ROUNDED), 0, 1); }
    void setStyle(int v) { sp.edit().putInt("style", clamp(v, 0, 1)).apply(); }

    int drawerColumns() { return clamp(sp.getInt("drawerCols", 4), 3, 6); }
    void setDrawerColumns(int v) { sp.edit().putInt("drawerCols", clamp(v, 3, 6)).apply(); }

    int homeColumns() { return clamp(sp.getInt("homeCols", 4), 4, 5); }
    void setHomeColumns(int v) { sp.edit().putInt("homeCols", clamp(v, 4, 5)).apply(); }

    int iconDp() { return clamp(sp.getInt("iconDp", 56), 44, 72); }
    void setIconDp(int v) { sp.edit().putInt("iconDp", clamp(v, 44, 72)).apply(); }

    boolean showWidgets() { return sp.getBoolean("widgets", true); }
    void setShowWidgets(boolean v) { sp.edit().putBoolean("widgets", v).apply(); }

    boolean seeded() { return sp.getBoolean("seeded", false); }
    void setSeeded(boolean v) { sp.edit().putBoolean("seeded", v).apply(); }

    Set<String> hidden() {
        Set<String> s = sp.getStringSet("hidden", Collections.<String>emptySet());
        return new HashSet<>(s == null ? Collections.<String>emptySet() : s);
    }

    void setHidden(Set<String> s) { sp.edit().putStringSet("hidden", new HashSet<>(s)).apply(); }

    List<List<String>> loadPages() {
        List<List<String>> out = new ArrayList<>();
        try {
            JSONArray pages = new JSONArray(sp.getString("pages", "[]"));
            for (int i = 0; i < pages.length(); i++) {
                JSONArray p = pages.getJSONArray(i);
                List<String> keys = new ArrayList<>();
                for (int j = 0; j < p.length(); j++) keys.add(p.getString(j));
                out.add(keys);
            }
        } catch (JSONException ignored) {
            out.clear();
        }
        if (out.isEmpty()) out.add(new ArrayList<String>());
        return out;
    }

    void savePages(List<List<String>> pages) {
        JSONArray all = new JSONArray();
        for (List<String> p : pages) {
            JSONArray a = new JSONArray();
            for (String k : p) a.put(k);
            all.put(a);
        }
        sp.edit().putString("pages", all.toString()).apply();
    }

    List<String> loadDock() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("dock", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
            out.clear();
        }
        return out;
    }

    void saveDock(List<String> dock) {
        JSONArray a = new JSONArray();
        for (String k : dock) a.put(k);
        sp.edit().putString("dock", a.toString()).apply();
    }

    // ---- agent: app usage counts, so the command bar can rank "smart" suggestions ----

    /** Call whenever an app is launched. Builds the ranking the Agent bar surfaces. */
    void recordLaunch(String key) {
        try {
            JSONObject o = new JSONObject(sp.getString("usage", "{}"));
            o.put(key, o.optInt(key, 0) + 1);
            sp.edit().putString("usage", o.toString()).apply();
        } catch (JSONException ignored) {
            // Corrupt usage data is not worth failing a launch over.
        }
    }

    /** The {@code limit} most-launched app keys, most-used first. */
    List<String> topUsed(int limit) {
        final Map<String, Integer> counts = new HashMap<>();
        try {
            JSONObject o = new JSONObject(sp.getString("usage", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                counts.put(k, o.optInt(k, 0));
            }
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return counts.get(b) - counts.get(a);
            }
        });
        return keys.subList(0, Math.min(limit, keys.size()));
    }
}
