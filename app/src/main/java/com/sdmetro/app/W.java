package com.sdmetro.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** Custom-drawn pieces: boxes and rules, the metro rails and stations, pips, bars and the flow layout. */
final class W {
    private W() { }

    /** TextView whose lines are spaced like CSS line-height: the extra (or missing) space is split above and below each line. */
    static final class T extends TextView {
        private int shift;
        private boolean grown;

        T(Context c) {
            super(c);
            setIncludeFontPadding(false);
        }

        void lineHeight(float mult) {
            float extra = getTextSize() * mult - getPaint().getFontMetrics(null);
            setLineSpacing(extra, 1f);
            shift = Math.round(extra / 2f);
        }

        /** Tight headings: pull the neighbours in instead of clipping the glyphs. */
        @Override public void setLayoutParams(ViewGroup.LayoutParams p) {
            if (shift < 0 && p instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) p;
                mp.topMargin += shift;
                mp.bottomMargin += shift;
            }
            super.setLayoutParams(p);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            super.onMeasure(wSpec, hSpec);
            grown = shift > 0 && MeasureSpec.getMode(hSpec) != MeasureSpec.EXACTLY;
            if (grown) setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + 2 * shift);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (grown) {
                canvas.save();
                canvas.translate(0, shift);
                super.onDraw(canvas);
                canvas.restore();
            } else {
                super.onDraw(canvas);
            }
        }
    }

    /** Background: fill, border (solid or dashed), single-side rules, a thick left edge, a bottom accent bar, corner marks. */
    static final class Box extends Drawable {
        static final int TOP = 1, BOTTOM = 2, RIGHT = 4, LEFT = 8;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int fill, stroke, leftColor, barColor, sides, sideColor;
        private float sw, leftW, barH, sideW;
        private boolean dashed, sideDashed, corners;
        private float[] radii;

        Box fill(int color) { fill = color; return this; }
        Box border(int color, float dp) { stroke = color; sw = dp * Ui.den; return this; }
        Box dashed() { dashed = true; return this; }
        Box left(int color, float dp) { leftColor = color; leftW = dp * Ui.den; return this; }
        Box bar(int color) { barColor = color; barH = 3 * Ui.den; return this; }
        Box corners() { corners = true; return this; }
        Box side(int which, int color, float dp, boolean dash) { sides = which; sideColor = color; sideW = dp * Ui.den; sideDashed = dash; return this; }
        Box radius(float dp) { return radii(dp, dp, dp, dp); }
        Box radii(float tl, float tr, float br, float bl) {
            float d = Ui.den;
            radii = new float[]{tl * d, tl * d, tr * d, tr * d, br * d, br * d, bl * d, bl * d};
            return this;
        }

        private static DashPathEffect dash(float w) { return new DashPathEffect(new float[]{Math.max(3 * w, 3 * Ui.den), Math.max(2 * w, 3 * Ui.den)}, 0); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            RectF r = new RectF(b);
            p.setPathEffect(null);
            if (fill != 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(fill);
                if (radii != null) { path.rewind(); path.addRoundRect(r, radii, Path.Direction.CW); c.drawPath(path, p); }
                else c.drawRect(r, p);
            }
            if (barH > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(barColor);
                c.drawRect(r.left + sw, r.bottom - sw - barH, r.right - sw, r.bottom - sw, p);
            }
            if (stroke != 0 && sw > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setColor(stroke);
                if (dashed) p.setPathEffect(dash(sw));
                RectF in = new RectF(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2);
                path.rewind();
                if (radii != null) path.addRoundRect(in, radii, Path.Direction.CW); else path.addRect(in, Path.Direction.CW);
                c.drawPath(path, p);
                p.setPathEffect(null);
            }
            if (leftW > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(leftColor);
                c.drawRect(r.left, r.top, r.left + leftW, r.bottom, p);
            }
            if (sides != 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sideW);
                p.setColor(sideColor);
                if (sideDashed) p.setPathEffect(dash(sideW));
                float h = sideW / 2;
                path.rewind();
                if ((sides & TOP) != 0) { path.moveTo(r.left, r.top + h); path.lineTo(r.right, r.top + h); }
                if ((sides & BOTTOM) != 0) { path.moveTo(r.left, r.bottom - h); path.lineTo(r.right, r.bottom - h); }
                if ((sides & RIGHT) != 0) { path.moveTo(r.right - h, r.top); path.lineTo(r.right - h, r.bottom); }
                if ((sides & LEFT) != 0) { path.moveTo(r.left + h, r.top); path.lineTo(r.left + h, r.bottom); }
                c.drawPath(path, p);
                p.setPathEffect(null);
            }
            if (corners) {
                float len = 12 * Ui.den, w = 2 * Ui.den;
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.INK);
                c.drawRect(r.left, r.top, r.left + len, r.top + w, p);
                c.drawRect(r.left, r.top, r.left + w, r.top + len, p);
                c.drawRect(r.right - len, r.bottom - w, r.right, r.bottom, p);
                c.drawRect(r.right - w, r.bottom - len, r.right, r.bottom, p);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** The drafting-sheet background: a coarse and a fine grid that stay put while content scrolls. */
    static final class Grid extends Drawable {
        private final Paint p = new Paint();

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawColor(Ui.PAPER);
            float w = Math.max(1, Ui.den);
            lines(c, b, 24 * Ui.den, w, Ui.alpha(0xDCE6FF, .035f));
            lines(c, b, 120 * Ui.den, w, Ui.alpha(0xDCE6FF, .09f));
        }

        private void lines(Canvas c, Rect b, float step, float w, int color) {
            p.setColor(color);
            for (float x = b.left; x < b.right; x += step) c.drawRect(x, b.top, x + w, b.bottom, p);
            for (float y = b.top; y < b.bottom; y += step) c.drawRect(b.left, y, b.right, y + w, p);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Progress bar: a faint track and a coloured fill. */
    static final class Bar extends View {
        private final Paint p = new Paint();
        private final float frac;
        private final int color, track;

        Bar(Context c, double frac, int color) { this(c, frac, color, Ui.TRACK); }

        Bar(Context c, double frac, int color, int track) {
            super(c);
            this.frac = (float) Math.max(0, Math.min(1, frac));
            this.color = color;
            this.track = track;
        }

        @Override protected void onDraw(Canvas c) {
            if (track != 0) { p.setColor(track); c.drawRect(0, 0, getWidth(), getHeight(), p); }
            p.setColor(color);
            c.drawRect(0, 0, getWidth() * frac, getHeight(), p);
        }
    }

    /** Loading: a train running down the line. */
    static final class Track extends View {
        private final Paint p = new Paint();
        private ValueAnimator anim;
        private float pos = -1;

        Track(Context c) { super(c); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!ValueAnimator.areAnimatorsEnabled()) return;
            anim = ValueAnimator.ofFloat(-.3f, 1f);
            anim.setDuration(1400);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new PathInterpolator(.6f, 0f, .4f, 1f));
            anim.addUpdateListener(a -> { pos = (Float) a.getAnimatedValue(); invalidate(); });
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (anim != null) { anim.cancel(); anim = null; }
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas c) {
            p.setColor(Ui.TRACK);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            if (anim == null) {
                p.setColor(Ui.alpha(Ui.SIGNAL, .4f));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            } else {
                p.setColor(Ui.SIGNAL);
                c.drawRect(pos * getWidth(), 0, (pos + .3f) * getWidth(), getHeight(), p);
            }
        }
    }

    /** Diamond pips. Each colour is the fill of one pip; 0 draws it empty. */
    static final class Pips extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int[] colors;

        Pips(Context c, int... colors) {
            super(c);
            this.colors = colors;
        }

        static Pips of(Context c, Progress.Pips x) {
            Pips v = new Pips(c, x.e != 0 ? Ui.BLUE : 0, x.i != 0 ? Ui.SIGNAL : 0, x.t != 0 ? Ui.GOOD : 0);
            v.setContentDescription(x.count() + " of 3 modes done");
            return v;
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(Math.round((colors.length * 12 + 2) * Ui.den), Math.round(15 * Ui.den));
        }

        @Override protected void onDraw(Canvas c) {
            float d = Ui.den, half = 9 * d * 0.7071f, cy = getHeight() / 2f, sw = 1.5f * d, in = half - sw * 0.7071f;
            for (int i = 0; i < colors.length; i++) {
                float cx = (7 + i * 12) * d;
                boolean on = colors[i] != 0;
                float r = on ? half : in;
                path.rewind();
                path.moveTo(cx, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r, cy); path.close();
                if (on) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(colors[i]);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(sw);
                    p.setColor(Ui.DIM);
                }
                c.drawPath(path, p);
            }
        }
    }

    /** The hexagonal line roundel with the line number. */
    static final class Hex extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size, color, on;
        private final String text;
        private final float sp;

        Hex(Context c, int sizeDp, int color, int on, String text, float sp) {
            super(c);
            this.size = Ui.dp(sizeDp); this.color = color; this.on = on; this.text = text;
            this.sp = sp * c.getResources().getDisplayMetrics().scaledDensity;
        }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(size, size); }

        @Override protected void onDraw(Canvas c) {
            float s = size;
            path.rewind();
            path.moveTo(.25f * s, 0); path.lineTo(.75f * s, 0); path.lineTo(s, .5f * s);
            path.lineTo(.75f * s, s); path.lineTo(.25f * s, s); path.lineTo(0, .5f * s); path.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawPath(path, p);
            p.setColor(on);
            p.setTypeface(Ui.DISPLAY7);
            p.setTextSize(sp);
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(text, s / 2, s / 2 - (fm.ascent + fm.descent) / 2, p);
        }
    }

    /** One station of the map trunk: the thick rail and a ring whose centre fills with the line's progress. */
    static final class MapRail extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final boolean first, term;
        private final float frac;

        MapRail(Context c, int color, boolean first, boolean term, double frac) {
            super(c);
            this.color = color; this.first = first; this.term = term; this.frac = (float) frac;
        }

        @Override protected void onDraw(Canvas c) {
            float d = Ui.den, cx = 32 * d, cy = 30 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(29 * d, first ? cy : 0, 35 * d, term ? cy : getHeight(), p);
            p.setColor(Ui.PAPER);
            c.drawCircle(cx, cy, 16 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(color);
            c.drawCircle(cx, cy, 15 * d, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(term ? color : Ui.CARD);
            c.drawCircle(cx, cy, 10 * d, p);
            if (!term && frac > 0) {
                p.setColor(color);
                c.drawArc(cx - 10 * d, cy - 10 * d, cx + 10 * d, cy + 10 * d, -90, Math.round(frac * 360), true, p);
            }
        }
    }

    /** One stop of a line: the rail, a branch stub for nested stops, and a dot that fills by pips earned. */
    static final class StopRail extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color, depth, pips;
        private final boolean first, last;

        StopRail(Context c, int color, int depth, boolean first, boolean last, int pips) {
            super(c);
            this.color = color; this.depth = Math.min(depth, 3); this.first = first; this.last = last; this.pips = pips;
        }

        static int widthDp(int depth) { return depth <= 1 ? 44 : depth == 2 ? 70 : 96; }

        @Override protected void onDraw(Canvas c) {
            float d = Ui.den, y = 22 * d;
            p.setStyle(Paint.Style.FILL);
            p.setPathEffect(null);
            p.setColor(color);
            c.drawRect(19 * d, first ? y : 0, 23 * d, last ? y : getHeight(), p);
            if (depth == 2) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                p.setPathEffect(new DashPathEffect(new float[]{5 * d, 3 * d}, 0));
                c.drawLine(23 * d, y + d, 53 * d, y + d, p);
                p.setPathEffect(null);
            }
            float r = (depth == 1 ? 9 : 7) * d, cx = (depth == 1 ? 21 : depth == 2 ? 57 : 89) * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(pips >= 3 ? Ui.INK : Ui.CARD);
            c.drawCircle(cx, y, r, p);
            if (pips > 0 && pips < 3) {
                p.setColor(Ui.INK);
                c.drawArc(cx - r, y - r, cx + r, y + r, -90, pips * 120, true, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(Ui.INK);
            c.drawCircle(cx, y, r - d, p);
        }
    }

    /** A square checkbox drawn in the app's colours. */
    static final class Tick extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        boolean checked;

        Tick(Context c, boolean checked) { super(c); this.checked = checked; }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Ui.dp(18), Ui.dp(18)); }

        @Override protected void onDraw(Canvas c) {
            float d = Ui.den, s = getWidth();
            if (checked) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.SIGNAL);
                c.drawRect(0, 0, s, s, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.2f * d);
                p.setColor(Ui.DEEP);
                path.rewind();
                path.moveTo(.22f * s, .52f * s); path.lineTo(.43f * s, .73f * s); path.lineTo(.79f * s, .29f * s);
                c.drawPath(path, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.5f * d);
                p.setColor(Ui.DIM);
                c.drawRect(d, d, s - d, s - d, p);
            }
        }
    }

    /** Inline bordered tag inside running text, e.g. the "L9" interchange marks. */
    static final class Tag extends ReplacementSpan {
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float lead;
        private final int color;

        Tag(Context c, int color, float leadDp) {
            this.color = color;
            this.lead = leadDp * Ui.den;
            tp.setTypeface(Ui.MONO6);
            tp.setTextSize(10 * c.getResources().getDisplayMetrics().scaledDensity);
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(lead + tp.measureText(text, start, end) + 12 * Ui.den);
        }

        @Override public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            float d = Ui.den, w = tp.measureText(text, start, end) + 12 * d;
            Paint.FontMetrics fm = tp.getFontMetrics();
            float mid = y - paint.getTextSize() * .34f, h = (fm.descent - fm.ascent) + 2 * d;
            float l = x + lead, t = mid - h / 2;
            tp.setStyle(Paint.Style.STROKE);
            tp.setStrokeWidth(d);
            tp.setColor(color);
            c.drawRect(l + d / 2, t + d / 2, l + w - d / 2, t + h - d / 2, tp);
            tp.setStyle(Paint.Style.FILL);
            c.drawText(text, start, end, l + 6 * d, t + d - fm.ascent, tp);
        }
    }

    /** Lays children out left to right and wraps to new rows, like a wrapping flex row with gaps. */
    static final class Flow extends ViewGroup {
        private final int hGap, vGap;

        Flow(Context c, float hGapDp, float vGapDp) {
            super(c);
            hGap = Ui.dp(hGapDp);
            vGap = Ui.dp(vGapDp);
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int max = MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0, widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                if (ch.getVisibility() == GONE) continue;
                ch.measure(MeasureSpec.makeMeasureSpec(max, max == Integer.MAX_VALUE ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = ch.getMeasuredWidth();
                if (x > 0 && x + hGap + w > max) { y += rowH + vGap; x = 0; rowH = 0; }
                if (x > 0) x += hGap;
                x += w;
                rowH = Math.max(rowH, ch.getMeasuredHeight());
                widest = Math.max(widest, x);
            }
            int w = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(wSpec) : widest + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(w, y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l - getPaddingLeft() - getPaddingRight();
            int n = getChildCount(), i = 0, y = getPaddingTop();
            while (i < n) {
                int x = 0, rowH = 0, j = i;
                while (j < n) {
                    View ch = getChildAt(j);
                    if (ch.getVisibility() != GONE) {
                        int w = ch.getMeasuredWidth();
                        if (x > 0 && x + hGap + w > max) break;
                        if (x > 0) x += hGap;
                        x += w;
                        rowH = Math.max(rowH, ch.getMeasuredHeight());
                    }
                    j++;
                }
                x = getPaddingLeft();
                boolean any = false;
                for (int k = i; k < j; k++) {
                    View ch = getChildAt(k);
                    if (ch.getVisibility() == GONE) continue;
                    if (any) x += hGap;
                    int top = y + (rowH - ch.getMeasuredHeight()) / 2;
                    ch.layout(x, top, x + ch.getMeasuredWidth(), top + ch.getMeasuredHeight());
                    x += ch.getMeasuredWidth();
                    any = true;
                }
                y += rowH + vGap;
                i = j;
            }
        }
    }
}
