package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView dnsChoice;
    private android.widget.CheckBox fadeEnabledCheck;
    private TextView fadeDelayChoice;
    private TextView fadeDurationChoice;
    private TextView screenOffChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        dnsChoice = findViewById(R.id.dnsChoice);
        dnsChoice.setOnClickListener(v -> showDnsPicker());
        fadeEnabledCheck = findViewById(R.id.fadeEnabledCheck);
        fadeEnabledCheck.setOnCheckedChangeListener((button, checked) -> {
            choices.edit().putBoolean(Config.UI_FADE_ENABLED_KEY, checked).apply();
            changed();
            refresh();
        });
        fadeDelayChoice = findViewById(R.id.fadeDelayChoice);
        fadeDelayChoice.setOnClickListener(v -> showFadeTimePicker(true));
        fadeDurationChoice = findViewById(R.id.fadeDurationChoice);
        fadeDurationChoice.setOnClickListener(v -> showFadeTimePicker(false));
        screenOffChoice = findViewById(R.id.screenOffChoice);
        screenOffChoice.setOnClickListener(v -> showScreenOffPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    // Custom for this set: Private DNS as a launcher setting — a predefined provider list
    // written to the system Global settings (needs the WRITE_SECURE_SETTINGS grant, which the
    // install script leaves in place for this build).
    private static final String[][] DNS_PROVIDERS = {
            {"AdGuard", "dns.adguard-dns.com"},
            {"Cloudflare", "one.one.one.one"},
            {"Google", "dns.google"},
            {"Quad9", "dns.quad9.net"},
            {"CleanBrowsing", "security-filter-dns.cleanbrowsing.org"},
    };

    private int currentDnsIndex() {
        String mode = android.provider.Settings.Global.getString(
                getContentResolver(), "private_dns_mode");
        if ("off".equals(mode)) return DNS_PROVIDERS.length + 1;
        if (!"hostname".equals(mode)) return DNS_PROVIDERS.length;   // automatic / unset
        String host = android.provider.Settings.Global.getString(
                getContentResolver(), "private_dns_specifier");
        for (int i = 0; i < DNS_PROVIDERS.length; i++) {
            if (DNS_PROVIDERS[i][1].equals(host)) return i;
        }
        return -1;   // a hostname outside the predefined list
    }

    private String currentDnsLabel() {
        int idx = currentDnsIndex();
        if (idx >= 0 && idx < DNS_PROVIDERS.length) return DNS_PROVIDERS[idx][0];
        if (idx == DNS_PROVIDERS.length) return getString(R.string.dns_automatic);
        if (idx == DNS_PROVIDERS.length + 1) return getString(R.string.dns_off);
        String host = android.provider.Settings.Global.getString(
                getContentResolver(), "private_dns_specifier");
        return host != null ? host : getString(R.string.dns_automatic);
    }

    private void showDnsPicker() {
        String[] labels = new String[DNS_PROVIDERS.length + 2];
        for (int i = 0; i < DNS_PROVIDERS.length; i++) labels[i] = DNS_PROVIDERS[i][0];
        labels[DNS_PROVIDERS.length] = getString(R.string.dns_automatic);
        labels[DNS_PROVIDERS.length + 1] = getString(R.string.dns_off);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dns_label)
                .setSingleChoiceItems(labels, currentDnsIndex(), (dialog, which) -> {
                    try {
                        if (which < DNS_PROVIDERS.length) {
                            android.provider.Settings.Global.putString(getContentResolver(),
                                    "private_dns_specifier", DNS_PROVIDERS[which][1]);
                            android.provider.Settings.Global.putString(getContentResolver(),
                                    "private_dns_mode", "hostname");
                        } else if (which == DNS_PROVIDERS.length) {
                            android.provider.Settings.Global.putString(getContentResolver(),
                                    "private_dns_mode", "opportunistic");
                        } else {
                            android.provider.Settings.Global.putString(getContentResolver(),
                                    "private_dns_mode", "off");
                        }
                        Toast.makeText(this, getString(R.string.dns_applied, labels[which]),
                                Toast.LENGTH_SHORT).show();
                    } catch (SecurityException e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
                    }
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Custom for this set: the idle fade-out is configurable — an on/off checkbox plus start
    // time and duration pickers, stored in the launcher settings prefs MainActivity reads.
    private static final long[] FADE_DELAYS = {30_000L, 60_000L, 120_000L, 300_000L, 0L};
    private static final long[] FADE_DURATIONS = {30_000L, 60_000L, 120_000L, 180_000L, 300_000L};

    private String fadeTimeLabel(long ms) {
        if (ms <= 0) return getString(R.string.fade_never);
        if (ms < 60_000L) return (ms / 1000) + " sec";
        return (ms / 60_000L) + " min";
    }

    private void showFadeTimePicker(boolean delay) {
        long[] options = delay ? FADE_DELAYS : FADE_DURATIONS;
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = fadeTimeLabel(options[i]);
        long current = choices.getLong(delay ? Config.UI_FADE_DELAY_KEY : Config.UI_FADE_DURATION_KEY,
                delay ? 60_000L : 120_000L);
        int checked = 0;
        for (int i = 0; i < options.length; i++) if (options[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(delay ? R.string.fade_delay_label : R.string.fade_duration_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    choices.edit().putLong(delay ? Config.UI_FADE_DELAY_KEY
                            : Config.UI_FADE_DURATION_KEY, options[which]).apply();
                    changed();
                    refresh();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Custom for this set: after the idle fade completes, keep the screen on this much longer,
    // then fade to black and sleep. 0 = never (the default).
    private static final long[] SCREEN_OFF_DELAYS = {0L, 60_000L, 120_000L, 300_000L, 600_000L, 1_800_000L};

    private void showScreenOffPicker() {
        String[] labels = new String[SCREEN_OFF_DELAYS.length];
        for (int i = 0; i < SCREEN_OFF_DELAYS.length; i++) labels[i] = fadeTimeLabel(SCREEN_OFF_DELAYS[i]);
        long current = choices.getLong(Config.SCREEN_OFF_DELAY_KEY, 0L);
        int checked = 0;
        for (int i = 0; i < SCREEN_OFF_DELAYS.length; i++) if (SCREEN_OFF_DELAYS[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.screen_off_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    choices.edit().putLong(Config.SCREEN_OFF_DELAY_KEY, SCREEN_OFF_DELAYS[which]).apply();
                    changed();
                    refresh();
                    dialog.dismiss();
                    if (SCREEN_OFF_DELAYS[which] > 0) offerSleepAdmin();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Real sleep needs a one-time device-admin grant (force-lock only): when a screen-off time
     *  is chosen and the grant is missing, open the system's activation screen for this app. */
    private void offerSleepAdmin() {
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager)
                getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName admin =
                new android.content.ComponentName(this, SleepAdminReceiver.class);
        if (dpm == null || dpm.isAdminActive(admin)) return;
        android.content.Intent intent = new android.content.Intent(
                android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.sleep_admin_description));
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // No system screen for it on this firmware: the timer still fades to black.
        }
    }

    // Custom for this set: wallpaper rotation lives inside the wallpaper popup (owner pick,
    // variant E) — a switch plus interval chips in the popup's side rail, stored in the
    // launcher settings prefs Config reads.
    private static final int[] ROTATION_INTERVALS = {5, 10, 15, 30, 60, 90};

    private String rotationIntervalLabel(int minutes) {
        return minutes + " min";
    }

    // Custom for this set: the wallpaper picker is a preview grid — picture thumbnails and live
    // wallpaper (videos/) frames side by side — instead of a plain filename list.
    // A small pool, not one thread: a single slow 4K video-frame probe must not starve
    // every other preview behind it.
    private final java.util.concurrent.ExecutorService thumbExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(3);
    private final java.util.Map<String, android.graphics.Bitmap> thumbCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Variant E (owner pick): the popup is split — a control rail on the left (rotation
    // switch + interval chips + None) and the preview grid on the right. Merged model
    // (owner pick): the rotation switch is the only rotation control — turning it on
    // clears any pinned pick, and picking a wallpaper turns rotation off.
    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // No separate "Theme wallpaper" card (owner's call): on this set the theme's
        // wallpaper.jpg is Photo 3, which the collection already offers — the extra card
        // was the same picture twice. The file itself stays; it is the theme default.
        List<File> videos = new java.util.ArrayList<>();
        File[] videoFiles = dir == null ? null : new File(dir, "videos").listFiles(
                f -> f.isFile() && f.getName().toLowerCase(java.util.Locale.US).endsWith(".mp4"));
        if (videoFiles != null) {
            java.util.Arrays.sort(videoFiles);
            videos.addAll(java.util.Arrays.asList(videoFiles));
        }
        float density = getResources().getDisplayMetrics().density;
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.HORIZONTAL);
        rootLayout.setPadding((int) (28 * density), (int) (24 * density),
                (int) (20 * density), (int) (20 * density));

        // --- Side rail: title, rotation switch, interval chips, None, hint. ---
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (264 * density), LinearLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(rail);

        TextView title = new TextView(this);
        title.setText(R.string.wallpaper_label);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        rail.addView(title);

        LinearLayout rotateRow = new LinearLayout(this);
        rotateRow.setOrientation(LinearLayout.HORIZONTAL);
        rotateRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rotateRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rotateRowLp.setMargins(0, (int) (22 * density), 0, 0);
        rotateRow.setLayoutParams(rotateRowLp);
        TextView rotateLabel = new TextView(this);
        rotateLabel.setText(R.string.rotation_enabled_label);
        rotateLabel.setTextColor(0xFFFFFFFF);
        rotateLabel.setTextSize(13);
        rotateLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        rotateRow.addView(rotateLabel);
        android.widget.Switch rotateSwitch = new android.widget.Switch(this);
        rotateRow.addView(rotateSwitch);
        rail.addView(rotateRow);

        TextView everyLabel = new TextView(this);
        everyLabel.setText(R.string.rotation_every_label);
        everyLabel.setTextColor(0xFFA8AEBA);
        everyLabel.setTextSize(12);
        LinearLayout.LayoutParams everyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        everyLp.setMargins(0, (int) (18 * density), 0, 0);
        everyLabel.setLayoutParams(everyLp);
        rail.addView(everyLabel);

        List<TextView> chips = new java.util.ArrayList<>();
        List<Integer> chipMinutes = new java.util.ArrayList<>();
        LinearLayout chipRow = null;
        for (int i = 0; i < ROTATION_INTERVALS.length; i++) {
            if (i % 3 == 0) {
                chipRow = new LinearLayout(this);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.setMargins(0, (int) (8 * density), 0, 0);
                chipRow.setLayoutParams(clp);
                rail.addView(chipRow);
            }
            TextView chip = new TextView(this);
            chip.setText(rotationIntervalLabel(ROTATION_INTERVALS[i]));
            chip.setTextSize(12);
            chip.setFocusable(true);
            chip.setPadding((int) (12 * density), (int) (6 * density),
                    (int) (12 * density), (int) (6 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int) (8 * density), 0);
            chip.setLayoutParams(lp);
            final int minutes = ROTATION_INTERVALS[i];
            chip.setOnClickListener(v -> {
                choices.edit().putInt(Config.WALLPAPER_ROTATION_MINUTES_KEY, minutes).apply();
                changed();
                refresh();
                restylePickerChips(chips, chipMinutes, density);
            });
            chip.setOnFocusChangeListener((v, focused) -> stylePickerChip(chip,
                    choices.getInt(Config.WALLPAPER_ROTATION_MINUTES_KEY, 30) == minutes,
                    focused, density));
            chips.add(chip);
            chipMinutes.add(minutes);
            chipRow.addView(chip);
        }

        View divider = new View(this);
        divider.setBackgroundColor(0xFF343945);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, (int) density));
        dlp.setMargins(0, (int) (24 * density), 0, (int) (16 * density));
        divider.setLayoutParams(dlp);
        rail.addView(divider);

        TextView noneRow = wallpaperOptionRow(getString(R.string.wallpaper_none_row),
                "none".equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, null)), density);
        noneRow.setOnClickListener(v -> {
            choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none")
                    .putBoolean(Config.WALLPAPER_ROTATION_ENABLED_KEY, false).apply();
            changed();
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
            dialogHolder[0].dismiss();
        });
        rail.addView(noneRow);

        TextView hint = new TextView(this);
        hint.setText(R.string.wallpaper_rotation_hint);
        hint.setTextColor(0xFFA8AEBA);
        hint.setTextSize(11);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, (int) (14 * density), 0, 0);
        hint.setLayoutParams(hlp);
        rail.addView(hint);

        // --- Preview grid on the right of a thin divider. ---
        View vDivider = new View(this);
        vDivider.setBackgroundColor(0xFF343945);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                Math.max(1, (int) density), LinearLayout.LayoutParams.MATCH_PARENT);
        vlp.setMargins((int) (20 * density), 0, (int) (18 * density), 0);
        vDivider.setLayoutParams(vlp);
        rootLayout.addView(vDivider);

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(3);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        scroll.addView(grid);
        rootLayout.addView(scroll);

        List<File> all = new java.util.ArrayList<>(images);
        all.addAll(videos);
        List<View> cards = new java.util.ArrayList<>();
        List<TextView> cardTags = new java.util.ArrayList<>();
        List<Object[]> loadQueue = new java.util.ArrayList<>();
        View focusTarget = null;
        int focusIndex = -1;
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        for (File file : all) {
            boolean video = videos.contains(file);
            View card = wallpaperCard(file, video, density, dialogHolder, cardTags, loadQueue);
            grid.addView(card);
            cards.add(card);
            if (file.getName().equals(current)) {
                focusTarget = card;
                focusIndex = cards.size() - 1;
            }
        }
        // Load previews starting at the current pick so its neighbourhood appears first;
        // the serial loader would otherwise take seconds to reach the bottom of the grid.
        int loadStart = focusIndex >= 0 ? focusIndex : 0;
        for (int k = 0; k < loadQueue.size(); k++) {
            Object[] job = loadQueue.get((loadStart + k) % loadQueue.size());
            loadWallpaperThumb((File) job[0], (Boolean) job[1],
                    (android.widget.ImageView) job[2]);
        }

        rotateSwitch.setChecked(pickerRotationOn());
        restylePickerChips(chips, chipMinutes, density);
        rotateSwitch.setOnCheckedChangeListener((button, checked) -> {
            android.content.SharedPreferences.Editor edit =
                    choices.edit().putBoolean(Config.WALLPAPER_ROTATION_ENABLED_KEY, checked);
            // Merged model: rotation on means no pinned pick.
            if (checked) edit.remove(Config.WALLPAPER_CHOICE_KEY);
            edit.apply();
            changed();
            refresh();
            restylePickerChips(chips, chipMinutes, density);
            restylePickerCards(cards, cardTags, density);
            styleWallpaperRow(noneRow, "none".equals(
                    choices.getString(Config.WALLPAPER_CHOICE_KEY, null)),
                    noneRow.isFocused(), density);
        });
        if (focusTarget == null) {
            focusTarget = "none".equals(current) ? noneRow : rotateSwitch;
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.WallpaperDialog)
                .setView(rootLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            window.setLayout((int) (dm.widthPixels * 0.94), (int) (dm.heightPixels * 0.87));
            android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cancel != null) cancel.setTextColor(0xFFFFFFFF);
        }
        // Equal previews: the fixed card width overflows the grid's real width and
        // GridLayout squeezes the last column, so size every card from the measured
        // viewport width once the dialog is laid out — three identical 16:9 cards.
        scroll.post(() -> {
            int cardWidth = (scroll.getWidth() - (int) (36 * density)) / 3;
            if (cardWidth <= 0) return;
            int cardHeight = cardWidth * 9 / 16;
            for (View card : cards) {
                android.widget.GridLayout.LayoutParams lp =
                        (android.widget.GridLayout.LayoutParams) card.getLayoutParams();
                lp.width = cardWidth;
                lp.height = cardHeight;
                card.setLayoutParams(lp);
            }
        });
        focusTarget.requestFocus();
    }

    /** Rotation is in effect only when the setting is on and no wallpaper is pinned. */
    private boolean pickerRotationOn() {
        return choices.getBoolean(Config.WALLPAPER_ROTATION_ENABLED_KEY, false)
                && choices.getString(Config.WALLPAPER_CHOICE_KEY, null) == null;
    }

    private void stylePickerChip(TextView chip, boolean selected, boolean focused, float density) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(16 * density);
        bg.setColor(selected ? 0xFFFFFFFF : 0xFF30353F);
        if (focused) bg.setStroke((int) (2 * density), 0xFFFFFFFF);
        chip.setBackground(bg);
        chip.setTextColor(selected ? 0xFF121418 : 0xFFE1E4EB);
    }

    private void restylePickerChips(List<TextView> chips, List<Integer> chipMinutes, float density) {
        boolean on = pickerRotationOn();
        int current = choices.getInt(Config.WALLPAPER_ROTATION_MINUTES_KEY, 30);
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            stylePickerChip(chip, chipMinutes.get(i) == current, chip.isFocused(), density);
            chip.setEnabled(on);
            chip.setAlpha(on ? 1f : 0.5f);
        }
    }

    private void restylePickerCards(List<View> cards, List<TextView> cardTags, float density) {
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        for (int i = 0; i < cards.size(); i++) {
            boolean selected = cards.get(i).getTag() != null
                    && cards.get(i).getTag().equals(current);
            styleWallpaperCard(cards.get(i), selected, cards.get(i).isFocused(), density);
            cardTags.get(i).setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    private TextView wallpaperOptionRow(String label, boolean selected, float density) {
        TextView row = new TextView(this);
        row.setText(selected ? label + " — current" : label);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(13);
        row.setFocusable(true);
        row.setPadding((int) (14 * density), (int) (10 * density),
                (int) (14 * density), (int) (10 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (8 * density));
        row.setLayoutParams(lp);
        styleWallpaperRow(row, selected, false, density);
        row.setOnFocusChangeListener((v, focused) -> styleWallpaperRow(row, selected, focused, density));
        return row;
    }

    private void styleWallpaperRow(View view, boolean selected, boolean focused, float density) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF1C1E24);
        bg.setCornerRadius(10 * density);
        if (focused) bg.setStroke((int) (2 * density), 0xFFFFFFFF);
        else if (selected) bg.setStroke((int) (2 * density), 0xFF7DD87D);
        view.setBackground(bg);
    }

    // Variant B (owner pick): no card box — the preview fills the card, the name sits on the
    // picture over a dark scrim, and focus/selected are strokes drawn over the picture's edge.
    private void styleWallpaperCard(View view, boolean selected, boolean focused, float density) {
        if (!focused && !selected) {
            view.setForeground(null);
            return;
        }
        android.graphics.drawable.GradientDrawable fg = new android.graphics.drawable.GradientDrawable();
        fg.setColor(0x00000000);
        fg.setCornerRadius(3 * density);
        fg.setStroke((int) (2.5 * density), focused ? 0xFFFFFFFF : 0xFF7DD87D);
        view.setForeground(fg);
    }

    private View wallpaperCard(File file, boolean video, float density,
                               AlertDialog[] dialogHolder, List<TextView> tagSink,
                               List<Object[]> loadQueue) {
        boolean selected = file.getName().equals(
                choices.getString(Config.WALLPAPER_CHOICE_KEY, null));
        android.widget.FrameLayout card = new android.widget.FrameLayout(this);
        card.setFocusable(true);
        card.setTag(file.getName());
        android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
        lp.width = (int) (184 * density);
        lp.height = (int) (103 * density);
        lp.setMargins((int) (6 * density), (int) (6 * density),
                (int) (6 * density), (int) (6 * density));
        card.setLayoutParams(lp);
        styleWallpaperCard(card, selected, false, density);
        card.setOnFocusChangeListener((v, focused) -> styleWallpaperCard(card,
                file.getName().equals(choices.getString(Config.WALLPAPER_CHOICE_KEY, null)),
                focused, density));

        android.widget.ImageView thumb = new android.widget.ImageView(this);
        thumb.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xFF000000);
        card.addView(thumb);

        View scrim = new View(this);
        android.graphics.drawable.GradientDrawable scrimBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xA6000000});
        scrim.setBackground(scrimBg);
        android.widget.FrameLayout.LayoutParams slp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, (int) (46 * density));
        slp.gravity = android.view.Gravity.BOTTOM;
        scrim.setLayoutParams(slp);
        card.addView(scrim);

        TextView name = new TextView(this);
        name.setText(wallpaperDisplayName(file.getName()));
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(13);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding((int) (10 * density), 0, (int) (34 * density), (int) (7 * density));
        android.widget.FrameLayout.LayoutParams nlp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        nlp.gravity = android.view.Gravity.BOTTOM;
        name.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.START);
        name.setLayoutParams(nlp);
        card.addView(name);

        if (video) {
            // A tiny, subtle play icon at the preview's bottom-right corner marks a live wallpaper.
            android.widget.ImageView badge = new android.widget.ImageView(this);
            badge.setImageResource(android.R.drawable.ic_media_play);
            badge.setColorFilter(0xFFFFFFFF);
            badge.setAlpha(0.55f);
            android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(
                    (int) (20 * density), (int) (20 * density));
            blp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            blp.setMargins(0, 0, (int) (8 * density), (int) (8 * density));
            badge.setLayoutParams(blp);
            card.addView(badge);
        }

        // The current pick carries a small CURRENT tag at the preview's top-right corner.
        TextView currentTag = new TextView(this);
        currentTag.setText(R.string.wallpaper_current_tag);
        currentTag.setTextColor(0xFF7DD87D);
        currentTag.setTextSize(10);
        currentTag.setTypeface(null, android.graphics.Typeface.BOLD);
        currentTag.setPadding((int) (6 * density), (int) (2 * density),
                (int) (6 * density), (int) (2 * density));
        android.graphics.drawable.GradientDrawable tagBg = new android.graphics.drawable.GradientDrawable();
        tagBg.setColor(0xCC1E4628);
        tagBg.setCornerRadius(4 * density);
        currentTag.setBackground(tagBg);
        currentTag.setVisibility(selected ? View.VISIBLE : View.GONE);
        android.widget.FrameLayout.LayoutParams tlp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        tlp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        tlp.setMargins(0, (int) (8 * density), (int) (8 * density), 0);
        currentTag.setLayoutParams(tlp);
        card.addView(currentTag);
        tagSink.add(currentTag);

        card.setOnClickListener(v -> {
            // Merged model: pinning a wallpaper turns rotation off.
            choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, file.getName())
                    .putBoolean(Config.WALLPAPER_ROTATION_ENABLED_KEY, false).apply();
            changed();
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
            dialogHolder[0].dismiss();
        });
        loadQueue.add(new Object[]{file, video, thumb});
        return card;
    }

    /** "photo-03.jpg" reads "Photo 3"; "nr-snow-mountain.mp4" reads "Snow Mountain". */
    private String wallpaperDisplayName(String fileName) {
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.equals("wallpaper")) return "Theme wallpaper";
        if (base.startsWith("photo-")) {
            try {
                return "Photo " + Integer.parseInt(base.substring(6));
            } catch (NumberFormatException ignored) {
                return base;
            }
        }
        String[] parts = base.split("-");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i == 0 && parts.length > 1 && parts[i].length() <= 2) continue;   // collection prefix
            if (out.length() > 0) out.append(' ');
            String part = parts[i];
            if (!part.isEmpty()) out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.length() == 0 ? base : out.toString();
    }

    private void loadWallpaperThumb(File file, boolean video, android.widget.ImageView view) {
        String key = file.getAbsolutePath();
        android.graphics.Bitmap cached = thumbCache.get(key);
        if (cached != null) view.setImageBitmap(cached);
        thumbExecutor.execute(() -> {
            android.graphics.Bitmap bmp = thumbCache.get(key);
            if (bmp == null) {
                // A pre-generated preview on disk wins: probing a 4K video for one frame
                // takes seconds each on this set, decoding a small JPEG is instant.
                File onDisk = new File(new File(Config.dir(this), "thumbs"),
                        file.getName() + ".jpg");
                if (onDisk.isFile()) bmp = imageThumb(onDisk);
            }
            if (bmp == null) bmp = video ? probeVideo(file) : imageThumb(file);
            if (bmp != null) thumbCache.put(key, bmp);
            final android.graphics.Bitmap result = bmp;
            view.post(() -> {
                if (result != null) view.setImageBitmap(result);
            });
        });
    }

    /** Low-quality previews are enough for a picker: they load fast and stay light. */
    private android.graphics.Bitmap imageThumb(File file) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > 320) sample *= 2;
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = sample;
        return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /** A video's preview frame (at half a second), downscaled like the picture previews. */
    private android.graphics.Bitmap probeVideo(File file) {
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        android.graphics.Bitmap frame = null;
        try {
            retriever.setDataSource(file.getAbsolutePath());
            frame = retriever.getFrameAtTime(500_000,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null && frame.getWidth() > 320) {
                float scale = 320f / frame.getWidth();
                frame = android.graphics.Bitmap.createScaledBitmap(frame,
                        320, Math.max(1, (int) (frame.getHeight() * scale)), true);
            }
        } catch (RuntimeException ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return frame;
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String wallpaperState;
        if ("none".equals(wallpaperCurrent)) {
            wallpaperState = getString(R.string.wallpaper_none);
        } else if (wallpaperCurrent != null) {
            wallpaperState = wallpaperDisplayName(wallpaperCurrent);
        } else if (choices.getBoolean(Config.WALLPAPER_ROTATION_ENABLED_KEY, false)) {
            wallpaperState = getString(R.string.wallpaper_state_rotating,
                    rotationIntervalLabel(choices.getInt(
                            Config.WALLPAPER_ROTATION_MINUTES_KEY, 30)));
        } else {
            wallpaperState = getString(R.string.wallpaper_theme_default);
        }
        wallpaperChoice.setText(getString(R.string.wallpaper_state, wallpaperState));
        dnsChoice.setText(getString(R.string.dns_state, currentDnsLabel()));
        boolean fadeOn = choices.getBoolean(Config.UI_FADE_ENABLED_KEY, true);
        if (fadeEnabledCheck.isChecked() != fadeOn) fadeEnabledCheck.setChecked(fadeOn);
        fadeDelayChoice.setText(getString(R.string.fade_delay_state,
                fadeTimeLabel(choices.getLong(Config.UI_FADE_DELAY_KEY, 60_000L))));
        fadeDurationChoice.setText(getString(R.string.fade_duration_state,
                fadeTimeLabel(choices.getLong(Config.UI_FADE_DURATION_KEY, 120_000L))));
        fadeDelayChoice.setEnabled(fadeOn);
        fadeDelayChoice.setAlpha(fadeOn ? 1f : 0.5f);
        fadeDurationChoice.setEnabled(fadeOn);
        fadeDurationChoice.setAlpha(fadeOn ? 1f : 0.5f);
        screenOffChoice.setText(getString(R.string.screen_off_state,
                fadeTimeLabel(choices.getLong(Config.SCREEN_OFF_DELAY_KEY, 0L))));
        screenOffChoice.setEnabled(fadeOn);
        screenOffChoice.setAlpha(fadeOn ? 1f : 0.5f);
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
