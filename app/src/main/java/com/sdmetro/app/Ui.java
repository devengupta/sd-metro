package com.sdmetro.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.Locale;

/** Colours, fonts and small view builders that reproduce the blueprint look of the app. */
final class Ui {
    private Ui() { }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static int PAPER, CARD, DEEP, INK, MUTED, DIM, RULE, SIGNAL, GOOD, WARN, BAD, BLUE, TRACK, OPT;
    static Typeface DISPLAY5, DISPLAY6, DISPLAY7, BODY4, BODY5, BODY7, MONO4, MONO5, MONO6;
    static float den = 1;
    private static boolean ready;

    static void init(Context c) {
        den = c.getResources().getDisplayMetrics().density;
        if (ready) return;
        PAPER = c.getColor(R.color.paper); CARD = c.getColor(R.color.card); DEEP = c.getColor(R.color.deep);
        INK = c.getColor(R.color.ink); MUTED = c.getColor(R.color.muted); DIM = c.getColor(R.color.dim);
        RULE = c.getColor(R.color.rule); SIGNAL = c.getColor(R.color.signal); GOOD = c.getColor(R.color.good);
        WARN = c.getColor(R.color.warn); BAD = c.getColor(R.color.bad); BLUE = c.getColor(R.color.ride);
        TRACK = c.getColor(R.color.track); OPT = c.getColor(R.color.opt);
        DISPLAY5 = c.getResources().getFont(R.font.chakra_petch_500);
        DISPLAY6 = c.getResources().getFont(R.font.chakra_petch_600);
        DISPLAY7 = c.getResources().getFont(R.font.chakra_petch_700);
        BODY4 = c.getResources().getFont(R.font.manrope_400);
        BODY5 = c.getResources().getFont(R.font.manrope_500);
        BODY7 = c.getResources().getFont(R.font.manrope_700);
        MONO4 = c.getResources().getFont(R.font.ibm_plex_mono_400);
        MONO5 = c.getResources().getFont(R.font.ibm_plex_mono_500);
        MONO6 = c.getResources().getFont(R.font.ibm_plex_mono_600);
        ready = true;
    }

    static int dp(float v) { return Math.round(v * den); }

