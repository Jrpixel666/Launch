package app.pocketshell;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Searchable app grid. Edit mode shows hidden apps dimmed; tapping an app toggles whether it is
 * hidden. Enter in search launches the first match; no match means no launch.
 */
final class DrawerView extends LinearLayout {
    private final Host host;
    private final List<AppInfo> shown = new ArrayList<>();
    private EditText search;
    private TextView editButton;
    private TextView empty;
    private GridView grid;
    private BaseAdapter adapter;
    private boolean editMode;
    private String query = "";
    private int insetTop, insetBottom;

    DrawerView(Context c, Host host) {
        super(c);
        this.host = host;
        setOrientation(VERTICAL);
        rebuild();
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        setPadding(0, top, 0, bottom);
    }

    /** Called each time the drawer is opened. */
    void reset(boolean edit) {
        editMode = edit;
        query = "";
        if (search != null) search.setText("");
        updateEditButton();
        refresh();
        if (grid != null) grid.setSelection(0);
    }

    boolean handleBack() {
        if (editMode) {
            editMode = false;
            updateEditButton();
            refresh();
            return true;
        }
        if (!query.isEmpty()) {
            search.setText("");
            return true;
        }
        return false;
    }

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && search != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        if (search != null) search.clearFocus();
    }

    void rebuild() {
        removeAllViews();
        final Context c = getContext();
        final Theme t = host.theme();
        setBackgroundColor(Ui.withAlpha(t.surface, 0xF5));
        setPadding(0, insetTop, 0, insetBottom);

        // Grab handle: tap or swipe down to close.
        FrameLayoutHandle handle = new FrameLayoutHandle(c, t);
        Ui.dismissOnSwipe(handle, 1, new Runnable() {
            @Override
            public void run() {
                host.closeDrawer();
            }
        });
        addView(handle, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 28)));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 8));

        search = new EditText(c);
        search.setHint("Search apps");
        search.setHintTextColor(t.textDim);
        search.setTextColor(t.text);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(Ui.rounded(c, t.surfaceHigh, 16));
        search.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                refresh();
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enter = actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN);
                if (!enter) return false;
                // Use the live text, not the last filtered state.
                query = v.getText().toString().trim().toLowerCase(Locale.ROOT);
                refresh();
                if (!editMode && !shown.isEmpty()) {
                    hideKeyboard();
                    host.launch(shown.get(0), null);
                }
                return true;
            }
        });
        bar.addView(search, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        editButton = Ui.button(c, "Edit", t.surfaceHigh, t.outline, t.text);
        editButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                editMode = !editMode;
                updateEditButton();
                refresh();
            }
        });
        LayoutParams elp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 48));
        elp.leftMargin = Ui.dp(c, 8);
        bar.addView(editButton, elp);
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(c);
        grid.setNumColumns(host.prefs().drawerColumns());
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.dp(c, 6));
        grid.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 12));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppInfo a = shown.get(pos);
                if (editMode) {
                    AppMenu.toggleHidden(host, a.key);
                } else {
                    host.launch(a, v);
                }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (editMode) return false; // rows do not launch or open menus while editing
                AppMenu.show(host, v, shown.get(pos), true);
                return true;
            }
        });
        addView(grid, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = Ui.text(c, "No apps found", 15, t.textDim, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(c, 32), 0, 0);
        empty.setVisibility(GONE);
        addView(empty, 2, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        updateEditButton();
        refresh();
    }

    private void updateEditButton() {
        if (editButton == null) return;
        Theme t = host.theme();
        editButton.setText(editMode ? "Done" : "Edit");
        editButton.setBackground(editMode
                ? Ui.pressable(getContext(), t.accent, t.accent, 14)
                : Ui.pressable(getContext(), t.surfaceHigh, t.outline, 14));
        editButton.setTextColor(editMode ? t.onAccent : t.text);
    }

    /** Recomputes the visible list from the repository, hidden set, search text and edit mode. */
    void refresh() {
        Set<String> hidden = host.prefs().hidden();
        shown.clear();
        for (AppInfo a : host.apps().all()) {
            if (!editMode && hidden.contains(a.key)) continue;
            if (a.matches(query)) shown.add(a);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (empty != null) empty.setVisibility(shown.isEmpty() && host.apps().isLoaded() ? VISIBLE : GONE);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Context c = parent.getContext();
            Theme t = host.theme();
            AppCell cell = (AppCell) convert;
            if (cell == null) {
                cell = new AppCell(c, Ui.dp(c, host.prefs().iconDp()), t.text, false, true);
                cell.setBackground(Ui.pressable(c, 0x00000000, Ui.withAlpha(t.text, 0x22), 16));
                cell.setLayoutParams(new AbsListViewLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(c, host.prefs().iconDp() + 50)));
            }
            AppInfo a = shown.get(i);
            boolean hiddenApp = host.prefs().hidden().contains(a.key);
            cell.bind(a, editMode && hiddenApp);
            // Edit mode marks the state explicitly: hidden apps are dimmed and say so.
            cell.label.setText(editMode && hiddenApp ? a.label + "\n(hidden)" : a.label);
            return cell;
        }
    }

    /** Named subclass only to keep the constructor call short. */
    private static final class AbsListViewLayout extends android.widget.AbsListView.LayoutParams {
        AbsListViewLayout(int w, int h) {
            super(w, h);
        }
    }

    private static final class FrameLayoutHandle extends android.widget.FrameLayout {
        FrameLayoutHandle(Context c, Theme t) {
            super(c);
            View pill = new View(c);
            pill.setBackground(Ui.rounded(c, t.outline, 3));
            addView(pill, new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5), Gravity.CENTER));
            setContentDescription("Close drawer");
        }
    }
}
