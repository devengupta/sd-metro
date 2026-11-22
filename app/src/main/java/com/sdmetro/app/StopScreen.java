package com.sdmetro.app;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A stop: its sign, neighbours, interchanges, and the Ride / Pager / Teach tabs in built-in and Claude-generated form. */
final class StopScreen {
    private StopScreen() { }

    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;
    private static final Random RND = new Random();

    static void build(final MainActivity a, LinearLayout root, final Roadmap.Stop t) {
        final App app = a.app;
        final Roadmap.Section s = app.rm.sec(t.sec);
        if (app.S.lastSec != t.sec || app.S.lastIdx != t.idx) {
            app.S.lastSec = t.sec;
            app.S.lastIdx = t.idx;
            app.persist();
        }
        final Roadmap.Stop prev = app.rm.stop(t.sec, t.idx - 1), next = app.rm.stop(t.sec, t.idx + 1);
        String mode = app.ui.mode.get(t.key);
        if (mode == null) mode = "ride";
        Progress.Pips pips = app.S.pipsOf(t.key);
        int c = s.phase.color;

        root.addView(Screens.back(a, "← Line " + Ui.pad2(s.n) + " · " + s.name, () -> a.up("line-" + s.n)), Ui.lp(WRAP, WRAP));

        LinearLayout sign = Ui.row(a);
        sign.setBackground(new W.Box().fill(Ui.DEEP).border(Ui.INK, 1));
        Ui.pad(sign, 17, 15, 17, 17);
        sign.addView(new W.Hex(a, 52, c, s.phase.on, String.valueOf(s.n), 24));
        LinearLayout st = Ui.col(a);
        W.T ln = Ui.mono(a, Ui.up((t.parent != null ? t.parent.name + " · " : "") + "Stop " + (t.idx + 1) + " of " + s.stops.size()), 11, Ui.MUTED);
        ln.setLetterSpacing(.08f);
        st.addView(ln);
        st.addView(Ui.head(a, t.name, Ui.clamp(26, a.wdp * .075f, 40), Ui.INK), Ui.m(Ui.lp(MATCH, WRAP), 0, 3, 0, 0));
        sign.addView(st, Ui.m(Ui.lpw(WRAP), 14, 0, 0, 0));
        root.addView(sign, Ui.block(6, 10));

        LinearLayout adj = Ui.row(a);
        adj.addView(adjCell(a, "← Previous", prev, false), Ui.lpw(WRAP));
        adj.addView(adjCell(a, "Next →", next, true), Ui.m(Ui.lpw(WRAP), 8, 0, 0, 0));
        root.addView(adj, Ui.block(0, 10));

        List<Roadmap.Loc> ox = app.rm.others(t);
        if (!ox.isEmpty()) {
            W.Flow ich = new W.Flow(a, 6, 6);
            ich.addView(Ui.note(a, "Interchange with"));
            for (final Roadmap.Loc o : ox) {
                W.T l = Ui.mono(a, "Line " + o.sec + " · " + app.rm.sec(o.sec).name, 12, Ui.SIGNAL);
                l.setBackground(new W.Box().border(Ui.SIGNAL, 1));
                Ui.pad(l, 11, 3, 11, 3);
                Ui.click(l, () -> a.go("stop-" + o.sec + "-" + o.idx));
                ich.addView(l);
            }
            root.addView(ich, Ui.block(-2, 12));
        }

        String[][] modes = {
            {"ride", "Ride", "Understand it, then prove it"}, {"pager", "Pager", "Fix a 2:47 AM incident"}, {"teach", "Teach", "Explain it to a new joiner"}
        };
        int[] pipColor = {pips.e != 0 ? Ui.BLUE : 0, pips.i != 0 ? Ui.SIGNAL : 0, pips.t != 0 ? Ui.GOOD : 0};
        LinearLayout tabs = Ui.row(a);
        tabs.setGravity(Gravity.TOP);
        for (int i = 0; i < 3; i++) {
            final String key = modes[i][0];
            boolean on = key.equals(mode);
            LinearLayout tab = Ui.col(a);
            tab.setBackground(on ? new W.Box().fill(Ui.DEEP).border(Ui.SIGNAL, 1).bar(Ui.SIGNAL) : new W.Box().fill(Ui.CARD).border(Ui.RULE, 1));
            Ui.pad(tab, 11, 11, 6, 11);
            LinearLayout top = Ui.row(a);
            top.setGravity(Gravity.TOP);
            top.addView(Ui.t(a, Ui.up(modes[i][1]), Ui.DISPLAY7, 20, Ui.INK, 1f), Ui.lpw(WRAP));
            top.addView(new W.Pips(a, pipColor[i]));
            tab.addView(top);
            if (a.wdp > 480) tab.addView(Ui.t(a, modes[i][2], Ui.BODY4, 12, Ui.MUTED, 1.3f), Ui.m(Ui.lp(MATCH, WRAP), 0, 4, 5, 0));
            tab.setSelected(on);
            tab.setContentDescription(modes[i][1] + ". " + modes[i][2]);
            Ui.click(tab, () -> { a.hideKeyboard(); app.ui.mode.put(t.key, key); a.render(); });
            tabs.addView(tab, Ui.m(Ui.lpw(MATCH), i == 0 ? 0 : 8, 0, 0, 0));
        }
        root.addView(tabs, Ui.block(0, 12));

        LinearLayout panel = Ui.panel(a);
        root.addView(panel, Ui.lp(MATCH, WRAP));
        if ("pager".equals(mode)) { if (live(app, "pager", t)) gPager(a, panel, t); else sPager(a, panel, t); }
        else if ("teach".equals(mode)) { if (live(app, "teach", t)) gTeach(a, panel, t); else sTeach(a, panel, t); }
        else { if (live(app, "ride", t)) gRide(a, panel, t, c); else sRide(a, panel, t, c); }
    }

