package app.pocketshell;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home: optional widgets page (clock, status, calendar), app pages, page dots and the dock.
 * Swipe up opens the drawer; swipe down opens Quick Settings.
 */
final class HomeView extends FrameLayout {
    private final Host host;
    private final int slop;
    private PagerView pager;
    private LinearLayout dots;
    private LinearLayout column;
    private int insetTop, insetBottom;
    private float downX, downY;
    private boolean vertical, edgeSwipe;

    HomeView(Context c, Host host) {
        super(c);
        this.host = host;
        this.slop = ViewConfiguration.get(c).getScaledTouchSlop();
        rebuild();
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (column != null) column.setPadding(0, top, 0, bottom);
    }

    private int firstAppPage() {
        return host.prefs().showWidgets() ? 1 : 0;
    }

    void goHome(boolean animate) {
        if (pager != null) pager.setPage(firstAppPage(), animate);
    }

    void rebuild() {
        int keep = pager != null ? pager.getPage() : -1;
        removeAllViews();
        Context c = getContext();
        Theme t = host.theme();
        Prefs p = host.prefs();
        HomeModel model = host.home();

        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, insetTop, 0, insetBottom);

        pager = new PagerView(c);
        List<View> views = new ArrayList<>();
        if (p.showWidgets()) views.add(buildWidgetsPage(c, t));
        for (int i = 0; i < model.pages.size(); i++) {
            views.add(buildAppPage(c, model.pages.get(i), i == 0));
        }
        int initial = keep >= 0 ? Math.min(keep, views.size() - 1) : firstAppPage();
        pager.setPages(views, initial);
        column.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new LinearLayout(c);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < views.size(); i++) {
            View d = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 6), Ui.dp(c, 6));
            lp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            dots.addView(d, lp);
        }
        updateDots(pager.getPage());
        pager.setListener(new PagerView.Listener() {
            @Override
            public void onPage(int page) {
                updateDots(page);
            }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 18));
        column.addView(dots, dlp);

        column.addView(buildDock(c, t), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void updateDots(int page) {
        Context c = getContext();
        for (int i = 0; i < dots.getChildCount(); i++) {
            dots.getChildAt(i).setBackground(Ui.rounded(c,
                    i == page ? 0xFFFFFFFF : 0x66FFFFFF, 3));
        }
    }

    private View buildWidgetsPage(Context c, Theme t) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 8));
        page.addView(new GlanceView(c), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout card = new FrameLayout(c);
        card.setBackground(Ui.rounded(c, Ui.withAlpha(t.surface, 0xD8), 24));
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        MonthView month = new MonthView(c);
        month.setColors(t.text, t.textDim, t.accent, t.onAccent);
        card.addView(month, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.topMargin = Ui.dp(c, 20);
        clp.bottomMargin = Ui.dp(c, 8);
        page.addView(card, clp);
        page.setOnLongClickListener(homeMenuListener());
        return page;
    }

    private View buildAppPage(Context c, List<String> keys, boolean firstPage) {
        Prefs p = host.prefs();
        FrameLayout page = new FrameLayout(c);
        AppGrid grid = new AppGrid(c, p.homeColumns());
        grid.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        int iconPx = Ui.dp(c, p.iconDp());
        int shown = 0;
        for (String key : keys) {
            final AppInfo a = host.apps().get(key);
            if (a == null) continue;
            final AppCell cell = new AppCell(c, iconPx, Color.WHITE, true, true);
            cell.bind(a, false);
            cell.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.launch(a, cell);
                }
            });
            cell.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AppMenu.show(host, v, a, false);
                    return true;
                }
            });
            grid.addView(cell);
            shown++;
        }
        grid.setOnLongClickListener(homeMenuListener());
        page.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (shown == 0 && firstPage) {
            TextView hint = Ui.text(c, "Swipe up for all apps.\nLong-press an app to add it here.",
                    14, 0xCCFFFFFF, false);
            hint.setGravity(Gravity.CENTER);
            Ui.shadow(hint);
            page.addView(hint, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }
        return page;
    }

    private View buildDock(Context c, Theme t) {
        LinearLayout dock = new LinearLayout(c);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(Ui.rounded(c, Ui.withAlpha(t.surface, 0x99), 28));
        dock.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));

        List<AppInfo> items = new ArrayList<>();
        for (String key : host.home().dock) {
            AppInfo a = host.apps().get(key);
            if (a != null) items.add(a);
        }
        int split = (items.size() + 1) / 2;
        int cellPx = Ui.dp(c, 64);
        int iconPx = Math.min(Ui.dp(c, host.prefs().iconDp()), Ui.dp(c, 52));
        for (int i = 0; i <= items.size(); i++) {
            if (i == split) dock.addView(buildAppsButton(c), new LinearLayout.LayoutParams(cellPx, cellPx));
            if (i == items.size()) break;
            final AppInfo a = items.get(i);
            final AppCell cell = new AppCell(c, iconPx, Color.WHITE, false, false);
            cell.bind(a, false);
            cell.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.launch(a, cell);
                }
            });
            cell.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AppMenu.show(host, v, a, false);
                    return true;
                }
            });
            dock.addView(cell, new LinearLayout.LayoutParams(cellPx, cellPx));
        }
        FrameLayout wrap = new FrameLayout(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        lp.setMargins(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 10));
        wrap.addView(dock, lp);
        return wrap;
    }

    private View buildAppsButton(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.pressable(c, 0x00000000, 0x33FFFFFF, 16));
        f.setContentDescription("All apps");
        f.setClickable(true);
        f.addView(new AppsGlyph(c, host.theme().accent), new FrameLayout.LayoutParams(
                Ui.dp(c, 28), Ui.dp(c, 28), Gravity.CENTER));
        f.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openDrawer(false);
            }
        });
        return f;
    }

    private OnLongClickListener homeMenuListener() {
        return new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showHomeMenu(v);
                return true;
            }
        };
    }

    private void showHomeMenu(View anchor) {
        PopupMenu menu = new PopupMenu(host.activity(), anchor, Gravity.CENTER);
        menu.getMenu().add(0, 1, 0, "Wallpaper");
        menu.getMenu().add(0, 2, 1, "Add page");
        menu.getMenu().add(0, 3, 2, "Remove empty last page");
        menu.getMenu().add(0, 4, 3, "Launcher settings");
        menu.getMenu().add(0, 5, 4, "Agent (or swipe in from the left edge)");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        AppMenu.start(host, Intent.createChooser(
                                new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
                        return true;
                    case 2:
                        if (!host.home().addPage()) host.toast("Page limit reached");
                        return true;
                    case 3:
                        if (!host.home().removeLastEmptyPage()) host.toast("Last page isn't empty");
                        return true;
                    case 4:
                        host.openSettings();
                        return true;
                    case 5:
                        host.openAgent();
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    // ---- vertical swipe gestures ----

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                vertical = false;
                edgeSwipe = downX < Ui.dp(getContext(), 24);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (edgeSwipe && dx > slop * 2 && dx > Math.abs(dy) * 1.2f) {
                    return true;
                }
                if (!vertical && Math.abs(dy) > slop * 2 && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                    vertical = true;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_UP:
                if (vertical) {
                    float dy = ev.getY() - downY;
                    float min = Ui.dp(getContext(), 56);
                    if (dy < -min) host.openDrawer(false);
                    else if (dy > min) host.openQuickSettings();
                } else if (edgeSwipe) {
                    float dx = ev.getX() - downX;
                    if (dx > Ui.dp(getContext(), 56)) host.openAgent();
                }
                vertical = false;
                edgeSwipe = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                vertical = false;
                edgeSwipe = false;
                return true;
            default:
                return true;
        }
    }

    /** 3x3 grid of dots, the middle one in the accent colour. */
    private static final class AppsGlyph extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;

        AppsGlyph(Context c, int accent) {
            super(c);
            this.accent = accent;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth();
            float step = w / 3f;
            for (int r = 0; r < 3; r++) {
                for (int col = 0; col < 3; col++) {
                    boolean mid = r == 1 && col == 1;
                    paint.setColor(mid ? accent : 0xFFFFFFFF);
                    cv.drawCircle(step * (col + 0.5f), step * (r + 0.5f), mid ? step * 0.3f : step * 0.22f, paint);
                }
            }
        }
    }
}
