package com.sdmetro.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only activity. It owns the shell (scrolling screen, dock, toast) and a back stack of routes:
 * map, line-N, stop-N-I, today, arcade, profile. Screens are rebuilt from state on every change.
 */
public class MainActivity extends Activity implements App.Host {
    private static final String K_STACK = "stack", K_UI = "ui";
    private static final Pattern LINE = Pattern.compile("^line-(\\d+)$"), STOP = Pattern.compile("^stop-(\\d+)-(\\d+)$");
    private static final String[] TABS = {"map", "today", "arcade", "profile"};

    App app;
    /** Screen width in dp; several sizes follow it the way the CSS used vw and media queries. */
    float wdp;
    private LinearLayout view, dock;
    private ScrollView scroll;
    private View dockRule;
    private TextView aiStatus, toast;
    private final TextView[] nav = new TextView[4];
    private ArrayList<String> stack = new ArrayList<>();
    private UiState pendingUi;
    private final Runnable hideToast = this::hideToast;
    private final Runnable disarm = () -> {
        if (app.ui.delArmed) {
            app.ui.delArmed = false;
            if (app.me != null && "profile".equals(route())) render();
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.root).setBackground(new W.Grid());
        view = findViewById(R.id.view);
        scroll = findViewById(R.id.scroll);
        dock = findViewById(R.id.dock);
        dockRule = findViewById(R.id.dockRule);
        aiStatus = findViewById(R.id.aiStatus);
        toast = findViewById(R.id.toast);
        int[] ids = {R.id.navMap, R.id.navToday, R.id.navArcade, R.id.navProfile};
        for (int i = 0; i < 4; i++) {
            final String tab = TABS[i];
            nav[i] = findViewById(ids[i]);
            nav[i].setOnClickListener(v -> tab(tab));
        }
        fitWidth();

        app = App.get(this);
        app.host = this;
        if (saved != null) {
            ArrayList<String> s = saved.getStringArrayList(K_STACK);
            if (s != null && !s.isEmpty()) stack = s;
            try { pendingUi = (UiState) saved.getSerializable(K_UI); } catch (Exception e) { pendingUi = null; }
        }
        if (stack.isEmpty()) stack.add("map");
        if (app.ready) onReady(); else { render(); app.load(); }
    }

    @Override
    protected void onDestroy() {
        if (app != null && app.host == this) app.host = null;
        toast.removeCallbacks(hideToast);
        toast.removeCallbacks(disarm);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList(K_STACK, stack);
        if (app != null && app.ready) out.putSerializable(K_UI, app.ui);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        Ui.init(this);
        fitWidth();
        render();
    }

    /** Content is at most 760dp wide and centred, as on the web version. */
    private void fitWidth() {
        wdp = getResources().getConfiguration().screenWidthDp;
        int side = Math.max(16, Math.round((wdp - 760) / 2f));
        View wrap = findViewById(R.id.wrap);
        wrap.setPadding(Ui.dp(side), wrap.getPaddingTop(), Ui.dp(side), wrap.getPaddingBottom());
    }

    /** Content parsed (or failed): pick up saved screen state when the process was recreated, then draw. */
    @Override
    public void onReady() {
        if (pendingUi != null) {
            if (app.ui != pendingUi) app.adopt(pendingUi);
            pendingUi = null;
        }
        render();
    }

    @Override
    public void rerender() { render(); }

    /* ---------------- navigation ---------------- */

    String route() { return stack.get(stack.size() - 1); }

    /** Opens a screen on top of the current one. */
    void go(String route) {
        if (!route.equals(route())) stack.add(route);
        top();
    }

    /** Swaps the current screen (previous / next stop) so Back does not replay every stop. */
    void replace(String route) {
        stack.set(stack.size() - 1, route);
        top();
    }