    private static View adjCell(final MainActivity a, String label, final Roadmap.Stop to, boolean right) {
        LinearLayout cell = Ui.col(a);
        cell.setBackground(new W.Box().fill(Ui.CARD).border(Ui.RULE, 1));
        Ui.pad(cell, 11, 9, 11, 9);
        W.T l = Ui.label(a, label, Ui.MUTED, .08f);
        W.T nm = Ui.t(a, to != null ? to.name : "", Ui.BODY7, 13, Ui.INK, 1.55f);
        Ui.ellipsize(nm);
        if (right) { l.setGravity(Gravity.END); nm.setGravity(Gravity.END); }
        cell.addView(l, Ui.lp(MATCH, WRAP));
        cell.addView(nm, Ui.lp(MATCH, WRAP));
        if (to == null) cell.setVisibility(View.INVISIBLE);
        else Ui.click(cell, () -> a.replace("stop-" + to.sec + "-" + to.idx));
        return cell;
    }

    /* ---------------- shared pieces ---------------- */

    private static boolean live(App app, String mode, Roadmap.Stop t) { return app.hasAI() && app.ui.live.containsKey(mode + ":" + t.key); }

    private static void liveBtn(final MainActivity a, LinearLayout p, final String mode, final Roadmap.Stop t) {
        if (!a.app.hasAI()) return;
        LinearLayout box = Ui.col(a);
        box.setBackground(new W.Box().side(W.Box.TOP, Ui.RULE, 1, true));
        Ui.pad(box, 0, 12, 0, 0);
        box.addView(Ui.btn(a, "Generate a fresh version with Claude", Ui.BTN_ALT, () -> { a.app.ui.live.put(mode + ":" + t.key, true); a.render(); }), Ui.lp(WRAP, WRAP));
        p.addView(box, Ui.block(16, 0));
    }

    private static void noContent(MainActivity a, LinearLayout p, String title) {
        p.addView(Ui.h3(a, title), Ui.block(0, 6));
        p.addView(Ui.t(a, "This stop has no built-in content yet.", Ui.BODY4, 16, Ui.MUTED, 1.55f));
    }

