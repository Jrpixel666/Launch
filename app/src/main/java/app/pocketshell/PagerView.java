package app.pocketshell;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.List;

/** Horizontal pager with snapping, built on HorizontalScrollView so no AndroidX is needed. */
final class PagerView extends HorizontalScrollView {
    interface Listener {
        void onPage(int page);
    }

    private final LinearLayout strip;
    private int page;
    private int count;
    private boolean flung;
    private Listener listener;

    PagerView(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        addView(strip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setListener(Listener l) {
        listener = l;
    }

    int getPage() {
        return page;
    }

    int getCount() {
        return count;
    }

    void setPages(List<View> views, int initial) {
        strip.removeAllViews();
        count = views.size();
        for (View v : views) {
            strip.addView(v, new LinearLayout.LayoutParams(
                    Math.max(getWidth(), 1), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        page = Math.max(0, Math.min(initial, count - 1));
        post(new Runnable() {
            @Override
            public void run() {
                scrollTo(page * getWidth(), 0);
            }
        });
    }

    void setPage(int p, boolean animate) {
        int target = Math.max(0, Math.min(p, count - 1));
        if (animate) smoothScrollTo(target * getWidth(), 0);
        else scrollTo(target * getWidth(), 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        for (int i = 0; i < strip.getChildCount(); i++) {
            strip.getChildAt(i).getLayoutParams().width = w;
        }
        strip.requestLayout();
        scrollTo(page * w, 0);
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        int w = getWidth();
        if (w > 0) {
            int p = Math.round(l / (float) w);
            if (p != page) {
                page = Math.max(0, Math.min(p, count - 1));
                if (listener != null) listener.onPage(page);
            }
        }
    }

    /** One swipe moves one page, whatever the fling speed. */
    @Override
    public void fling(int velocityX) {
        int w = getWidth();
        if (w == 0) return;
        flung = true;
        float pos = getScrollX() / (float) w;
        int target = velocityX > 0 ? (int) Math.floor(pos) + 1 : (int) Math.ceil(pos) - 1;
        target = Math.max(0, Math.min(target, count - 1));
        smoothScrollTo(target * w, 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) flung = false;
        boolean handled = super.onTouchEvent(ev);
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !flung) {
            int w = getWidth();
            if (w > 0) {
                int target = Math.max(0, Math.min(Math.round(getScrollX() / (float) w), count - 1));
                smoothScrollTo(target * w, 0);
            }
        }
        return handled;
    }
}
