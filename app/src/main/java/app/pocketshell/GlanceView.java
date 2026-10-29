package app.pocketshell;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;

/** Clock, date and a row of live status chips: battery, free RAM and connectivity. */
final class GlanceView extends LinearLayout {
    private final TextView battery;
    private final TextView ram;
    private final TextView network;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            postDelayed(this, 5000);
        }
    };

    GlanceView(Context c) {
        super(c);
        setOrientation(VERTICAL);

        TextClock time = new TextClock(c);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(60);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        Ui.shadow(time);
        addView(time, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(c);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(16);
        date.setTextColor(0xFFFFFFFF);
        Ui.shadow(date);
        addView(date, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(HORIZONTAL);
        chips.setGravity(Gravity.START);
        battery = chip(c);
        ram = chip(c);
        network = chip(c);
        chips.addView(battery);
        chips.addView(ram);
        chips.addView(network);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 14);
        addView(chips, lp);
        refresh();
    }

    private static TextView chip(Context c) {
        TextView t = Ui.text(c, "", 12, Color.WHITE, false);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        t.setBackground(Ui.rounded(c, 0x66000000, 16));
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        Context c = getContext();

        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (level >= 0 && scale > 0) {
                battery.setText(String.format(Locale.getDefault(), "%d%%%s",
                        Math.round(level * 100f / scale), charging ? " · charging" : ""));
            } else {
                battery.setText("Battery unavailable");
            }
        } else {
            battery.setText("Battery unavailable");
        }

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            ram.setText(String.format(Locale.getDefault(), "RAM %.1f GB free",
                    mi.availMem / (1024f * 1024f * 1024f)));
        } else {
            ram.setText("RAM unavailable");
        }

        network.setText(networkLabel(c));
    }

    private static String networkLabel(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "Network unavailable";
        Network n = cm.getActiveNetwork();
        if (n == null) return "Offline";
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        if (caps == null) return "Offline";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile data";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return "Online";
    }
}