    /** Goes up to a parent screen: back to it when it is already underneath, otherwise in place of this one. */
    void up(String route) {
        int at = stack.lastIndexOf(route);
        if (at >= 0) { while (stack.size() > at + 1) stack.remove(stack.size() - 1); top(); }
        else replace(route);
    }

    /** Dock tabs: Map is the root, the other tabs sit directly on it. */
    void tab(String name) {
        stack.clear();
        stack.add("map");
        if (!"map".equals(name)) stack.add(name);
        top();
    }

    private void top() {
        hideKeyboard();
        render();
        scroll.scrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        // On the sign-in screen, and at the root, Back leaves the app.
        if (app.me == null || stack.size() <= 1) { finish(); return; }
        stack.remove(stack.size() - 1);
        top();
    }

    void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    void signedIn() {
        stack.clear();
        stack.add("map");
        top();
    }

    void signOut() {
        app.signOut();
        app.ui.authTab = "in";
        signedIn();
    }

    void armDelete() {
        app.ui.delArmed = true;
        toast.removeCallbacks(disarm);
        toast.postDelayed(disarm, 4000);
        render();
    }

    /* ---------------- drawing ---------------- */

    void render() {
        view.removeAllViews();
        boolean in = app.ready && app.me != null;
        dock.setVisibility(in ? View.VISIBLE : View.GONE);
        dockRule.setVisibility(in ? View.VISIBLE : View.GONE);
        aiStatus.setText(app.ready ? app.aiStatus() : "");
        if (!app.ready) {
            LinearLayout box = Ui.col(this);
            Ui.pad(box, 0, 18, 0, 8);
            if (app.loadFailed) {
                box.addView(Ui.body(this, "The bundled lessons could not be read. Reinstall the app."));
            } else {
                box.addView(new W.Track(this), Ui.lp(Ui.MATCH, 6));
                box.addView(Ui.mono(this, "Laying the tracks…", 12, Ui.MUTED), Ui.m(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 8, 0, 0));
            }
            view.addView(box);
            return;
        }
        if (app.me == null) { Screens.auth(this, view); return; }

        String r = route();
        String navKey = r.startsWith("today") ? "today" : r.startsWith("arcade") ? "arcade" : r.startsWith("profile") ? "profile" : "map";
        for (int i = 0; i < 4; i++) {
            boolean cur = TABS[i].equals(navKey);
            nav[i].setBackgroundColor(cur ? Ui.SIGNAL : 0);
            nav[i].setTextColor(cur ? Ui.DEEP : Ui.MUTED);
            nav[i].setSelected(cur);
        }
        Matcher m;
        Roadmap.Section sec;
        Roadmap.Stop stop;
        if ((m = LINE.matcher(r)).matches() && (sec = app.rm.sec(num(m.group(1)))) != null) Screens.line(this, view, sec);
        else if ((m = STOP.matcher(r)).matches() && (stop = app.rm.stop(num(m.group(1)), num(m.group(2)))) != null) StopScreen.build(this, view, stop);
        else if (r.equals("today")) Screens.today(this, view);
        else if (r.equals("arcade")) Screens.arcade(this, view);
        else if (r.equals("profile")) Screens.profile(this, view);
        else Screens.map(this, view);
    }

    private static int num(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    /** True while this stop is the screen being shown. */
    boolean showing(Roadmap.Stop t) { return app.me != null && route().equals("stop-" + t.sec + "-" + t.idx); }

    @Override
    public void toast(String message) {
        toast.setText(message);
        toast.removeCallbacks(hideToast);
        toast.setVisibility(View.VISIBLE);
        toast.setGravity(Gravity.CENTER);
        toast.animate().cancel();
        if (toast.getAlpha() == 0) toast.setTranslationY(Ui.dp(20));
        toast.animate().alpha(1).translationY(0).setDuration(250).start();
        toast.postDelayed(hideToast, 1800);
        toast.announceForAccessibility(message);
    }

    private void hideToast() {
        toast.animate().alpha(0).translationY(Ui.dp(20)).setDuration(250).start();
    }
}