    private static void titleRow(MainActivity a, LinearLayout p, String title, CharSequence right, boolean mono) {
        LinearLayout r = Ui.row(a);
        r.addView(Ui.h3(a, title), Ui.lpw(WRAP));
        W.T n = mono ? Ui.mono(a, right, 13, Ui.MUTED) : Ui.note(a, right);
        r.addView(n, Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
        p.addView(r, Ui.block(0, 6));
    }

    private static W.T tagline(MainActivity a, String s) {
        W.T v = Ui.t(a, Ui.up(s), Ui.DISPLAY6, 24, Ui.SIGNAL, 1.15f);
        v.setLetterSpacing(.02f);
        return v;
    }

    private static void prose(MainActivity a, LinearLayout p, String[] paras) {
        for (String s : paras) p.addView(Ui.body(a, s), Ui.block(0, 10));
    }

    private static View fact(MainActivity a, String label, String text, int color) {
        LinearLayout f = Ui.col(a);
        f.setBackground(new W.Box().left(color, 3));
        Ui.pad(f, 13, 4, 0, 4);
        W.T l = Ui.t(a, Ui.up(label), Ui.MONO5, 10, Ui.MUTED, 1.55f);
        l.setLetterSpacing(.1f);
        f.addView(l);
        f.addView(Ui.t(a, text, Ui.BODY4, 14, Ui.INK, 1.55f));
        return f;
    }

    /** Pocket card. kind: 0 plain (dashed yellow), 1 win (green), 2 loss (red). */
    private static View pocket(MainActivity a, String label, String text, int kind) {
        LinearLayout f = Ui.col(a);
        int col = kind == 1 ? Ui.GOOD : kind == 2 ? Ui.BAD : Ui.SIGNAL;
        W.Box b = new W.Box().fill(Ui.DEEP).border(col, 1);
        if (kind == 0) b.dashed();
        f.setBackground(b);
        Ui.pad(f, 15, 13, 15, 13);
        W.T l = Ui.t(a, Ui.up(label), Ui.MONO5, 10, col, 1.55f);
        l.setLetterSpacing(.12f);
        f.addView(l);
        f.addView(Ui.t(a, text, Ui.BODY4, 17, Ui.INK, 1.55f), Ui.m(Ui.lp(MATCH, WRAP), 0, 4, 0, 0));
        f.setLayoutParams(Ui.block(0, 12));
        return f;
    }

    /** The block that holds a question: dashed rule above. */
    private static LinearLayout qBox(MainActivity a, float top) {
        LinearLayout q = Ui.col(a);
        q.setBackground(new W.Box().side(W.Box.TOP, Ui.RULE, 1, true));
        Ui.pad(q, 0, 12, 0, 0);
        q.setLayoutParams(Ui.block(top, 0));
        return q;
    }

    private static W.T why(MainActivity a, CharSequence s, int color) {
        return Ui.t(a, s, Ui.BODY4, 14, color, 1.55f);
    }

    /** The outcome of a pager move. */
    private static View outcome(MainActivity a, String quality, String what, String lesson) {
        int col = "strong".equals(quality) ? Ui.GOOD : "wrong".equals(quality) ? Ui.BAD : Ui.WARN;
        LinearLayout o = Ui.col(a);
        o.setBackground(new W.Box().fill(Ui.alpha(col, .1f)).border(col, 1));
        Ui.pad(o, 13, 11, 13, 11);
        W.T q = Ui.t(a, Ui.up(quality), Ui.MONO6, 11, col, 1.55f);
        q.setLetterSpacing(.1f);
        o.addView(q);
        o.addView(Ui.body(a, what));
        SpannableStringBuilder b = Ui.rich();
        Ui.add(b, "Lesson:", 0, Ui.BODY7, 0).append(" ").append(lesson);
        o.addView(why(a, b, Ui.INK), Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
        o.setLayoutParams(Ui.block(12, 12));
        return o;
    }

    private static View pagerBox(MainActivity a, String left, String right, String alert, List<String[]> metrics) {
        LinearLayout box = Ui.col(a);
        box.setBackground(new W.Box().fill(0xFF07122F).border(Ui.BAD, 1).left(Ui.BAD, 5));
        Ui.pad(box, 19, 15, 15, 15);
        LinearLayout top = Ui.row(a);
        W.T l = Ui.mono(a, Ui.up(left), 11, Ui.DIM);
        l.setLetterSpacing(.06f);
        W.T r = Ui.mono(a, Ui.up(right), 11, Ui.DIM);
        r.setLetterSpacing(.06f);
        top.addView(l, Ui.lpw(WRAP));
        top.addView(r, Ui.m(Ui.lp(WRAP, WRAP), 8, 0, 0, 0));
        box.addView(top);
        box.addView(Ui.t(a, alert, Ui.MONO6, 16, 0xFFFFFFFF, 1.55f), Ui.block(6, 6));
        if (metrics != null && !metrics.isEmpty()) {
            W.Flow fl = new W.Flow(a, 6, 6);
            for (String[] m : metrics) {
                LinearLayout mm = Ui.col(a);
                mm.setBackground(new W.Box().border(Ui.alpha(0xDCE6FF, .2f), 1));
                Ui.pad(mm, 9, 5, 9, 5);
                mm.addView(Ui.mono(a, m[0], 11, 0xFFE8EEFF));
                int vc = "bad".equals(m[2]) ? Ui.BAD : "warn".equals(m[2]) ? Ui.WARN : "ok".equals(m[2]) ? Ui.GOOD : 0xFFE8EEFF;
                mm.addView(Ui.t(a, m[1], Ui.MONO6, 15, vc, 1.55f));
                fl.addView(mm);
            }
            box.addView(fl, Ui.block(4, 0));
        }
        return box;
    }

    private static View bubble(MainActivity a, String who, String text, boolean you) {
        LinearLayout b = Ui.col(a);
        b.setBackground(you ? new W.Box().fill(Ui.CARD).border(Ui.RULE, 1).radii(12, 12, 0, 12) : new W.Box().fill(Ui.DEEP).border(Ui.INK, 1).radii(12, 12, 12, 0));
        Ui.pad(b, 15, 11, 15, 11);
        W.T w = Ui.mono(a, Ui.up(who), 11, Ui.SIGNAL);
        w.setLetterSpacing(.08f);
        b.addView(w);
        b.addView(Ui.body(a, text));
        b.setLayoutParams(Ui.block(0, 10));
        return b;
    }

    private static View scoreRow(MainActivity a, int score) {
        LinearLayout r = Ui.row(a);
        r.addView(Ui.bold(a, "Score"));
        for (int i = 0; i < 3; i++) {
            View v = new View(a);
            v.setBackgroundColor(i < score ? Ui.GOOD : Ui.TRACK);
            r.addView(v, Ui.m(Ui.lp(40, 10), i == 0 ? 10 : 6, 0, 0, 0));
        }
        r.addView(Ui.mono(a, score + "/3", 16, Ui.INK), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
        return r;
    }

    /** A fold: bold summary with a marker, content shown when open. */
    private static void details(final MainActivity a, LinearLayout p, String summary, boolean open, final Runnable toggle, View content) {
        W.T sm = Ui.bold(a, (open ? "▼ " : "▶ ") + summary);
        Ui.click(sm, toggle);
        p.addView(sm, Ui.block(10, 0));
        if (open) p.addView(content, Ui.block(8, 0));
    }

    /** The failure of the last Claude request; "Profile" opens the Profile tab where that helps. */
    private static void err(final MainActivity a, LinearLayout p, UiState.Live st) {
        if (!st.hasErr()) return;
        String msg = Gen.errText(st.errCode, st.errMsg);
        SpannableStringBuilder b = new SpannableStringBuilder(msg);
        int at = msg.lastIndexOf("Profile");
        W.T box = Screens.errBox(a, b);
        if (Gen.linksToProfile(st.errCode) && at >= 0) {
            b.setSpan(new ClickableSpan() {
                @Override public void onClick(View v) { a.tab("profile"); }
                @Override public void updateDrawState(TextPaint ds) { ds.setUnderlineText(true); }
            }, at, at + 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            box.setText(b);
            box.setMovementMethod(LinkMovementMethod.getInstance());
            box.setHighlightColor(0);
        }
        p.addView(box, Ui.block(10, 0));
    }

    private static LinearLayout.LayoutParams left(float top) { return Ui.m(Ui.lp(WRAP, WRAP), 0, top, 0, 0); }

    /* ---------------- built-in Ride ---------------- */

    private static void sRide(final MainActivity a, LinearLayout p, final Roadmap.Stop t, int c) {
        final App app = a.app;
        final Content.Entry e = app.content.get(t.key);
        if (e == null) { noContent(a, p, "Ride"); return; }
        UiState.Quiz q0 = app.ui.sRide.get(t.key);
        if (q0 == null) { q0 = new UiState.Quiz(); app.ui.sRide.put(t.key, q0); }
        final UiState.Quiz st = q0;
        if (st.order == null) st.order = Arcade.shuffled(4, RND);
        int r = 0;
        for (int i = 0; i < 4; i++) if (st.order[i] == 0) r = i;
        final int right = r;
        p.addView(Ui.h3(a, "Ride"), Ui.block(0, 6));
        p.addView(tagline(a, e.t), Ui.block(0, 10));
        prose(a, p, e.e);
        p.addView(fact(a, "Interview trap", e.p, c), Ui.block(2, 12));
        p.addView(pocket(a, "Pocket card", e.k, 0));
        LinearLayout q = qBox(a, 2);
        q.addView(Ui.bold(a, "Quick check. " + e.q[0]), Ui.block(0, 8));
        for (int oi = 0; oi < 4; oi++) {
            final int pick = oi;
            int state = st.pick < 0 ? 0 : oi == right ? 1 : oi == st.pick ? 2 : 0;
            q.addView(Ui.opt(a, e.q[1 + st.order[oi]], state, st.pick >= 0 ? null : () -> {
                st.pick = pick;
                if (pick == right) { app.S.notes.put(t.key, e.k); app.award(t.key, 'e', 20, "Ride complete"); }
                else app.toast("Not quite. Read the why, then try again.");
                a.render();
            }), Ui.block(oi == 0 ? 0 : 6, 0));
        }
        if (st.pick >= 0) {
            q.addView(why(a, (st.pick == right ? "Correct." : "Not quite.") + " " + e.q[5], Ui.MUTED), Ui.block(6, 0));
            if (st.pick != right) q.addView(Ui.btn(a, "Try again", Ui.BTN_ALT, () -> { st.order = Arcade.shuffled(4, RND); st.pick = -1; a.render(); }), left(10));
        }
        p.addView(q);
        liveBtn(a, p, "ride", t);
    }

    /* ---------------- built-in Pager ---------------- */

    private static void sPager(final MainActivity a, LinearLayout p, final Roadmap.Stop t) {
        final App app = a.app;
        final Content.Entry e = app.content.get(t.key);
        if (e == null) { noContent(a, p, "Pager"); return; }
        UiState.Quiz q0 = app.ui.sPager.get(t.key);
        if (q0 == null) { q0 = new UiState.Quiz(); app.ui.sPager.put(t.key, q0); }
        final UiState.Quiz st = q0;
        if (st.order == null) st.order = Arcade.shuffled(3, RND);
        final String[][] ch = new String[3][];
        for (int i = 0; i < 3; i++) ch[i] = Gen.builtinMove(e, st.order[i]);
        titleRow(a, p, "Pager", Gen.companyFor(t.key), true);
        p.addView(pagerBox(a, "prod · you are on call", "02:47 IST", e.i[0], null));
        p.addView(Ui.t(a, e.i[1], Ui.BODY4, 15, Ui.INK, 1.55f), Ui.block(10, 10));
        if (st.pick < 0) {
            p.addView(Ui.note(a, "Pick your move. One fixes it, one makes it worse, one only helps a little."), Ui.block(0, 6));
            for (int i = 0; i < 3; i++) {
                final int pick = i;
                p.addView(Ui.opt(a, ch[i][0], 0, () -> {
                    st.pick = pick;
                    if ("strong".equals(ch[pick][2])) app.award(t.key, 'i', 40, "Incident resolved");
                    else app.toast("Postmortem time. Replay to earn the pip.");
                    a.render();
                }), Ui.block(i == 0 ? 0 : 6, 0));
            }
        } else {
            String[] x = ch[st.pick];
            p.addView(Ui.opt(a, "You: " + x[0], 3, null));
            p.addView(outcome(a, x[2], x[1], e.i[5]));
            if ("strong".equals(x[2])) {
                p.addView(pocket(a, "Incident resolved", "You can go back to sleep.", 1));
            } else {
                p.addView(fact(a, "The better move", e.i[2], Ui.GOOD));
                p.addView(Ui.btn(a, "Replay the incident", Ui.BTN_ALT, () -> { st.order = Arcade.shuffled(3, RND); st.pick = -1; a.render(); }), left(12));
            }
        }
        liveBtn(a, p, "pager", t);
    }

    /* ---------------- built-in Teach ---------------- */

    private static void sTeach(final MainActivity a, LinearLayout p, final Roadmap.Stop t) {
        final App app = a.app;
        final Content.Entry e = app.content.get(t.key);
        if (e == null) { noContent(a, p, "Teach"); return; }
        UiState.BTeach s0 = app.ui.sTeach.get(t.key);
        if (s0 == null) { s0 = new UiState.BTeach(); app.ui.sTeach.put(t.key, s0); }
        final UiState.BTeach st = s0;
        p.addView(Ui.h3(a, "Teach"), Ui.block(0, 10));
        p.addView(bubble(a, "Ravi · joined 6 months ago", e.a[0], false));
        if (!st.shown) {
            p.addView(Ui.note(a, "Explain it in your own words. Type it, or say it out loud, before you look at the key points."));
            EditText ans = Ui.area(a, "Your explanation", st.draft);
            ans.setId(R.id.answer);
            Screens.watch(ans, s -> st.draft = s);
            p.addView(ans, Ui.lp(MATCH, WRAP));
            p.addView(Ui.btn(a, "Compare with the key points", Ui.BTN, () -> { a.hideKeyboard(); st.shown = true; a.render(); }), left(10));
        } else {
            if (!st.draft.trim().isEmpty()) p.addView(bubble(a, "You", st.draft, true));
            p.addView(Ui.note(a, "Tick only what your explanation actually covered."), Ui.block(2, 0));
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                LinearLayout kp = Ui.row(a);
                kp.setGravity(Gravity.TOP);
                kp.setBackground(new W.Box().fill(Ui.OPT).border(Ui.RULE, 1));
                Ui.pad(kp, 11, 10, 11, 10);
                final W.Tick tick = new W.Tick(a, st.ticks[i]);
                kp.addView(tick, Ui.m(Ui.lp(WRAP, WRAP), 0, 4, 0, 0));
                kp.addView(Ui.body(a, e.a[1 + i]), Ui.m(Ui.lpw(WRAP), 10, 0, 0, 0));
                if (st.score < 0) Ui.click(kp, () -> { st.ticks[idx] = !st.ticks[idx]; tick.checked = st.ticks[idx]; tick.invalidate(); });
                else tick.setAlpha(.6f);
                kp.setContentDescription(e.a[1 + i]);
                p.addView(kp, Ui.block(6, 0));
            }
            LinearLayout model = Ui.col(a);
            prose(a, model, e.e);
            details(a, p, "Model answer", st.score >= 0 || st.open, () -> { st.open = !st.open; a.render(); }, model);
            if (st.score < 0) {
                p.addView(Ui.btn(a, "Score my explanation", Ui.BTN, () -> {
                    int n = 0;
                    for (boolean b : st.ticks) if (b) n++;
                    st.score = n;
                    if (n >= 2) app.award(t.key, 't', 60, "Ravi gets it"); else app.toast("Cover at least 2 of 3 to earn the pip.");
                    a.render();
                }), left(12));
            } else {
                p.addView(scoreRow(a, st.score), Ui.block(12, 10));
                p.addView(bubble(a, "Ravi", st.score == 3 ? "That makes complete sense now. Thanks!" : st.score == 2 ? "Got it, mostly. I will read up on the part you skipped." : "Hmm, I am still a bit lost. Can we go over it again?", false));
                p.addView(Ui.btn(a, "Explain it again", Ui.BTN_ALT, () -> { app.ui.sTeach.remove(t.key); a.render(); }), left(0));
            }
        }
        liveBtn(a, p, "teach", t);
    }

    /* ---------------- generated Ride ---------------- */

    private static View lensChips(final MainActivity a, final Runnable changed) {
        final UiState ui = a.app.ui;
        W.Flow f = new W.Flow(a, 6, 6);
        for (final String[] l : Gen.LENSES) {
            f.addView(Ui.chip(a, l[1], l[0].equals(ui.lens), () -> { ui.lens = l[0]; changed.run(); a.render(); }));
        }
        return f;
    }

    private static void gRide(final MainActivity a, LinearLayout p, final Roadmap.Stop t, int c) {
        final App app = a.app;
        final UiState ui = app.ui;
        UiState.GRide s0 = ui.ride.get(t.key);
        if (s0 == null) { s0 = new UiState.GRide(); ui.ride.put(t.key, s0); }
        final UiState.GRide st = s0;
        if (st.data == null && !st.loading) {
            String ck = App.cacheKey(t.key, ui.lens), cached = app.cache.get(ck);
            if (cached != null) {
                try { st.data = Gen.ride(new JSONObject(cached)); st.lens = ui.lens; }
                catch (Exception bad) { app.cache.remove(ck); }
            }
        }
        if (st.loading) {
            p.addView(Ui.h3(a, "Ride"), Ui.block(0, 6));
            p.addView(Screens.loading(a, "Train approaching the platform…"));
            return;
        }
        if (st.data == null) {
            p.addView(Ui.h3(a, "Ride"), Ui.block(0, 6));
            p.addView(Ui.t(a, "Pick the world you want this explained through, then board. You get the idea, the real mechanics, the interview trap, a pocket card and a two-question check.", Ui.BODY4, 16, Ui.MUTED, 1.55f));
            p.addView(lensChips(a, st::clearErr), Ui.block(10, 10));
            p.addView(Ui.btn(a, "Board the train", Ui.BTN, () -> rideFetch(a, t)), left(0));
            err(a, p, st);
            return;
        }
        final Gen.Ride d = st.data;
        titleRow(a, p, "Ride", "Lens: " + Gen.lensName(st.lens), false);
        p.addView(tagline(a, d.tagline), Ui.block(0, 10));
        prose(a, p, d.explain);
        if (d.map.length > 0) {
            LinearLayout amap = Ui.col(a);
            amap.setBackground(new W.Box().border(Ui.RULE, 1).dashed());
            Ui.pad(amap, 11, 11, 11, 11);
            LinearLayout hr = Ui.row(a);
            hr.addView(Ui.label(a, "In the analogy", Ui.MUTED, .1f), Ui.lpw(WRAP));
            hr.addView(Ui.gap(a, 30, 1));
            hr.addView(Ui.label(a, "In the system", Ui.MUTED, .1f), Ui.lpw(WRAP));
            amap.addView(hr);
            for (String[] row : d.map) {
                LinearLayout r = Ui.row(a);
                r.addView(Ui.t(a, row[0], Ui.BODY4, 14, Ui.INK, 1.55f), Ui.lpw(WRAP));
                W.T ar = Ui.mono(a, "→", 14, Ui.SIGNAL);
                ar.setGravity(Gravity.CENTER);
                r.addView(ar, Ui.lp(30, WRAP));
                r.addView(Ui.t(a, row[1], Ui.BODY4, 14, Ui.INK, 1.55f), Ui.lpw(WRAP));
                amap.addView(r, Ui.block(4, 0));
            }
            p.addView(amap, Ui.block(0, 14));
        }
        LinearLayout facts = Ui.col(a);
        String[][] fs = {{"Rule of thumb", d.ruleOfThumb}, {"Seen in the wild", d.seenIn}, {"Interview trap", d.trap}};
        for (String[] f : fs) if (!f[1].isEmpty()) facts.addView(fact(a, f[0], f[1], c), Ui.block(facts.getChildCount() == 0 ? 0 : 8, 0));
        p.addView(facts, Ui.block(d.map.length > 0 ? 0 : 2, 12));
        p.addView(pocket(a, "Pocket card", d.remember, 0));
        p.addView(Ui.bold(a, "Quick check"));
        final int n = d.check.length;
        for (int qi = 0; qi < n; qi++) {
            final int fq = qi;
            final Gen.Check q = d.check[qi];
            final Integer ans = st.answers.get(qi);
            LinearLayout qb = qBox(a, 14);
            qb.addView(Ui.bold(a, (qi + 1) + ". " + q.q), Ui.block(0, 8));
            for (int oi = 0; oi < q.options.length; oi++) {
                final int fo = oi;
                int state = ans == null ? 0 : oi == q.answer ? 1 : oi == ans ? 2 : 0;
                qb.addView(Ui.opt(a, q.options[oi], state, ans != null ? null : () -> {
                    st.answers.put(fq, fo);
                    if (st.answers.size() == n) {
                        int right = 0;
                        for (int i = 0; i < n; i++) { Integer x = st.answers.get(i); if (x != null && x == d.check[i].answer) right++; }
                        if (right >= (n + 1) / 2) app.award(t.key, 'e', 20, "Ride complete");
                        else app.toast(right + "/" + n + ". Ride again to earn the pip.");
                    }
                    a.render();
                }), Ui.block(oi == 0 ? 0 : 6, 0));
            }
            if (ans != null) qb.addView(why(a, (ans == q.answer ? "Correct." : "Not quite.") + " " + q.why, Ui.MUTED), Ui.block(6, 0));
            p.addView(qb);
        }
        p.addView(lensChips(a, () -> { }), Ui.block(16, 10));
        p.addView(Ui.btn(a, "Ride again with this lens", Ui.BTN_ALT, () -> { app.cache.remove(App.cacheKey(t.key, ui.lens)); rideFetch(a, t); }), left(0));
        err(a, p, st);
    }

    private static void rideFetch(final MainActivity a, final Roadmap.Stop t) {
        final App app = a.app;
        final UiState.GRide st = app.ui.ride.get(t.key);
        if (st == null || st.loading) return;
        st.loading = true;
        st.clearErr();
        a.render();
        final String lens = app.ui.lens;
        app.ask(Gen.ridePrompt(Gen.ctxOf(t, app.rm), lens), false, new App.Reply() {
            @Override public void ok(JSONObject json) {
                try {
                    // Shape is checked before the lesson is cached or drawn.
                    Gen.Ride d = Gen.ride(json);
                    st.data = d;
                    st.lens = lens;
                    st.answers.clear();
                    app.cache.put(App.cacheKey(t.key, lens), d.json);
                    if (!d.remember.isEmpty()) {
                        app.S.notes.put(t.key, d.remember.length() > 220 ? d.remember.substring(0, 220) : d.remember);
                        app.save();
                    }
                } catch (Gen.Fail f) {
                    st.fail(f);
                }
                done();
            }
            @Override public void fail(Gen.Fail f) { st.fail(f); done(); }
            private void done() { st.loading = false; refresh(app, t); }
        });
    }

    /** Redraws when the stop a reply belongs to is still on screen. */
    private static void refresh(App app, Roadmap.Stop t) {
        if (app.host instanceof MainActivity) {
            MainActivity now = (MainActivity) app.host;
            if (now.showing(t)) now.render();
        }
    }

    /* ---------------- generated Pager ---------------- */

    private static void gPager(final MainActivity a, LinearLayout p, final Roadmap.Stop t) {
        final App app = a.app;
        UiState.GPager s0 = app.ui.pager.get(t.key);
        if (s0 == null) { s0 = new UiState.GPager(); app.ui.pager.put(t.key, s0); }
        final UiState.GPager st = s0;
        if (st.loading && st.rounds == null) {
            p.addView(Ui.h3(a, "Pager"), Ui.block(0, 6));
            p.addView(Screens.loading(a, "Paging the on-call engineer…"));
            return;
        }
        if (st.rounds == null) {
            p.addView(Ui.h3(a, "Pager"), Ui.block(0, 6));
            p.addView(Ui.t(a, "It's 2:47 AM. You're on call at a fictional company and something is on fire. The concept is never named in the alert. Read the dashboards, pick a move, live with the consequences. Three rounds.", Ui.BODY4, 16, Ui.MUTED, 1.55f));
            p.addView(Ui.btn(a, "Start the shift", Ui.BTN, () -> pagerStart(a, t)), left(12));
            err(a, p, st);
            return;
        }
        final ArrayList<Gen.Round> R = st.rounds;
        Gen.Round cur = R.get(R.size() - 1);
        titleRow(a, p, "Pager", st.company == null ? "" : st.company, true);
        LinearLayout bars = Ui.row(a);
        int doneCount = R.size() - (cur.chosen < 0 ? 1 : 0);
        for (int i = 0; i < 3; i++) {
            View v = new View(a);
            v.setBackgroundColor(i < doneCount ? Ui.SIGNAL : Ui.TRACK);
            bars.addView(v, Ui.m(Ui.lpw(4), i == 0 ? 0 : 4, 0, 0, 0));
        }
        p.addView(bars, Ui.block(0, 10));
        for (int ri = 0; ri < R.size(); ri++) {
            final Gen.Round r = R.get(ri);
            String service = st.service == null || st.service.isEmpty() ? "prod" : st.service;
            p.addView(pagerBox(a, service + " · round " + (ri + 1), "02:" + Ui.pad2(47 + ri * 6) + " IST", r.alert, r.metrics));
            p.addView(Ui.t(a, r.context, Ui.BODY4, 15, Ui.INK, 1.55f), Ui.block(10, 10));
            if (r.chosen < 0) {
                if (st.loading) p.addView(Screens.loading(a, "Applying your fix to production…"));
                else for (int ci = 0; ci < r.choices.size(); ci++) {
                    final int pick = ci;
                    p.addView(Ui.opt(a, r.choices.get(ci), 0, () -> pagerChoose(a, t, pick)), Ui.block(ci == 0 ? 0 : 6, 0));
                }
            } else {
                p.addView(Ui.opt(a, "You: " + r.choices.get(r.chosen), 3, null));
                if (r.quality != null) p.addView(outcome(a, r.quality, r.outcome, r.lesson));
                else if (st.loading) p.addView(Screens.loading(a, "Applying your fix to production…"));
            }
        }
        if (st.debrief != null) {
            Gen.Debrief d = st.debrief;
            p.addView(pocket(a, d.survived ? "Incident resolved" : "Postmortem", d.summary, d.survived ? 1 : 2));
            p.addView(fact(a, "The better move", d.betterMove, app.rm.sec(t.sec).phase.color));
            p.addView(Ui.btn(a, "New incident", Ui.BTN_ALT, () -> { app.ui.pager.put(t.key, new UiState.GPager()); pagerStart(a, t); }), left(12));
        }
        err(a, p, st);
    }

    private static void pagerStart(final MainActivity a, final Roadmap.Stop t) {
        final App app = a.app;
        final UiState.GPager st = app.ui.pager.get(t.key);
        if (st == null || st.loading) return;
        st.loading = true;
        st.clearErr();
        a.render();
        app.ask(Gen.pagerStartPrompt(Gen.ctxOf(t, app.rm), t.name), false, new App.Reply() {
            @Override public void ok(JSONObject json) {
                try {
                    Gen.PagerStart d = Gen.pagerStart(json);
                    st.company = d.company;
                    st.service = d.service;
                    st.rounds = new ArrayList<>();
                    st.rounds.add(d.round);
                } catch (Gen.Fail f) {
                    st.fail(f);
                }
                done();
            }
            @Override public void fail(Gen.Fail f) { st.fail(f); done(); }
            private void done() { st.loading = false; refresh(app, t); }
        });
    }

    private static void pagerChoose(final MainActivity a, final Roadmap.Stop t, int ci) {
        final App app = a.app;
        final UiState.GPager st = app.ui.pager.get(t.key);
        if (st == null || st.loading || st.rounds == null) return;
        final ArrayList<Gen.Round> R = st.rounds;
        final Gen.Round r = R.get(R.size() - 1);
        if (ci < 0 || ci >= r.choices.size()) return;
        r.chosen = ci;
        st.loading = true;
        st.clearErr();
        a.render();
        final int n = R.size();
        String prompt = Gen.pagerChoosePrompt(Gen.ctxOf(t, app.rm), st.company, n, Gen.historyJson(R), r.choices.get(ci));
        app.ask(prompt, false, new App.Reply() {
            @Override public void ok(JSONObject json) {
                try {
                    Gen.Judge d = Gen.judge(json, n);
                    r.quality = d.quality;
                    r.outcome = d.outcome;
                    r.lesson = d.lesson;
                    if (d.next != null) {
                        R.add(d.next);
                    } else {
                        st.debrief = d.debrief;
                        if (st.debrief.survived) app.award(t.key, 'i', 40, "Incident resolved"); else app.addXp(10, "Postmortem filed");
                    }
                } catch (Gen.Fail f) {
                    r.chosen = -1;
                    st.fail(f);
                }
                done();
            }
            @Override public void fail(Gen.Fail f) { r.chosen = -1; st.fail(f); done(); }
            private void done() { st.loading = false; refresh(app, t); }
        });
    }

    /* ---------------- generated Teach ---------------- */

    private static void gTeach(final MainActivity a, LinearLayout p, final Roadmap.Stop t) {
        final App app = a.app;
        UiState.GTeach s0 = app.ui.teach.get(t.key);
        if (s0 == null) { s0 = new UiState.GTeach(); app.ui.teach.put(t.key, s0); }
        final UiState.GTeach st = s0;
        p.addView(Ui.h3(a, "Teach"), Ui.block(0, st.q == null ? 6 : 10));
        if (st.loading && st.q == null) { p.addView(Screens.loading(a, "Ravi is walking over with his laptop…")); return; }
        if (st.q == null) {
            p.addView(Ui.t(a, "Ravi joined the team six months out of college. He's sharp and asks \"why\" until the answer holds. Explain it well enough and he gets it; leave a gap and he finds it. Score 2 of 3 to earn the pip.", Ui.BODY4, 16, Ui.MUTED, 1.55f));
            p.addView(Ui.btn(a, "Call Ravi over", Ui.BTN, () -> teachAsk(a, t)), left(12));
            err(a, p, st);
            return;
        }
        p.addView(bubble(a, "Ravi", st.q, false));
        if (st.g != null) {
            final Gen.Grade g = st.g;
            p.addView(bubble(a, "You", st.a, true));
            p.addView(bubble(a, "Ravi", g.reaction, false));
            p.addView(scoreRow(a, g.score), Ui.block(4, 4));
            LinearLayout cols = a.wdp > 480 ? Ui.row(a) : Ui.col(a);
            if (a.wdp > 480) cols.setGravity(Gravity.TOP);
            View nailed = bullets(a, "You nailed", g.nailed, "Nothing yet"), missing = bullets(a, "Missing", g.missing, "Nothing major");
            if (a.wdp > 480) { cols.addView(nailed, Ui.lpw(WRAP)); cols.addView(missing, Ui.m(Ui.lpw(WRAP), 12, 0, 0, 0)); }
            else { cols.addView(nailed); cols.addView(missing, Ui.block(12, 0)); }
            p.addView(cols, Ui.block(4, 0));
            details(a, p, "A crisp model answer", st.open, () -> { st.open = !st.open; a.render(); }, Ui.body(a, g.modelAnswer));
            W.Flow row = new W.Flow(a, 10, 10);
            row.addView(Ui.btn(a, "Answer again", Ui.BTN_ALT, () -> { st.draft = st.a; st.g = null; a.render(); }));
            row.addView(Ui.btn(a, "New question", Ui.BTN_ALT, () -> { app.ui.teach.put(t.key, new UiState.GTeach()); teachAsk(a, t); }));
            p.addView(row, Ui.block(12, 0));
        } else {
            p.addView(Ui.note(a, "Your explanation. Short is fine if it's right."));
            EditText ans = Ui.area(a, "Type, or use your keyboard's voice input", st.draft);
            ans.setId(R.id.answer);
            Screens.watch(ans, s -> st.draft = s);
            if (st.loading) ans.setEnabled(false);
            p.addView(ans, Ui.lp(MATCH, WRAP));
            if (st.loading) p.addView(Screens.loading(a, "Ravi is thinking about your answer…"), Ui.block(10, 0));
            else p.addView(Ui.btn(a, "Explain to Ravi", Ui.BTN, () -> teachGrade(a, t)), left(10));
        }
        err(a, p, st);
    }

    private static View bullets(MainActivity a, String title, String[] items, String none) {
        LinearLayout c = Ui.col(a);
        c.addView(Ui.t(a, title, Ui.BODY7, 14, Ui.INK, 1.55f));
        String[] list = items.length > 0 ? items : new String[]{none};
        for (String s : list) {
            LinearLayout li = Ui.row(a);
            li.setGravity(Gravity.TOP);
            li.addView(Ui.t(a, "•", Ui.BODY4, 14, Ui.INK, 1.55f), Ui.lp(18, WRAP));
            li.addView(Ui.t(a, s, Ui.BODY4, 14, Ui.INK, 1.55f), Ui.lpw(WRAP));
            c.addView(li, Ui.block(c.getChildCount() == 1 ? 4 : 0, 0));
        }
        return c;
    }

    private static void teachAsk(final MainActivity a, final Roadmap.Stop t) {
        final App app = a.app;
        final UiState.GTeach st = app.ui.teach.get(t.key);
        if (st == null || st.loading) return;
        st.loading = true;
        st.clearErr();
        a.render();
        app.ask(Gen.teachAskPrompt(Gen.ctxOf(t, app.rm)), true, new App.Reply() {
            @Override public void ok(JSONObject json) {
                try { st.q = Gen.question(json); } catch (Gen.Fail f) { st.fail(f); }
                done();
            }
            @Override public void fail(Gen.Fail f) { st.fail(f); done(); }
            private void done() { st.loading = false; refresh(app, t); }
        });
    }

    private static void teachGrade(final MainActivity a, final Roadmap.Stop t) {
        final App app = a.app;
        final UiState.GTeach st = app.ui.teach.get(t.key);
        if (st == null || st.loading || st.q == null) return;
        String ans = st.draft == null ? "" : st.draft.trim();
        a.hideKeyboard();
        if (ans.length() < 15) {
            st.errCode = "short";
            st.errMsg = "";
            a.render();
            return;
        }
        st.a = ans;
        st.loading = true;
        st.clearErr();
        a.render();
        app.ask(Gen.teachGradePrompt(Gen.ctxOf(t, app.rm), st.q, ans), false, new App.Reply() {
            @Override public void ok(JSONObject json) {
                try {
                    Gen.Grade g = Gen.grade(json);
                    st.g = g;
                    if (g.score >= 2) app.award(t.key, 't', 60, "Ravi gets it"); else app.addXp(5, "Keep going");
                } catch (Gen.Fail f) {
                    st.fail(f);
                }
                done();
            }
            @Override public void fail(Gen.Fail f) { st.fail(f); done(); }
            private void done() { st.loading = false; refresh(app, t); }
        });
    }
}
