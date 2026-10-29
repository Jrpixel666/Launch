package app.pocketshell;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Fixed grid of {@code cols x HomeModel.ROWS} equal cells, filled in child order. */
final class AppGrid extends ViewGroup {
    private final int cols;

    AppGrid(Context c, int cols) {
        super(c);
        this.cols = cols;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        int cw = (w - getPaddingLeft() - getPaddingRight()) / cols;
        int ch = (h - getPaddingTop() - getPaddingBottom()) / HomeModel.ROWS;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(Math.max(cw, 0), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(ch, 0), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cw = (r - l - getPaddingLeft() - getPaddingRight()) / cols;
        int ch = (b - t - getPaddingTop() - getPaddingBottom()) / HomeModel.ROWS;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int x = getPaddingLeft() + (i % cols) * cw;
            int y = getPaddingTop() + (i / cols) * ch;
            v.layout(x, y, x + cw, y + ch);
        }
    }
}
