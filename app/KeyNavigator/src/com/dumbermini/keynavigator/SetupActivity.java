package com.dumbermini.keynavigator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ActionMenuView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SetupActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final List<AppEntry> installed = new ArrayList<>(), filtered = new ArrayList<>();
    private AppSettings controls;
    private TextView count;
    private Switch serviceToggle;
    private EditText search;
    private AppListAdapter adapter;
    private int loadGeneration;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AccessibilityConfig.initializeOnce(this);
        controls = new AppSettings(this);
        setTitle(R.string.key_navigation_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        serviceToggle = new Switch(this);
        serviceToggle.setId(R.id.service_toggle);
        serviceToggle.setText(R.string.use_key_navigation);
        serviceToggle.setTextSize(18);
        serviceToggle.setPadding(dp(20), dp(16), dp(20), dp(16));
        serviceToggle.setMinimumHeight(dp(72));
        serviceToggle.setBackgroundResource(R.drawable.main_switch_background);
        serviceToggle.setOnKeyListener((view, key, event) -> key == KeyEvent.KEYCODE_DPAD_UP
                && event.getAction() == KeyEvent.ACTION_DOWN && focusOverflowMenu());
        LinearLayout.LayoutParams masterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        masterParams.setMargins(dp(16), dp(12), dp(16), dp(4));
        content.addView(serviceToggle, masterParams);

        search = new EditText(this);
        search.setId(R.id.app_search);
        search.setSingleLine(true);
        search.setHint(R.string.search_apps);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dp(16), 0, dp(16), dp(4));
        content.addView(search, searchParams);
        count = new TextView(this);
        count.setTextSize(14);
        count.setPadding(dp(20), dp(12), dp(20), dp(8));
        TypedValue accent = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true);
        count.setTextColor(accent.data);
        count.setText(R.string.loading_apps);
        content.addView(count);
        ListView list = new ListView(this);
        list.setId(R.id.app_list);
        list.setItemsCanFocus(true);
        list.setDivider(null);
        adapter = new AppListAdapter();
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int before, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int after) { filterApps(); }
            @Override public void afterTextChanged(Editable text) {}
        });
        setContentView(content);
        serviceToggle.requestFocus();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.controls_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    private boolean focusOverflowMenu() {
        ImageButton overflow = findOverflowButton(getWindow().getDecorView());
        if (overflow == null) return false;
        // The framework action bar is a separate keyboard-navigation cluster.
        // Give D-pad users a direct route from the master switch and back down.
        if (overflow.getId() == View.NO_ID) overflow.setId(R.id.overflow_menu);
        overflow.setFocusableInTouchMode(true);
        overflow.setNextFocusDownId(serviceToggle.getId());
        overflow.setOnKeyListener((view, key, event) -> key == KeyEvent.KEYCODE_DPAD_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN && serviceToggle.requestFocus());
        return overflow.requestFocus();
    }

    private ImageButton findOverflowButton(View view) {
        // This page has only overflow menu items; its ActionMenuView's ImageButton
        // is the native three-dot button, independent of translated descriptions.
        if (view instanceof ImageButton && view.getParent() instanceof ActionMenuView
                && view.isShown() && view.isEnabled()) return (ImageButton) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageButton found = findOverflowButton(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this).setTitle(R.string.key_navigation_title)
                    .setMessage(R.string.instructions).setPositiveButton(android.R.string.ok, null).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshServiceState();
        loadApps();
    }

    private void refreshServiceState() {
        serviceToggle.setOnCheckedChangeListener(null);
        boolean enabled = AccessibilityConfig.isEnabled(this);
        serviceToggle.setChecked(enabled);
        serviceToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!AccessibilityConfig.setEnabled(this, checked)) {
                startActivity(AccessibilityConfig.detailsIntent(this));
            }
            refreshServiceState();
        });
    }

    private void loadApps() {
        int generation = ++loadGeneration;
        loader.execute(() -> {
            PackageManager manager = getPackageManager();
            List<AppEntry> apps = new ArrayList<>();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo entry : manager.queryIntentActivities(launcher, 0)) {
                if (entry.activityInfo == null) continue;
                ApplicationInfo app = entry.activityInfo.applicationInfo;
                if (!seen.add(app.packageName)) continue;
                CharSequence label = app.loadLabel(manager);
                apps.add(new AppEntry(app.packageName, label == null ? app.packageName : label.toString(), app.loadIcon(manager)));
            }
            Collator collator = Collator.getInstance();
            apps.sort((a, b) -> {
                int result = collator.compare(a.label, b.label);
                return result == 0 ? a.packageName.compareTo(b.packageName) : result;
            });
            runOnUiThread(() -> {
                if (isDestroyed() || generation != loadGeneration) return;
                installed.clear(); installed.addAll(apps); filterApps();
            });
        });
    }

    private void filterApps() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (AppEntry app : installed) {
            if (app.label.toLowerCase(Locale.ROOT).contains(query)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(query)) filtered.add(app);
        }
        adapter.notifyDataSetChanged();
        count.setText(filtered.isEmpty() ? getString(R.string.no_apps)
                : getResources().getQuantityString(R.plurals.app_count, filtered.size(), filtered.size()));
    }

    @Override protected void onDestroy() {
        loadGeneration++;
        loader.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private final class AppListAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public AppEntry getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Row row;
            if (recycled == null) {
                row = new Row();
                LinearLayout layout = new LinearLayout(SetupActivity.this);
                layout.setGravity(Gravity.CENTER_VERTICAL);
                layout.setPadding(dp(20), dp(12), dp(20), dp(12));
                layout.setMinimumHeight(dp(72));
                layout.setFocusable(true);
                layout.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                TypedValue background = new TypedValue();
                getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
                layout.setBackgroundResource(background.resourceId);
                row.icon = new ImageView(SetupActivity.this);
                row.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
                iconParams.setMarginEnd(dp(20));
                layout.addView(row.icon, iconParams);
                LinearLayout labels = new LinearLayout(SetupActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                row.name = new TextView(SetupActivity.this);
                row.name.setId(R.id.app_label);
                row.name.setTextSize(17);
                row.name.setMaxLines(2);
                row.name.setEllipsize(TextUtils.TruncateAt.END);
                labels.addView(row.name);
                layout.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.toggle = new Switch(SetupActivity.this);
                row.toggle.setId(R.id.app_toggle);
                row.toggle.setFocusable(false);
                layout.addView(row.toggle);
                Switch toggle = row.toggle;
                layout.setOnClickListener(v -> toggle.performClick());
                layout.setTag(row);
                recycled = layout;
            } else row = (Row) recycled.getTag();
            AppEntry app = getItem(position);
            row.name.setText(app.label);
            row.icon.setImageDrawable(app.icon);
            row.toggle.setOnCheckedChangeListener(null);
            row.toggle.setChecked(controls.enabled(app.packageName));
            row.toggle.setContentDescription(getString(R.string.app_controls, app.label));
            row.toggle.setOnCheckedChangeListener((button, checked) -> controls.setEnabled(app.packageName, checked));
            return recycled;
        }
    }

    private static final class Row { TextView name; ImageView icon; Switch toggle; }
    private static final class AppEntry {
        final String packageName, label;
        final Drawable icon;
        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName; this.label = label; this.icon = icon;
        }
    }
}
