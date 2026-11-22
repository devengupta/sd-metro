package com.sdmetro.app;

import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Sign-in, Map, Line, Today, Arcade and Profile. Each method fills the screen from current state. */
final class Screens {
    private Screens() { }

    private static final int MATCH = Ui.MATCH, WRAP = Ui.WRAP;
    private static final Random RND = new Random();

    interface Text { void changed(String s); }

    static void watch(EditText e, final Text t) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { t.changed(s.toString()); }
        });
    }

    /* ---------------- shared blocks ---------------- */

    /** Title block: big heading, mono subtitle, a solid rule and a dashed one under it. */
    static void mast(MainActivity a, LinearLayout root, CharSequence title, String sub) {
        W.T h = Ui.head(a, title, Ui.clamp(34, a.wdp * .095f, 60), Ui.INK);
        h.setLetterSpacing(.01f);
        root.addView(h, Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
        W.T s = Ui.mono(a, sub, 12, Ui.MUTED);
        s.setLetterSpacing(.02f);
        root.addView(s, Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 14));
        View solid = new View(a);
        solid.setBackgroundColor(Ui.INK);
        root.addView(solid, Ui.lp(MATCH, 1));
        View dash = new View(a);
        dash.setBackground(new W.Box().side(W.Box.BOTTOM, Ui.RULE, 1, true));
        root.addView(dash, Ui.m(Ui.lp(MATCH, 1), 0, 4, 0, 11));
    }

    static SpannableStringBuilder title() {
        SpannableStringBuilder b = Ui.rich();
        b.append("SYSTEM DESIGN ");
        return Ui.add(b, "METRO", Ui.SIGNAL, null, 0);
    }

    /** The ticket: XP, streak, mastered, visited, and progress to the next rank. */
    static void ticket(MainActivity a, LinearLayout root) {
        App app = a.app;
        boolean small = a.wdp <= 420;
        Progress.Rank r = app.S.rank();
        LinearLayout box = Ui.col(a);
        box.setBackground(new W.Box().fill(Ui.DEEP).border(Ui.INK, 1).radius(2));
        Ui.pad(box, 1, 1, 1, 1);
        LinearLayout cells = Ui.row(a);
        cells.setGravity(Gravity.TOP);
        String[] labels = {"XP", "Streak", "Mastered", "Visited"};
        CharSequence[] values = {
            String.valueOf(app.S.xp),
            Ui.add(Ui.rich().append(String.valueOf(app.S.streak(LocalDate.now()))), " d", 0, null, 15),
            String.valueOf(app.S.mastered(app.rm)), String.valueOf(app.S.touched(app.rm))
        };
        for (int i = 0; i < 4; i++) {
            LinearLayout cell = Ui.col(a);
            if (small) Ui.pad(cell, 8, 9, 8, 9); else Ui.pad(cell, 12, 10, 12, 10);
            if (i < 3) cell.setBackground(new W.Box().side(W.Box.RIGHT, Ui.RULE, 1, false));
            W.T cap = Ui.t(a, Ui.up(labels[i]), Ui.MONO4, 10, Ui.MUTED, 2.48f);
            cap.setLetterSpacing(.1f);
            cell.addView(cap, Ui.lp(MATCH, WRAP));
            W.T v = Ui.t(a, values[i], Ui.DISPLAY7, small ? 23 : 30, Ui.INK, 1f);
            cell.addView(v, Ui.m(Ui.lp(MATCH, WRAP), 0, 4, 0, 0));
            cells.addView(cell, Ui.lpw(MATCH));
        }
        box.addView(cells, Ui.lp(MATCH, WRAP));
        LinearLayout rank = Ui.row(a);
        rank.setBackground(new W.Box().side(W.Box.TOP, Ui.RULE, 1, false));
        if (small) Ui.pad(rank, 8, 9, 8, 9); else Ui.pad(rank, 12, 10, 12, 10);
        rank.addView(Ui.mono(a, r.name, 13, Ui.INK));
        rank.addView(new W.Bar(a, r.pct / 100.0, Ui.SIGNAL), Ui.m(Ui.lpw(6), 10, 0, 10, 0));
        rank.addView(Ui.mono(a, r.next != null ? r.need + " to " + r.next : "Top rank", 13, Ui.INK));
        box.addView(rank, Ui.lp(MATCH, WRAP));
        root.addView(box, Ui.lp(MATCH, WRAP));
    }

    /** Section heading with a dashed rule and an optional mono note on the right. */
    /** top: 24dp after a plain block, 16dp after a card (whose own 8dp margin makes up the rest). */
    static void sect(MainActivity a, LinearLayout root, String title, String right, int rightColor, float top) {
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.BOTTOM);
        row.setBackground(new W.Box().side(W.Box.BOTTOM, Ui.RULE, 1, true));
        Ui.pad(row, 0, 0, 0, 6);
        W.T h = Ui.head(a, title, 24, Ui.INK);
        h.setLetterSpacing(.05f);
        row.addView(h, Ui.lpw(WRAP));
        if (right != null) row.addView(Ui.mono(a, right, 13, rightColor), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
        root.addView(row, Ui.block(top, 8));
    }

    static View loading(MainActivity a, String msg) {
        LinearLayout box = Ui.col(a);
        Ui.pad(box, 0, 18, 0, 8);
        box.addView(new W.Track(a), Ui.lp(MATCH, 6));
        box.addView(Ui.mono(a, msg, 12, Ui.MUTED), Ui.m(Ui.lp(MATCH, WRAP), 0, 8, 0, 0));
        return box;
    }

    static W.T errBox(MainActivity a, CharSequence msg) {
        W.T v = Ui.t(a, msg, Ui.BODY4, 14, 0xFFFFD3CC, 1.55f);
        Ui.pad(v, 13, 11, 13, 11);
        v.setBackground(new W.Box().fill(Ui.alpha(Ui.BAD, .12f)).border(Ui.BAD, 1));
        return v;
    }

    /* ---------------- sign in / create account ---------------- */

    static void auth(final MainActivity a, LinearLayout root) {
        final App app = a.app;
        final UiState ui = app.ui;
        final boolean up = "up".equals(ui.authTab);
        int n = app.accounts.count();

        W.T count = Ui.mono(a, app.rm.sections.size() + " LINES · " + app.rm.totalStops + " STOPS · 1 TERMINUS", 13, Ui.MUTED);
        root.addView(count, Ui.m(Ui.lp(MATCH, WRAP), 0, 8, 0, 0));
        root.addView(Ui.head(a, title(), Ui.clamp(40, a.wdp * .12f, 64), Ui.INK), Ui.lp(MATCH, WRAP));
        root.addView(Ui.t(a, "Ride every concept on the roadmap, fix the 2:47 AM incident, and teach it back. Works fully offline.", Ui.BODY4, 16, Ui.MUTED, 1.55f),
                Ui.m(Ui.lp(MATCH, WRAP), 0, 8, 0, 18));

        LinearLayout p = Ui.panel(a);
        W.Flow tabs = new W.Flow(a, 6, 6);
        tabs.addView(Ui.chip(a, "Sign in", !up, () -> { ui.authTab = "in"; ui.authErr = ""; a.render(); }));
        tabs.addView(Ui.chip(a, "Create account", up, () -> { ui.authTab = "up"; ui.authErr = ""; a.render(); }));
        p.addView(tabs, Ui.lp(MATCH, WRAP));

        final EditText name = Ui.input(a, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText email = Ui.input(a, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText pass = Ui.input(a, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText pass2 = Ui.input(a, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTypeface(Ui.BODY4);
        pass2.setTypeface(Ui.BODY4);
        name.setId(R.id.au_name); email.setId(R.id.au_email); pass.setId(R.id.au_pass); pass2.setId(R.id.au_pass2);
        name.setAutofillHints(View.AUTOFILL_HINT_NAME);
        email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
        pass.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
        name.setText(ui.authName);
        email.setText(ui.authEmail);
        watch(name, s -> ui.authName = s);
        watch(email, s -> ui.authEmail = s);
        if (up) p.addView(Ui.field(a, "Name", name));
        p.addView(Ui.field(a, "Email", email));
        p.addView(Ui.field(a, "Password", pass));
        if (up) p.addView(Ui.field(a, "Confirm password", pass2));
        if (!ui.authErr.isEmpty()) p.addView(errBox(a, ui.authErr), Ui.block(12, 0));

        final Runnable submit = () -> {
            if (ui.authBusy) return;
            final String em = Accounts.cleanEmail(ui.authEmail), pw = pass.getText().toString(), nm = ui.authName.trim();
            String err = app.accounts.validate(up, nm, em, pw, pass2.getText().toString());
            a.hideKeyboard();
            if (err != null) { ui.authErr = err; a.render(); return; }
            ui.authBusy = true;
            ui.authErr = "";
            a.render();
            final long now = app.now();
            app.pool.execute(() -> {
                final String fail = app.accounts.finish(up, nm, em, pw, now);
                app.main.post(() -> {
                    if (app.ui != ui || app.me != null) return;
                    ui.authBusy = false;
                    if (fail != null) { ui.authErr = fail; if (app.host != null) app.host.rerender(); return; }
                    app.enter(em);
                    if (app.host instanceof MainActivity) ((MainActivity) app.host).signedIn();
                    String first = app.me.name.split(" ")[0];
                    app.toast("Welcome aboard, " + first);
                });
            });
        };
        EditText lastField = up ? pass2 : pass;
        lastField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        lastField.setOnEditorActionListener((v, id, ev) -> { submit.run(); return true; });

        W.T go = Ui.btn(a, up ? "Create account" : "Sign in", Ui.BTN, submit);
        go.setId(R.id.au_submit);
        if (ui.authBusy) Ui.disable(go);
        LinearLayout.LayoutParams glp = Ui.m(Ui.lp(WRAP, WRAP), 0, 12, 0, 0);
        glp.gravity = Gravity.START;
        p.addView(go, glp);
        String where = n > 0 ? " (" + n + " profile" + (n > 1 ? "s" : "") + " here)" : "";
        p.addView(Ui.note(a, "Accounts and progress are stored on this phone only" + where + ". Nothing is sent to a server, so a forgotten password cannot be recovered."),
                Ui.m(Ui.lp(MATCH, WRAP), 0, 14, 0, 0));
        root.addView(p, Ui.lp(MATCH, WRAP));
    }

    /* ---------------- map ---------------- */

    static void map(final MainActivity a, LinearLayout root) {
        final App app = a.app;
        Roadmap rm = app.rm;
        mast(a, root, title(), rm.sections.size() + " lines · " + rm.totalStops + " stops · " + rm.keyLoc.size() + " unique concepts · 1 terminus");
        ticket(a, root);

        final Roadmap.Stop last = app.S.lastSec >= 0 ? rm.stop(app.S.lastSec, app.S.lastIdx) : null;
        LinearLayout cont = Ui.row(a);
        cont.setBackground(new W.Box().fill(Ui.SIGNAL).radius(2));
        Ui.pad(cont, 14, 12, 14, 12);
        LinearLayout left = Ui.col(a);
        W.T lbl = Ui.t(a, last != null ? "CONTINUE AT" : "FIRST RIDE", Ui.MONO4, 11, Ui.alpha(Ui.DEEP, .75f), 2.25f);
        lbl.setLetterSpacing(.1f);
        left.addView(lbl);
        left.addView(Ui.t(a, Ui.up(last != null ? last.name : "How the Internet Works"), Ui.DISPLAY7, 23, Ui.DEEP, 1.55f));
        cont.addView(left, Ui.lpw(WRAP));
        W.T goT = Ui.t(a, last != null ? "LINE " + Ui.pad2(last.sec) + " →" : "BOARD →", Ui.DISPLAY7, 18, Ui.DEEP, 1.55f);
        goT.setMaxLines(1);
        cont.addView(goT, Ui.m(Ui.lp(WRAP, WRAP), 12, 0, 0, 0));
        Ui.click(cont, () -> a.go(last != null ? "stop-" + last.sec + "-" + last.idx : "stop-1-0"));
        root.addView(cont, Ui.block(14, 4));

        W.Flow legend = new W.Flow(a, 14, 6);
        for (Roadmap.Phase ph : Roadmap.PHASES) {
            LinearLayout it = Ui.row(a);
            View sw = new View(a);
            sw.setBackgroundColor(ph.color);
            it.addView(sw, Ui.lp(18, 4));
            it.addView(Ui.mono(a, ph.name, 12, Ui.MUTED), Ui.m(Ui.lp(WRAP, WRAP), 6, 0, 0, 0));
            legend.addView(it);
        }
        root.addView(legend, Ui.block(18, 14));

        for (int i = 0; i < rm.sections.size(); i++) {
            final Roadmap.Section s = rm.sections.get(i);
            double pr = app.S.secProgress(s);
            int pct = (int) Math.round(pr * 100);
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.TOP);
            row.setMinimumHeight(Ui.dp(76));
            row.addView(new W.MapRail(a, s.phase.color, i == 0, false, pr), Ui.lp(64, MATCH));
            LinearLayout body = Ui.col(a);
            body.setBackground(new W.Box().side(W.Box.BOTTOM, Ui.RULE, 1, true));
            Ui.pad(body, 0, 10, 0, 14);
            LinearLayout top = Ui.row(a);
            W.T num = Ui.t(a, "LINE " + Ui.pad2(s.n), Ui.MONO4, 11, Ui.MUTED, 2.2545f);
            num.setLetterSpacing(.08f);
            top.addView(num);
            if (app.S.lastSec == s.n) {
                W.T here = Ui.t(a, "YOU ARE HERE", Ui.MONO6, 10, Ui.DEEP, 1.55f);
                here.setLetterSpacing(.08f);
                here.setBackgroundColor(Ui.SIGNAL);
                Ui.pad(here, 7, 2, 7, 2);
                top.addView(here, Ui.m(Ui.lp(WRAP, WRAP), 8, 0, 0, 0));
            }
            body.addView(top);
            W.T nm = Ui.t(a, Ui.up(s.name), Ui.DISPLAY6, 22, Ui.INK, 1.1f);
            nm.setLetterSpacing(.01f);
            body.addView(nm, Ui.lp(MATCH, WRAP));
            LinearLayout meta = Ui.row(a);
            W.T zone = Ui.mono(a, Ui.up(s.phase.name), 12, s.phase.color);
            zone.setLetterSpacing(.08f);
            zone.setBackground(new W.Box().border(s.phase.color, 1));
            Ui.pad(zone, 7, 1, 7, 1);
            meta.addView(zone);
            meta.addView(Ui.mono(a, s.stops.size() + " stops", 12, Ui.MUTED), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
            meta.addView(new W.Bar(a, pr, s.phase.color), Ui.m(Ui.lp(90, 4), 10, 0, 10, 0));
            meta.addView(Ui.mono(a, pct + "%", 12, Ui.MUTED));
            body.addView(meta, Ui.m(Ui.lp(MATCH, WRAP), 0, 5, 0, 0));
            row.addView(body, Ui.lpw(WRAP));
            row.setContentDescription("Line " + s.n + ", " + s.name);
            Ui.click(row, () -> a.go("line-" + s.n));
            root.addView(row, Ui.lp(MATCH, WRAP));
        }

        LinearLayout term = Ui.row(a);
        term.setGravity(Gravity.TOP);
        term.setMinimumHeight(Ui.dp(76));
        term.addView(new W.MapRail(a, Ui.SIGNAL, false, true, 1), Ui.lp(64, MATCH));
        LinearLayout box = Ui.col(a);
        box.setBackground(new W.Box().fill(Ui.DEEP).border(Ui.SIGNAL, 1));
        Ui.pad(box, 17, 15, 17, 15);
        W.T tn = Ui.t(a, "TERMINUS", Ui.MONO4, 11, Ui.MUTED, 2.2545f);
        tn.setLetterSpacing(.08f);
        box.addView(tn);
        box.addView(Ui.head(a, "Become an Exceptional System Designer", 22, Ui.SIGNAL), Ui.lp(MATCH, WRAP));
        LinearLayout goals = Ui.col(a);
        for (String g : rm.goals) {
            LinearLayout li = Ui.row(a);
            li.setGravity(Gravity.TOP);
            li.addView(Ui.t(a, "○", Ui.BODY4, 10, Ui.MUTED, 2.17f), Ui.lp(18, WRAP));
            li.addView(Ui.t(a, g, Ui.BODY4, 14, Ui.MUTED, 1.55f), Ui.lpw(WRAP));
            goals.addView(li);
        }
        box.addView(goals, Ui.m(Ui.lp(MATCH, WRAP), 0, 8, 0, 0));
        term.addView(box, Ui.m(Ui.lpw(WRAP), 0, 4, 0, 0));
        root.addView(term, Ui.lp(MATCH, WRAP));
    }

    /* ---------------- line ---------------- */

    static W.T back(final MainActivity a, String text, final Runnable r) {
        W.T v = Ui.mono(a, text, 13, Ui.MUTED);
        Ui.pad(v, 0, 6, 0, 6);
        Ui.click(v, r);
        return v;
    }

    static void line(final MainActivity a, LinearLayout root, final Roadmap.Section s) {
        App app = a.app;
        int c = s.phase.color;
        double pr = app.S.secProgress(s);
        LinearLayout.LayoutParams blp = Ui.lp(WRAP, WRAP);
        root.addView(back(a, "← Network map", () -> a.tab("map")), blp);

        LinearLayout head = Ui.col(a);
        head.setBackground(new W.Box().fill(Ui.DEEP).border(c, 1).left(c, 10));
        Ui.pad(head, 26, 17, 17, 17);
        W.T code = Ui.mono(a, "LINE " + Ui.pad2(s.n) + " · " + Ui.up(s.phase.name), 12, c);
        code.setLetterSpacing(.12f);
        head.addView(code);
        head.addView(Ui.head(a, s.name, Ui.clamp(30, a.wdp * .08f, 46), Ui.INK), Ui.m(Ui.lp(MATCH, WRAP), 0, 4, 0, 0));
        head.addView(Ui.t(a, s.hook, Ui.BODY4, 16, Ui.MUTED, 1.55f), Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
        LinearLayout prog = Ui.row(a);
        prog.addView(new W.Bar(a, pr, c), Ui.lpw(6));
        prog.addView(Ui.mono(a, Math.round(pr * 100) + "% of pips", 12, Ui.INK), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
        head.addView(prog, Ui.m(Ui.lp(MATCH, WRAP), 0, 12, 0, 0));
        root.addView(head, Ui.block(6, 14));

        SpannableStringBuilder nb = Ui.rich();
        nb.append("Each stop has three pips: ");
        Ui.add(nb, "Ride", Ui.BLUE, Ui.BODY7, 0).append(" (understand it), ");
        Ui.add(nb, "Pager", Ui.SIGNAL, Ui.BODY7, 0).append(" (survive an incident with it), ");
        Ui.add(nb, "Teach", Ui.GOOD, Ui.BODY7, 0).append(" (explain it back). Black tags like ");
        Ui.add(nb, "L9", 0, Ui.MONO6, 0).append(" are interchanges: the same concept on another line, sharing your progress.");
        root.addView(Ui.note(a, nb), Ui.block(13, 13));

        int n = s.stops.size();
        for (int i = 0; i < n; i++) {
            final Roadmap.Stop t = s.stops.get(i);
            int depth = Math.min(t.depth, 3);
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.TOP);
            row.addView(new W.StopRail(a, c, depth, i == 0, i == n - 1, app.S.pipCount(t.key)), Ui.lp(W.StopRail.widthDp(depth), MATCH));
            LinearLayout link = Ui.row(a);
            link.setMinimumHeight(Ui.dp(46));
            link.setBackground(new W.Box().side(W.Box.BOTTOM, Ui.RULE, 1, true));
            Ui.pad(link, 8, 10, 4, 10);
            SpannableStringBuilder nm = Ui.rich();
            nm.append(t.name);
            Set<Integer> lines = new LinkedHashSet<>();
            for (Roadmap.Loc o : app.rm.others(t)) lines.add(o.sec);
            boolean firstTag = true;
            for (Integer ln : lines) {
                int at = nm.length();
                nm.append("L" + ln);
                nm.setSpan(new W.Tag(a, Ui.SIGNAL, firstTag ? 6 : 4), at, nm.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                firstTag = false;
            }
            W.T name = Ui.t(a, nm, depth == 1 ? Ui.BODY7 : Ui.BODY5, 16, depth == 1 ? Ui.INK : Ui.MUTED, 1.55f);
            link.addView(name, Ui.lpw(WRAP));
            link.addView(W.Pips.of(a, app.S.pipsOf(t.key)), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
            row.addView(link, Ui.lpw(WRAP));
            row.setContentDescription(t.name);
            Ui.click(row, () -> a.go("stop-" + s.n + "-" + t.idx));
            root.addView(row, Ui.lp(MATCH, WRAP));
        }
    }

    /* ---------------- today ---------------- */

    static void today(final MainActivity a, LinearLayout root) {
        final App app = a.app;
        final Roadmap rm = app.rm;
        List<String> due = app.S.dueList(app.now(), rm);
        int td = app.S.dailyCount(app.today()), goal = Progress.DAILY_GOAL;
        mast(a, root, "Today", new SimpleDateFormat("EEEE, d MMMM", new Locale("en", "IN")).format(new Date()));
        ticket(a, root);

        LinearLayout quest = Ui.card(a);
        LinearLayout qr = Ui.row(a);
        qr.addView(Ui.bold(a, "Daily quest: 5 actions"), Ui.lpw(WRAP));
        qr.addView(Ui.mono(a, Math.min(td, goal) + "/" + goal, 16, Ui.INK));
        quest.addView(qr);
        quest.addView(new W.Bar(a, Math.min(1.0, td / (double) goal), Ui.GOOD), Ui.m(Ui.lp(MATCH, 6), 0, 8, 0, 0));
        quest.addView(Ui.note(a, "Any ride, incident, teach-back, review or arcade round counts."), Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
        root.addView(quest, Ui.block(12, 8));

        sect(a, root, "Due for review", String.valueOf(due.size()), Ui.MUTED, 16);
        if (due.isEmpty()) {
            LinearLayout none = Ui.card(a);
            none.addView(Ui.note(a, "Nothing due. Stops you learn come back here after 1, 3, 7, 16 and 35 days so they stick."));
            root.addView(none);
        }
        for (final String k : due.subList(0, Math.min(8, due.size()))) {
            final Roadmap.Loc l = rm.keyLoc.get(k).get(0);
            Roadmap.Stop t = rm.stop(l.sec, l.idx);
            LinearLayout card = Ui.card(a);
            LinearLayout hr = Ui.row(a);
            hr.addView(Ui.bold(a, t.name), Ui.lpw(WRAP));
            hr.addView(Ui.note(a, "Line " + l.sec), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
            card.addView(hr);
            String noteText = app.S.notes.get(k);
            if (noteText == null || noteText.isEmpty()) { Content.Entry e = app.content.get(k); noteText = e != null ? e.k : ""; }
            final W.T note = Ui.t(a, noteText, Ui.BODY4, 14, Ui.INK, 1.55f);
            note.setVisibility(View.GONE);
            card.addView(note, Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 8));
            final W.Flow actions = new W.Flow(a, 10, 6);
            final W.Flow rate = new W.Flow(a, 10, 6);
            rate.setVisibility(View.GONE);
            final W.T show = Ui.btn(a, "Recall it, then reveal", Ui.BTN_ALT, null);
            // The rating row stays hidden until the note is revealed.
            Ui.click(show, () -> { note.setVisibility(View.VISIBLE); rate.setVisibility(View.VISIBLE); show.setVisibility(View.GONE); });
            actions.addView(show);
            W.T open = Ui.note(a, "Open stop");
            open.setPaintFlags(open.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            Ui.click(open, () -> a.go("stop-" + l.sec + "-" + l.idx));
            actions.addView(open);
            card.addView(actions);
            rate.addView(Ui.note(a, "How well did you recall it?"));
            String[] names = {"Forgot", "Got it", "Easy"};
            for (int v = 0; v < 3; v++) {
                final int val = v;
                rate.addView(Ui.chip(a, names[v], false, () -> {
                    app.S.rate(k, val, app.now());
                    app.addXp(10, "Reviewed");
                    a.render();
                }));
            }
            card.addView(rate, Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
            root.addView(card);
        }

        sect(a, root, "Next stops", null, 0, 16);
        List<Roadmap.Stop> sug = app.S.suggestions(rm);
        if (sug.isEmpty()) {
            LinearLayout none = Ui.card(a);
            none.addView(Ui.note(a, "Every stop is mastered. You reached the terminus."));
            root.addView(none);
        }
        for (final Roadmap.Stop t : sug) {
            Roadmap.Section s = rm.sec(t.sec);
            LinearLayout card = Ui.card(a);
            LinearLayout row = Ui.row(a);
            row.addView(new W.Hex(a, 32, s.phase.color, s.phase.on, String.valueOf(s.n), 16));
            LinearLayout mid = Ui.col(a);
            mid.addView(Ui.bold(a, t.name));
            mid.addView(Ui.t(a, s.name, Ui.BODY4, 13, Ui.MUTED, 1.9077f));
            row.addView(mid, Ui.m(Ui.lpw(WRAP), 10, 0, 10, 0));
            row.addView(W.Pips.of(a, app.S.pipsOf(t.key)));
            card.addView(row);
            Ui.click(card, () -> a.go("stop-" + t.sec + "-" + t.idx));
            root.addView(card);
        }
    }

    /* ---------------- arcade ---------------- */

    static void arcade(final MainActivity a, LinearLayout root) {
        final App app = a.app;
        final UiState ui = app.ui;
        if (ui.game == null) ui.game = "ladder";
        mast(a, root, "Arcade", "Five-round drills for speed and instinct.");
        boolean wide = a.wdp > 520;
        LinearLayout games = wide ? Ui.row(a) : Ui.col(a);
        if (wide) games.setGravity(Gravity.TOP);
        for (int i = 0; i < Arcade.GAME_KEYS.length; i++) {
            final String k = Arcade.GAME_KEYS[i];
            boolean on = k.equals(ui.game);
            LinearLayout g = Ui.col(a);
            g.setBackground(on ? new W.Box().fill(Ui.DEEP).border(Ui.SIGNAL, 1).bar(Ui.SIGNAL) : new W.Box().fill(Ui.CARD).border(Ui.RULE, 1));
            Ui.pad(g, 13, 13, 13, 13);
            g.addView(Ui.t(a, Ui.up(Arcade.GAMES[i][0]), Ui.DISPLAY7, 20, Ui.INK, 1f));
            g.addView(Ui.t(a, Arcade.GAMES[i][1], Ui.BODY4, 13, Ui.MUTED, 1.55f), Ui.m(Ui.lp(MATCH, WRAP), 0, 4, 0, 0));
            g.addView(Ui.mono(a, "Best: " + app.S.best(k) + "/5", 12, Ui.SIGNAL), Ui.m(Ui.lp(MATCH, WRAP), 0, 6, 0, 0));
            g.setSelected(on);
            Ui.click(g, () -> { ui.game = k; ui.ladder = null; ui.quiz = null; a.render(); });
            if (wide) games.addView(g, Ui.m(Ui.lpw(MATCH), i == 0 ? 0 : 8, 0, 0, 0));
            else games.addView(g, Ui.m(Ui.lp(MATCH, WRAP), 0, i == 0 ? 0 : 8, 0, 0));
        }
        root.addView(games, Ui.lp(MATCH, WRAP));
        LinearLayout p = Ui.panel(a);
        if ("ladder".equals(ui.game)) ladder(a, p); else quiz(a, p);
        root.addView(p, Ui.block(12, 0));
    }

    private static void finishGame(MainActivity a, String k, int score) {
        int gain = a.app.S.finishGame(k, score, a.app.today());
        a.app.save();
        a.app.toast("+" + gain + " XP · " + Arcade.title(k) + " " + score + "/5");
    }

    private static void ladder(final MainActivity a, LinearLayout p) {
        final UiState ui = a.app.ui;
        if (ui.ladder == null) ui.ladder = new Arcade.Ladder(RND);
        final Arcade.Ladder g = ui.ladder;
        if (!g.done()) {
            p.addView(Ui.h3(a, "Latency Ladder"), Ui.block(0, 6));
            SpannableStringBuilder b = Ui.rich().append("Tap from ");
            Ui.add(b, "fastest", 0, Ui.BODY7, 0).append(" to ");
            Ui.add(b, "slowest", 0, Ui.BODY7, 0).append(". Tap a picked one to undo.");
            p.addView(Ui.note(a, b), Ui.block(0, 13));
            for (int i = 0; i < g.items.length; i++) {
                final int idx = i;
                int ord = g.orderOf(i);
                LinearLayout lad = Ui.row(a);
                lad.setBackground(new W.Box().fill(Ui.OPT).border(ord > 0 ? Ui.SIGNAL : Ui.RULE, 1));
                Ui.pad(lad, 13, 11, 13, 11);
                lad.addView(Ui.body(a, g.name(i)), Ui.lpw(WRAP));
                W.T o = Ui.t(a, ord > 0 ? String.valueOf(ord) : "", Ui.MONO6, 12, Ui.DEEP, 1f);
                o.setGravity(Gravity.CENTER);
                o.setBackground(ord > 0 ? new W.Box().fill(Ui.SIGNAL) : new W.Box().border(Ui.DIM, 1));
                lad.addView(o, Ui.m(Ui.lp(22, 22), 10, 0, 0, 0));
                Ui.click(lad, () -> {
                    if (g.tap(idx)) finishGame(a, "ladder", g.score);
                    a.render();
                });
                p.addView(lad, Ui.block(i == 0 ? 0 : 6, 0));
            }
            return;
        }
        p.addView(Ui.h3(a, "Latency Ladder · " + g.score + "/5"), Ui.block(0, 6));
        p.addView(Ui.note(a, "Correct order, bars on a log scale. Red means you placed it elsewhere."), Ui.block(0, 13));
        int[] sorted = g.sorted();
        for (int j = 0; j < sorted.length; j++) {
            int i = sorted[j];
            LinearLayout r = Ui.row(a);
            r.addView(Ui.t(a, (j + 1) + ". " + g.name(i), Ui.BODY4, 13, Ui.INK, 1.55f), Ui.lpw(WRAP));
            r.addView(Ui.mono(a, Arcade.fmtNs(g.ns(i)), 12, Ui.MUTED), Ui.m(Ui.lp(WRAP, WRAP), 6, 0, 0, 0));
            p.addView(r);
            p.addView(new W.Bar(a, g.barPct(i) / 100.0, g.miss(j) ? Ui.BAD : Ui.BLUE, 0), Ui.m(Ui.lp(MATCH, 10), 0, 3, 0, 10));
        }
        p.addView(Ui.note(a, "Classic figures from the widely shared \"latency numbers every programmer should know\" list (Jeff Dean, Peter Norvig). Modern hardware shifts some, but the orders of magnitude hold."), Ui.block(13, 13));
        LinearLayout.LayoutParams lp = Ui.lp(WRAP, WRAP);
        p.addView(Ui.btn(a, "Play again", Ui.BTN, () -> { ui.ladder = null; a.render(); }), lp);
    }

    private static void quiz(final MainActivity a, LinearLayout p) {
        final UiState ui = a.app.ui;
        final String k = ui.game;
        if (ui.quiz == null || !k.equals(ui.quiz.game)) ui.quiz = new Arcade.Quiz(k, RND);
        final Arcade.Quiz g = ui.quiz;
        String title = Arcade.title(k);
        if (g.finished()) {
            p.addView(Ui.h3(a, title + " · " + g.score + "/5"), Ui.block(0, 6));
            p.addView(Ui.body(a, g.verdict()), Ui.block(16, 16));
            p.addView(Ui.btn(a, "Play again", Ui.BTN, () -> { ui.quiz = null; a.render(); }), Ui.lp(WRAP, WRAP));
            return;
        }
        final Arcade.Q q = g.current();
        LinearLayout hr = Ui.row(a);
        hr.addView(Ui.h3(a, title), Ui.lpw(WRAP));
        hr.addView(Ui.mono(a, (g.i + 1) + "/5 · " + g.score + " right", 13, Ui.MUTED), Ui.m(Ui.lp(WRAP, WRAP), 10, 0, 0, 0));
        p.addView(hr, Ui.block(0, 6));
        p.addView(Ui.bold(a, q.q), Ui.block(6, 10));
        for (int oi = 0; oi < q.o.length; oi++) {
            final int pick = oi;
            int state = g.ans < 0 ? 0 : oi == q.a ? 1 : oi == g.ans ? 2 : 0;
            p.addView(Ui.opt(a, q.o[oi], state, g.ans >= 0 ? null : () -> { g.answer(pick); a.render(); }), Ui.block(oi == 0 ? 0 : 6, 0));
        }
        if (g.ans >= 0) {
            p.addView(Ui.t(a, q.w, Ui.BODY4, 14, Ui.MUTED, 1.55f), Ui.block(6, 0));
            p.addView(Ui.btn(a, g.i == 4 ? "See score" : "Next", Ui.BTN, () -> {
                if (g.next()) finishGame(a, k, g.score);
                a.render();
            }), Ui.m(Ui.lp(WRAP, WRAP), 0, 12, 0, 0));
        }
    }

    /* ---------------- profile ---------------- */

    static void profile(final MainActivity a, LinearLayout root) {
        final App app = a.app;
        final Accounts.Account me = app.me;
        final String key = app.apiKey;
        int n = app.accounts.count();
        mast(a, root, "Profile", me.email);
        ticket(a, root);

        sect(a, root, "Account", null, 0, 24);
        LinearLayout acc = Ui.card(a);
        String[][] kv = {
            {"Name", me.name}, {"Email", me.email},
            {"Joined", new SimpleDateFormat("d MMM yyyy", new Locale("en", "IN")).format(new Date(me.created))},
            {"Storage", "On this phone only · " + n + " profile" + (n > 1 ? "s" : "")}
        };
        LinearLayout grid = Ui.col(a);
        float labelW = 0;
        ArrayList<W.T> labels = new ArrayList<>();
        for (String[] row : kv) {
            LinearLayout r = Ui.row(a);
            W.T l = Ui.label(a, row[0], Ui.MUTED, .1f);
            labelW = Math.max(labelW, l.getPaint().measureText(Ui.up(row[0])) * 1.1f + l.getTextSize() * .2f);
            labels.add(l);
            r.addView(l);
            r.addView(Ui.t(a, row[1], Ui.BODY4, 14, Ui.INK, 1.55f), Ui.m(Ui.lpw(WRAP), 14, 0, 0, 0));
            grid.addView(r, Ui.m(Ui.lp(MATCH, WRAP), 0, grid.getChildCount() == 0 ? 0 : 4, 0, 0));
        }
        for (W.T l : labels) l.setMinWidth((int) Math.ceil(labelW));
        acc.addView(grid, Ui.block(6, 12));
        acc.addView(Ui.btn(a, "Sign out", Ui.BTN_ALT, a::signOut), Ui.lp(WRAP, WRAP));
        root.addView(acc);

        boolean has = !key.isEmpty();
        sect(a, root, "Claude key · optional", has ? "CONNECTED" : "NOT SET", has ? Ui.GOOD : Ui.MUTED, 16);
        LinearLayout kc = Ui.card(a);
        kc.addView(Ui.note(a, "Every stop already has built-in Ride, Pager and Teach content, so no key is needed. If you add your own Anthropic API key, each mode also offers a freshly generated version. Usage is billed to your Anthropic account; the key stays on this phone and is sent only to api.anthropic.com."),
                Ui.block(0, 4));
        final EditText in = Ui.input(a, has ? "Saved: ••••" + key.substring(Math.max(0, key.length() - 4)) : "sk-ant-…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        in.setTypeface(Ui.BODY4);
        in.setId(R.id.apikey);
        in.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        kc.addView(Ui.field(a, "Anthropic API key", in));
        W.Flow kr = new W.Flow(a, 10, 10);
        kr.addView(Ui.btn(a, "Save key", Ui.BTN, () -> {
            String v = in.getText().toString().trim();
            if (v.length() < 20) { app.toast("That does not look like an API key"); return; }
            if (!app.accounts.setApiKey(me, v)) { app.toast(App.SAVE_FAILED); return; }
            app.apiKey = v;
            a.hideKeyboard();
            app.toast("Key saved");
            a.render();
        }));
        if (has) kr.addView(Ui.btn(a, "Remove key", Ui.BTN_ALT, () -> {
            app.accounts.removeApiKey(me);
            app.apiKey = "";
            app.toast("Key removed");
            a.render();
        }));
        kc.addView(kr, Ui.block(12, 0));
        kc.addView(Ui.mono(a, "Models: " + Gen.MODEL_DEFAULT + " (Ride, Pager, grading) · " + Gen.MODEL_QUICK + " (Ravi's questions)", 11, Ui.MUTED), Ui.block(12, 0));
        root.addView(kc);

        sect(a, root, "Danger zone", null, 0, 16);
        LinearLayout dz = Ui.card(a);
        dz.addView(Ui.note(a, "Deletes this account and all its progress from this phone. This cannot be undone."), Ui.block(0, 10));
        W.T del = Ui.btn(a, app.ui.delArmed ? "Tap again to delete forever" : "Delete this account", Ui.BTN_DANGER, () -> {
            if (!app.ui.delArmed) { a.armDelete(); return; }
            app.ui.delArmed = false;
            boolean ok = app.accounts.delete(me);
            a.signOut();
            app.toast(ok ? "Account deleted" : App.SAVE_FAILED);
        });
        del.setId(R.id.del);
        dz.addView(del, Ui.lp(WRAP, WRAP));
        root.addView(dz);
    }
}
