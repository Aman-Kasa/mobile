package com.burdew.app.ui;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.burdew.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.util.Date;

/**
 * Small form toolkit. Forms are built in code from string resources, which keeps
 * dozens of near-identical XML layouts out of the APK while all visible text
 * stays translatable (Tigrinya / Arabic later).
 */
public final class Ui {
    private Ui() {}

    public static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    public static final int MULTILINE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    public static final int PHONE = InputType.TYPE_CLASS_PHONE;
    public static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    public static final int PASSWORD = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    public static final int PLAIN = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** Sets a scrolling vertical form as the activity content and returns its container. */
    public static LinearLayout form(Activity a) {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        LinearLayout root = column(a);
        int p = dp(a, 16);
        root.setPadding(p, p, p, p * 2);
        scroll.addView(root);
        a.setContentView(scroll);
        return root;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    public static LinearLayout card(Context c, ViewGroup parent) {
        LinearLayout l = column(c);
        l.setBackgroundResource(R.drawable.bg_card);
        int p = dp(c, 12);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        parent.addView(l, lp);
        return l;
    }

    public static EditText field(ViewGroup parent, @StringRes int label, int inputType) {
        Context c = parent.getContext();
        TextInputLayout til = new TextInputLayout(c);
        til.setHint(c.getString(label));
        TextInputEditText e = new TextInputEditText(til.getContext());
        e.setInputType(inputType);
        if (inputType == MULTILINE) e.setMinLines(2);
        if (inputType == PASSWORD) til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        til.addView(e);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 6);
        parent.addView(til, lp);
        return e;
    }

    public static TextView section(ViewGroup parent, @StringRes int text) {
        TextView t = new TextView(parent.getContext());
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.burdew_primary));
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), 18);
        parent.addView(t, lp);
        return t;
    }

    public static TextView text(ViewGroup parent, CharSequence text) {
        TextView t = new TextView(parent.getContext());
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.burdew_text));
        t.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(parent.getContext(), 4);
        parent.addView(t, lp);
        return t;
    }

    public static TextView muted(ViewGroup parent, CharSequence text) {
        TextView t = text(parent, text);
        t.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.burdew_text_muted));
        t.setTextSize(13);
        return t;
    }

    public static Button button(ViewGroup parent, @StringRes int label, boolean outlined, View.OnClickListener click) {
        Context c = parent.getContext();
        MaterialButton b = outlined
                ? new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                : new MaterialButton(c);
        b.setText(label);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        parent.addView(b, lp);
        return b;
    }

    public static Spinner spinner(ViewGroup parent, @StringRes int label, String[] items) {
        Context c = parent.getContext();
        muted(parent, c.getString(label));
        Spinner s = new Spinner(c);
        ArrayAdapter<String> a = new ArrayAdapter<>(c, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static String val(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    public static void toast(Context c, CharSequence msg) {
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    public static String dateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(millis));
    }

    public static String date(long millis) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }

    /** Human label for an enum such as IN_PROGRESS -> "In progress". */
    public static String label(Enum<?> e) {
        return e == null ? "" : label(e.name());
    }

    public static String label(String constant) {
        if (constant == null || constant.isEmpty()) return "";
        String s = constant.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
