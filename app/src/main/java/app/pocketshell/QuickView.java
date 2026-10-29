package app.pocketshell;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Quick Settings shade. Android does not let a launcher toggle radios directly, so each tile shows
 * current state and opens the matching system panel or settings screen.
 */
final class QuickView extends FrameLayout {
    private final Host host;
    private View scrim;
    private LinearLayout panel;
    private int insetTop, insetBottom;
    private boolean open;

    QuickView(Context c, Host host) {
        super(c);
        this.host = host;
        setVisibility(GONE);
    }

    boolean isOpen() {
        return open;
    }

    void setInsets(int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        if (panel != null) panel.setPadding(Ui.dp(getContext(), 16), top + Ui.dp(getContext(), 12),
                Ui.dp(getContext(), 16), Ui.dp(getContext(), 12));
    }

    void open() {
        open = true;
        rebuild();
        setVisibility(VISIBLE);
        scrim.setAlpha(0f);
        panel.setTranslationY(-Ui.dp(getContext(), 700));
        post(new Runnable() {
            @Override
            public void run() {
                panel.setTranslationY(-panel.getHeight());
                panel.animate().translationY(0).setDuration(220).start();
                scrim.animate().alpha(1f).setDuration(220).start();
            }
        });
    }

    void close() {
        if (!open) return;
        open = false;
        scrim.animate().alpha(0f).setDuration(180).start();
        panel.animate().translationY(-panel.getHeight()).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!open) setVisibility(GONE);
                    }
                }).start();
    }

    void rebuild() {
        removeAllViews();
        final Context c = getContext();
        final Theme t = host.theme();

        scrim = new View(c);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.closeQuickSettings();
            }
        });
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.withAlpha(t.surface, 0xF5));
        float r = Ui.dp(c, 28);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        panel.setBackground(bg);
        panel.setPadding(Ui.dp(c, 16), insetTop + Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        panel.setClickable(true); // taps on the panel must not fall through to the scrim

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(c, "Quick Settings", 20, t.text, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = Ui.button(c, "Launcher settings", t.surfaceHigh, t.outline, t.text);
        gear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSettings();
            }
        });
        header.addView(gear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 44)));
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = null;
        Object[][] tiles = tiles(c);
        for (int i = 0; i < tiles.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(c, 10);
                panel.addView(row, rlp);
            }
            final Intent intent = (Intent) tiles[i][2];
            View tile = tile(c, t, (String) tiles[i][0], (String) tiles[i][1], intent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = i % 2 == 0 ? 0 : Ui.dp(c, 5);
            lp.rightMargin = i % 2 == 0 ? Ui.dp(c, 5) : 0;
            row.addView(tile, lp);
        }

        View handle = new View(c);
        handle.setBackground(Ui.rounded(c, t.outline, 3));
        FrameLayout hw = new FrameLayout(c);
        hw.addView(handle, new LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5), Gravity.CENTER));
        hw.setContentDescription("Close quick settings");
        Ui.dismissOnSwipe(hw, -1, new Runnable() {
            @Override
            public void run() {
                host.closeQuickSettings();
            }
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 32));
        hlp.topMargin = Ui.dp(c, 8);
        panel.addView(hw, hlp);

        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
    }

    private View tile(Context c, Theme t, String name, String status, final Intent intent) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));
        tile.setMinimumHeight(Ui.dp(c, 72));
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(Ui.pressable(c, t.surfaceHigh, t.outline, 20));
        tile.setClickable(true);
        TextView n = Ui.text(c, name, 15, t.text, true);
        tile.addView(n);
        TextView s = Ui.text(c, status, 12, t.textDim, false);
        s.setMaxLines(1);
        tile.addView(s);
        tile.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.closeOverlays();
                if (intent == null) {
                    host.openSettings();
                } else {
                    AppMenu.start(host, intent);
                }
            }
        });
        return tile;
    }

    /** {title, status, intent}. */
    private Object[][] tiles(Context c) {
        Intent internet = Build.VERSION.SDK_INT >= 29
                ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                : new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        Intent volume = Build.VERSION.SDK_INT >= 29
                ? new Intent(Settings.Panel.ACTION_VOLUME)
                : new Intent(Settings.ACTION_SOUND_SETTINGS);
        return new Object[][]{
                {"Internet", networkStatus(c), internet},
                {"Bluetooth", bluetoothStatus(), new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)},
                {"Sound", volumeStatus(c), volume},
                {"Display", brightnessStatus(c), new Intent(Settings.ACTION_DISPLAY_SETTINGS)},
                {"Battery", batteryStatus(c), new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)},
                {"Location", "Open location settings", new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)},
                {"Accessibility", "Open accessibility settings", new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)},
                {"System settings", "Open Android settings", new Intent(Settings.ACTION_SETTINGS)},
        };
    }

    private static String networkStatus(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "Unavailable";
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null) return "Offline";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi connected";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile data";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return "Online";
    }

    @SuppressWarnings("deprecation")
    private static String bluetoothStatus() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return "Not available";
            return a.isEnabled() ? "On" : "Off";
        } catch (SecurityException e) {
            return "Open Bluetooth settings";
        }
    }

    private static String volumeStatus(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return "Unavailable";
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? "Unavailable"
                : String.format(Locale.getDefault(), "Media %d%%", Math.round(cur * 100f / max));
    }

    private static String brightnessStatus(Context c) {
        try {
            int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            return String.format(Locale.getDefault(), "Brightness %d%%", Math.round(v * 100f / 255f));
        } catch (Settings.SettingNotFoundException e) {
            return "Open display settings";
        }
    }

    private static String batteryStatus(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) return "Unavailable";
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level < 0 || scale <= 0) return "Unavailable";
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        return String.format(Locale.getDefault(), "%d%%%s", Math.round(level * 100f / scale),
                charging ? " · charging" : "");
    }
}