    static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(a * 255) << 24); }

    static String up(String s) { return s == null ? "" : s.toUpperCase(Locale.ROOT); }

    static String pad2(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    static float clamp(float lo, float v, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /* ---------------- text ---------------- */

    /** Text with a CSS-style line height (a multiple of the font size). */
    static W.T t(Context c, CharSequence s, Typeface f, float sp, int color, float lineHeight) {
        W.T v = new W.T(c);
        v.setTypeface(f);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.lineHeight(lineHeight);
        v.setText(s);
        return v;
    }

    static W.T body(Context c, CharSequence s) { return t(c, s, BODY4, 16, INK, 1.55f); }

    static W.T bold(Context c, CharSequence s) { return t(c, s, BODY7, 16, INK, 1.55f); }

    static W.T note(Context c, CharSequence s) { return t(c, s, BODY4, 13, MUTED, 1.55f); }

    static W.T mono(Context c, CharSequence s, float sp, int color) { return t(c, s, MONO4, sp, color, 1.55f); }

    /** Small uppercase mono caption. */
    static W.T label(Context c, String s, int color, float spacing) {
        W.T v = t(c, up(s), MONO4, 10, color, 1.55f);
        v.setLetterSpacing(spacing);
        return v;
    }

    /** Display heading: uppercase Chakra Petch 700, tight lines, balanced wrapping. */
    static W.T head(Context c, CharSequence s, float sp, int color) {
        W.T v = t(c, s instanceof String ? up((String) s) : s, DISPLAY7, sp, color, 1.05f);
        v.setLetterSpacing(.02f);
        v.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);
        v.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        return v;
    }

    static final class FontSpan extends MetricAffectingSpan {
        private final Typeface f;
        FontSpan(Typeface f) { this.f = f; }
        @Override public void updateMeasureState(TextPaint p) { p.setTypeface(f); }
        @Override public void updateDrawState(TextPaint p) { p.setTypeface(f); }
    }

    /** Appends text with optional colour (0 keeps), font (null keeps) and size in sp (0 keeps). */
    static SpannableStringBuilder add(SpannableStringBuilder b, CharSequence s, int color, Typeface f, float sp) {
        int a = b.length();
        b.append(s);
        int z = b.length();
        if (color != 0) b.setSpan(new ForegroundColorSpan(color), a, z, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (f != null) b.setSpan(new FontSpan(f), a, z, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (sp > 0) b.setSpan(new AbsoluteSizeSpan(Math.round(sp), true), a, z, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    static SpannableStringBuilder rich() { return new SpannableStringBuilder(); }

    /* ---------------- layout ---------------- */

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBaselineAligned(false);
        return l;
    }

    /** Layout params; w and h are MATCH, WRAP or a size in dp. */
    static LinearLayout.LayoutParams lp(float w, float h) { return new LinearLayout.LayoutParams(size(w), size(h)); }

    static LinearLayout.LayoutParams lpw(float h) {
        return new LinearLayout.LayoutParams(0, size(h), 1f);
    }

    private static int size(float v) { return v == MATCH || v == WRAP ? (int) v : dp(v); }

    /** Margins in dp. */
    static <L extends ViewGroup.MarginLayoutParams> L m(L p, float l, float t, float r, float b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    static LinearLayout.LayoutParams block(float top, float bottom) { return m(lp(MATCH, WRAP), 0, top, 0, bottom); }

    static void pad(View v, float l, float t, float r, float b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }

    static View gap(Context c, float w, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h)));
        return v;
    }

    static View spring(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    static void click(View v, Runnable r) {
        v.setClickable(true);
        v.setFocusable(true);
        TypedValue tv = new TypedValue();
        v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(v.getContext().getDrawable(tv.resourceId));
        v.setOnClickListener(x -> r.run());
    }

    /* ---------------- controls ---------------- */

    static final int BTN = 0, BTN_ALT = 1, BTN_DANGER = 2;

    static W.T btn(Context c, String text, int style, Runnable onClick) {
        W.T v = t(c, up(text), DISPLAY7, 16, style == BTN ? DEEP : style == BTN_ALT ? INK : BAD, 1.55f);
        v.setLetterSpacing(.04f);
        if (style == BTN) pad(v, 18, 11, 18, 11); else pad(v, 19, 12, 19, 12);
        v.setGravity(Gravity.CENTER);
        W.Box b = new W.Box().radius(2);
        if (style == BTN) b.fill(SIGNAL); else b.border(style == BTN_ALT ? INK : BAD, 1);
        v.setBackground(b);
        if (onClick != null) click(v, onClick);
        return v;
    }

    static void disable(View v) {
        v.setEnabled(false);
        v.setClickable(false);
        v.setAlpha(.45f);
    }

    static W.T chip(Context c, String text, boolean pressed, Runnable onClick) {
        W.T v = t(c, text, BODY4, 14, pressed ? DEEP : INK, 1.55f);
        pad(v, 13, 6, 13, 6);
        v.setBackground(pressed ? new W.Box().fill(INK).border(INK, 1) : new W.Box().border(RULE, 1));
        v.setSelected(pressed);
        if (onClick != null) click(v, onClick);
        return v;
    }

    /** A multiple-choice option. state: 0 plain, 1 right, 2 wrong, 3 the chosen pager move. */
    static W.T opt(Context c, CharSequence text, int state, Runnable onClick) {
        W.T v = t(c, text, BODY4, 15, INK, 1.55f);
        pad(v, 13, 11, 13, 11);
        W.Box b = new W.Box();
        if (state == 1) b.fill(alpha(GOOD, .14f)).border(GOOD, 1);
        else if (state == 2) b.fill(alpha(BAD, .14f)).border(BAD, 1);
        else if (state == 3) b.fill(OPT).border(INK, 1);
        else b.fill(OPT).border(RULE, 1);
        v.setBackground(b);
        if (onClick != null) click(v, onClick);
        return v;
    }

    static EditText input(Context c, String hint, int inputType) {
        final EditText e = new EditText(c);
        e.setTypeface(BODY4);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setTextColor(INK);
        e.setHintTextColor(DIM);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTypeface(BODY4);
        e.setIncludeFontPadding(false);
        pad(e, 11, 12, 11, 12);
        e.setMinHeight(dp(49));
        final W.Box idle = new W.Box().fill(DEEP).border(RULE, 1), focus = new W.Box().fill(DEEP).border(SIGNAL, 2);
        e.setBackground(idle);
        e.setOnFocusChangeListener((v, has) -> e.setBackground(has ? focus : idle));
        return e;
    }

    static EditText area(Context c, String hint, String text) {
        EditText e = input(c, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setSingleLine(false);
        e.setMinHeight(dp(120));
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setLineSpacing(0, 1.14f);
        pad(e, 11, 11, 11, 11);
        e.setText(text);
        return e;
    }

    static LinearLayout field(Context c, String label, View input) {
        LinearLayout f = col(c);
        f.addView(label(c, label, MUTED, .1f), lp(MATCH, WRAP));
        f.addView(input, m(lp(MATCH, WRAP), 0, 4, 0, 0));
        f.setLayoutParams(block(12, 0));
        return f;
    }

    /** Card: raised panel with a thin rule border. */
    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(new W.Box().fill(CARD).border(RULE, 1));
        pad(l, 15, 13, 15, 13);
        l.setLayoutParams(block(0, 8));
        return l;
    }

    /** Panel: a card with drafting corner marks. */
    static LinearLayout panel(Context c) {
        LinearLayout l = col(c);
        l.setBackground(new W.Box().fill(CARD).border(RULE, 1).corners());
        pad(l, 17, 17, 17, 17);
        return l;
    }

    static W.T h3(Context c, String s) {
        W.T v = head(c, s, 20, INK);
        v.setLetterSpacing(.06f);
        return v;
    }

    static void ellipsize(W.T v) {
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
    }
}
