package app.pocketshell;

import java.util.ArrayList;
import java.util.List;

/** Home pages and dock membership. Persisted through {@link Prefs} after every change. */
final class HomeModel {
    static final int MAX_PAGES = 6;
    static final int MAX_DOCK = 4;
    static final int ROWS = 5;

    enum Result { OK, ALREADY, FULL, NONE }

    final List<List<String>> pages;
    final List<String> dock;
    private final Prefs prefs;
    private Runnable onChange;

    HomeModel(Prefs prefs) {
        this.prefs = prefs;
        this.pages = prefs.loadPages();
        this.dock = prefs.loadDock();
        normalize();
    }

    void setOnChange(Runnable r) {
        onChange = r;
    }

    int capacity() {
        return prefs.homeColumns() * ROWS;
    }

    private void changed() {
        prefs.savePages(pages);
        prefs.saveDock(dock);
        if (onChange != null) onChange.run();
    }

    boolean contains(String key) {
        if (dock.contains(key)) return true;
        for (List<String> p : pages) if (p.contains(key)) return true;
        return false;
    }

    boolean inDock(String key) {
        return dock.contains(key);
    }

    /** Spills overflowing pages forward, e.g. after the column count shrinks. No change callback. */
    void normalize() {
        int cap = capacity();
        for (int i = 0; i < pages.size(); i++) {
            List<String> p = pages.get(i);
            while (p.size() > cap) {
                String moved = p.remove(p.size() - 1);
                if (i + 1 >= pages.size()) {
                    if (pages.size() >= MAX_PAGES) break; // drop silently beyond the page limit
                    pages.add(new ArrayList<String>());
                }
                pages.get(i + 1).add(0, moved);
            }
        }
        if (pages.isEmpty()) pages.add(new ArrayList<String>());
        prefs.savePages(pages);
    }

    Result addToHome(String key) {
        if (contains(key)) return Result.ALREADY;
        for (List<String> p : pages) {
            if (p.size() < capacity()) {
                p.add(key);
                changed();
                return Result.OK;
            }
        }
        if (pages.size() < MAX_PAGES) {
            List<String> n = new ArrayList<>();
            n.add(key);
            pages.add(n);
            changed();
            return Result.OK;
        }
        return Result.FULL;
    }

    Result addToDock(String key) {
        if (contains(key)) return Result.ALREADY;
        if (dock.size() >= MAX_DOCK) return Result.FULL;
        dock.add(key);
        changed();
        return Result.OK;
    }

    void remove(String key) {
        boolean any = dock.remove(key);
        for (List<String> p : pages) any |= p.remove(key);
        if (any) changed();
    }

    /** Moves an item one slot earlier (-1) or later (+1) inside its own page or the dock. */
    void moveWithin(String key, int delta) {
        List<String> list = dock.contains(key) ? dock : null;
        if (list == null) {
            for (List<String> p : pages) if (p.contains(key)) list = p;
        }
        if (list == null) return;
        int i = list.indexOf(key);
        int j = i + delta;
        if (j < 0 || j >= list.size()) return;
        list.remove(i);
        list.add(j, key);
        changed();
    }

    Result moveToDock(String key) {
        if (dock.contains(key)) return Result.ALREADY;
        if (dock.size() >= MAX_DOCK) return Result.FULL;
        for (List<String> p : pages) p.remove(key);
        dock.add(key);
        changed();
        return Result.OK;
    }

    Result moveToHome(String key) {
        if (!dock.contains(key)) return Result.NONE;
        dock.remove(key);
        Result r = addToHome(key);
        if (r != Result.OK) {
            dock.add(key); // restore
            return r;
        }
        return Result.OK;
    }

    /** Moves an app to the previous (-1) or next (+1) page, creating a page when allowed. */
    Result moveToPage(String key, int delta) {
        int from = -1;
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).contains(key)) from = i;
        if (from < 0) return Result.NONE;
        int to = from + delta;
        if (to < 0) return Result.NONE;
        if (to >= pages.size()) {
            if (pages.size() >= MAX_PAGES) return Result.FULL;
            pages.add(new ArrayList<String>());
        }
        if (pages.get(to).size() >= capacity()) return Result.FULL;
        pages.get(from).remove(key);
        pages.get(to).add(key);
        changed();
        return Result.OK;
    }

    boolean addPage() {
        if (pages.size() >= MAX_PAGES) return false;
        pages.add(new ArrayList<String>());
        changed();
        return true;
    }

    boolean removeLastEmptyPage() {
        if (pages.size() <= 1) return false;
        if (!pages.get(pages.size() - 1).isEmpty()) return false;
        pages.remove(pages.size() - 1);
        changed();
        return true;
    }

    void removePackage(String pkg) {
        boolean any = false;
        String prefix = pkg + "/";
        any |= dock.removeIf(k -> k.startsWith(prefix));
        for (List<String> p : pages) any |= p.removeIf(k -> k.startsWith(prefix));
        if (any) changed();
    }

    void clear() {
        pages.clear();
        pages.add(new ArrayList<String>());
        dock.clear();
        changed();
    }
}
