package ro.dotdesign.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** User-reviewed email only. No automatic transmission, log collection or storage. */
public final class SupportActivity extends Activity {
    private boolean dictate;
    private int accent, soft, surface, ink, muted;
    private EditText description;
    private CheckBox technical;
    private Button categoryButton;
    private int category;
    private String[] categories;
    private String provider = "";
    AlertDialog activeDialog;

    public static void open(Context context) { open(context, ""); }

    public static void open(Context context, String provider) {
        Intent intent = new Intent(context, SupportActivity.class);
        intent.putExtra("provider", SupportReport.provider(provider));
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dictate = getPackageName().contains("dictate") || getPackageName().contains("dictation");
        provider = dictate ? SupportReport.provider(getIntent().getStringExtra("provider")) : "";
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        accent = Color.parseColor(dictate ? (dark ? "#C4B7FF" : "#5B48CF") : (dark ? "#65D7AC" : "#00885A"));
        soft = Color.parseColor(dark ? "#303237" : dictate ? "#EEE8FF" : "#E0F3EA");
        surface = Color.parseColor(dark ? "#242729" : "#FFFFFF");
        ink = Color.parseColor(dark ? "#F1F2F1" : "#202524");
        muted = Color.parseColor(dark ? "#B7C0BB" : "#68746F");
        int background = Color.parseColor(dark ? "#151819" : "#F7F6F2");
        categories = new String[]{s("choose"), s(dictate ? "dictation" : "calls"),
                s(dictate ? "translation" : "contacts"), s("access"), s("other")};
        category = state == null ? 0 : Math.max(0, Math.min(4, state.getInt("category")));

        LinearLayout content = column();
        content.setPadding(dp(20), dp(12), dp(20), dp(28));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹", false);
        back.setTextSize(28); back.setBackgroundColor(Color.TRANSPARENT);
        back.setContentDescription(s("back")); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text(s("title"), 21, true); header.addView(title);
        add(content, header, 0);
        LinearLayout hero = new LinearLayout(this); hero.setGravity(Gravity.CENTER_VERTICAL);
        { ImageView image = new ImageView(this); image.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(52), dp(52));
            size.setMarginEnd(dp(14)); hero.addView(image, size); }
        LinearLayout greeting = column(); greeting.addView(text(s("headline"), 23, true));
        greeting.addView(note(s("subtitle"), 14)); hero.addView(greeting);
        add(content, hero, 28); add(content, note(s("intro"), 15), 20);

        Button quick = button(s("quick"), false); quick.setBackground(shape(soft));
        quick.setOnClickListener(v -> openGuide()); add(content, quick, 22);
        LinearLayout form = card();
        form.addView(text(s("category"), 15, true));
        categoryButton = button(categories[category], false);
        categoryButton.setOnClickListener(v -> activeDialog = new AlertDialog.Builder(this).setTitle(s("category"))
                .setSingleChoiceItems(categories, category, (dialog, index) -> {
                    category = index; categoryButton.setText(categories[index]); dialog.dismiss();
                }).setNegativeButton(s("cancel"), null).show());
        add(form, categoryButton, 8); add(form, text(s("description"), 15, true), 18);
        description = new EditText(this); description.setId(SupportResources.DESCRIPTION_ID);
        description.setTextColor(ink); description.setHintTextColor(muted); description.setTextSize(16);
        description.setHint(s(dictate ? "hint_dictate" : "hint_dialer"));
        description.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3000)});
        description.setMinLines(4); description.setGravity(Gravity.TOP | Gravity.START);
        description.setPadding(dp(12), dp(12), dp(12), dp(12)); description.setBackground(shape(soft));
        if (state != null) description.setText(state.getString("description", ""));
        add(form, description, 10); add(content, form, 22);

        LinearLayout privacy = card();
        technical = new CheckBox(this); technical.setId(SupportResources.TECHNICAL_ID); technical.setText(s("technical"));
        technical.setTextSize(16); technical.setTextColor(ink); technical.setMinHeight(dp(48));
        technical.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
        technical.setChecked(state == null || state.getBoolean("technical", true));
        privacy.addView(technical); privacy.addView(note(s("technical_detail"), 13));
        add(content, privacy, 16); add(content, note(s(dictate ? "privacy_dictate" : "privacy_dialer"), 13), 12);
        Button send = button(s("send"), true); send.setOnClickListener(v -> compose()); add(content, send, 24);
        TextView recipient = note(SupportReport.recipient(dictate), 15); recipient.setTextColor(accent);
        recipient.setGravity(Gravity.CENTER); add(content, recipient, 12);
        TextView review = note(s("review"), 12); review.setGravity(Gravity.CENTER); add(content, review, 8);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(background);
        scroll.addView(content); scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(dark ? 0 : light, light);
        } else if (!dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("category", category); state.putString("description", description.getText().toString());
        state.putBoolean("technical", technical.isChecked()); super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        super.onDestroy();
    }

    private void compose() {
        if (description.getText().toString().trim().isEmpty()) {
            description.setError(s("required")); description.requestFocus(); return;
        }
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        String body = SupportReport.body(category == 0 ? s("other") : categories[category],
                description.getText().toString(), technical.isChecked(), version,
                Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")", Build.MANUFACTURER + " " + Build.MODEL, provider);
        String subject = (dictate ? "dot.dictate" : "dot.dialer") + " · " + s("title");
        Uri uri = Uri.parse("mailto:" + SupportReport.recipient(dictate)
                + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body));
        try { startActivity(new Intent(Intent.ACTION_SENDTO, uri)); }
        catch (ActivityNotFoundException e) { activeDialog = new AlertDialog.Builder(this).setTitle(s("no_mail"))
                .setMessage(SupportReport.recipient(dictate)).setPositiveButton(s("copy"), (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(subject, SupportReport.recipient(dictate) + "\n" + subject + "\n\n" + body));
                    android.widget.Toast.makeText(this, s("copied"), android.widget.Toast.LENGTH_SHORT).show();
                }).setNegativeButton(s("cancel"), null).show(); }
    }

    private void openGuide() {
        boolean ro = getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ro");
        String url = dictate ? (ro ? "https://dotdesign.ro/aplicatii/dot-dictation/ghid/" : "https://dotdesign.ro/en/apps/dot-dictation/guide/")
                : (ro ? "https://dotdesign.ro/aplicatii/dot-dialer/suport/" : "https://dotdesign.ro/en/apps/dot-dialer/support/");
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { android.widget.Toast.makeText(this, url, android.widget.Toast.LENGTH_LONG).show(); }
    }

    private String s(String key) { return getString(SupportResources.string(key)); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card() { LinearLayout v = column(); v.setPadding(dp(14), dp(14), dp(14), dp(14)); v.setBackground(shape(surface)); return v; }
    private GradientDrawable shape(int color) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(16)); return g; }
    private TextView text(String value, int size, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextColor(ink); v.setTextSize(size); if (bold) v.setTypeface(null, Typeface.BOLD); return v; }
    private TextView note(String value, int size) { TextView v = text(value, size, false); v.setTextColor(muted); return v; }
    private Button button(String value, boolean primary) { Button v = new Button(this); v.setText(value); v.setTextSize(16); v.setAllCaps(false); v.setMinHeight(dp(48)); v.setStateListAnimator(null); v.setElevation(0); v.setTextColor(primary ? (Color.luminance(accent) > .4 ? Color.BLACK : Color.WHITE) : accent); v.setBackground(shape(primary ? accent : surface)); return v; }
    private void add(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin = dp(top); parent.addView(view, p); }
}
