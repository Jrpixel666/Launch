package app.pocketshell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/** Month grid. Tap the left/right of the title to change month, the middle to return to today. */
final class MonthView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private Calendar shown = Calendar.getInstance();
    private int text = 0xFFFFFFFF;
    private int dim = 0xFF999999;
    private int accent = 0xFF5B8CFF;
    private int onAccent = 0xFF000000;

    MonthView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setColors(int text, int dim, int accent, int onAccent) {
        this.text = text;
        this.dim = dim;
        this.accent = accent;
        this.onAccent = onAccent;
        invalidate();
    }

    void resetToToday() {
        shown = Calendar.getInstance();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        float colW = w / 7f;
        float rowH = h / 8f; // title + weekday header + up to six weeks
        float textSize = Math.min(rowH * 0.42f, 15f * density);

        // Title with month arrows.
        CharSequence title = DateFormat.format(
                DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLL yyyy"), shown);
        paint.setColor(text);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(textSize * 1.2f);
        float baseline = rowH / 2f - (paint.ascent() + paint.descent()) / 2f;
        cv.drawText(title, 0, title.length(), w / 2f, baseline, paint);
        paint.setColor(dim);
        cv.drawText("‹", colW / 2f, baseline, paint);
        cv.drawText("›", w - colW / 2f, baseline, paint);

        // Weekday header, starting on the locale's first day of week.
        Calendar now = Calendar.getInstance();
        int first = now.getFirstDayOfWeek();
        String[] names = DateFormatSymbols.getInstance().getShortWeekdays();
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(textSize * 0.85f);
        paint.setColor(dim);
        float hb = rowH * 1.5f - (paint.ascent() + paint.descent()) / 2f;
        for (int i = 0; i < 7; i++) {
            int d = ((first - 1 + i) % 7) + 1;
            String n = names[d];
            if (n.length() > 2) n = n.substring(0, 2);
            cv.drawText(n, colW * i + colW / 2f, hb, paint);
        }

        // Day grid.
        Calendar c = (Calendar) shown.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (c.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        boolean sameMonth = now.get(Calendar.YEAR) == shown.get(Calendar.YEAR)
                && now.get(Calendar.MONTH) == shown.get(Calendar.MONTH);
        int today = now.get(Calendar.DAY_OF_MONTH);
        paint.setTextSize(textSize);
        for (int day = 1; day <= days; day++) {
            int idx = offset + day - 1;
            float cx = colW * (idx % 7) + colW / 2f;
            float cy = rowH * (2 + idx / 7) + rowH / 2f;
            boolean isToday = sameMonth && day == today;
            if (isToday) {
                paint.setColor(accent);
                cv.drawCircle(cx, cy, Math.min(colW, rowH) * 0.44f, paint);
                paint.setColor(onAccent);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                paint.setColor(text);
                paint.setTypeface(Typeface.DEFAULT);
            }
            cv.drawText(String.valueOf(day), cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getY() < getHeight() / 8f) {
            float x = e.getX();
            if (x < getWidth() / 3f) shown.add(Calendar.MONTH, -1);
            else if (x > getWidth() * 2f / 3f) shown.add(Calendar.MONTH, 1);
            else shown = Calendar.getInstance();
            invalidate();
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
