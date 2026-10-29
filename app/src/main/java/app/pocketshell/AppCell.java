package app.pocketshell;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Icon plus optional two-line label. Shrinks its icon when the cell is too short to fit both. */
final class AppCell extends LinearLayout {
    final ImageView icon;
    final TextView label;
    private final int iconPx;
    private final boolean showLabel;

    AppCell(Context c, int iconPx, int textColor, boolean shadow, boolean showLabel) {
        super(c);
        this.iconPx = iconPx;
        this.showLabel = showLabel;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 4));
        setBackground(Ui.pressable(c, 0x00000000, 0x33FFFFFF, 16));

        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(iconPx, iconPx));

        label = new TextView(c);
        label.setTextSize(11.5f);
        label.setTextColor(textColor);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        if (shadow) Ui.shadow(label);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 4);
        addView(label, lp);
        label.setVisibility(showLabel ? VISIBLE : GONE);
    }

    void bind(AppInfo a, boolean dim) {
        icon.setImageDrawable(a.newIcon());
        label.setText(a.label);
        setAlpha(dim ? 0.4f : 1f);
        setContentDescription(a.label);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            Context c = getContext();
            int avail = MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom()
                    - (showLabel ? Ui.dp(c, 36) : 0);
            int s = Math.max(Ui.dp(c, 28), Math.min(iconPx, avail));
            LayoutParams lp = (LayoutParams) icon.getLayoutParams();
            if (lp.width != s) {
                lp.width = s;
                lp.height = s;
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
