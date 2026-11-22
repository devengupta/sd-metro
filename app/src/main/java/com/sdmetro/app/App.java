package com.sdmetro.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide state: the parsed content, the store, and the signed-in account's session. */
final class App {
    interface Host {
        void onReady();
        void rerender();
        void toast(String message);
    }

    interface Reply {
        void ok(JSONObject json);
        void fail(Gen.Fail f);
    }

    /** SharedPreferences behind the KV interface; every write is committed so a failure is known. */
    static final class Prefs implements KV {
        private final SharedPreferences sp;
        Prefs(Context c, String name) { sp = c.getSharedPreferences(name, Context.MODE_PRIVATE); }
        @Override public String get(String key) { try { return sp.getString(key, null); } catch (Exception e) { return null; } }
        @Override public boolean put(String key, String value) { try { return sp.edit().putString(key, value).commit(); } catch (Exception e) { return false; } }
        @Override public boolean remove(String key) { try { return sp.edit().remove(key).commit(); } catch (Exception e) { return false; } }
    }

    static final String SAVE_FAILED = "This phone would not save that. Free up some storage.";
    private static App instance;

    static App get(Context c) {
        if (instance == null) instance = new App(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    /** Accounts, session, progress and API keys. */
    final KV store;
    /** Generated Ride lessons ("sdm.c.&lt;key&gt;.&lt;lens&gt;"), shared by all accounts on the phone. */
    final KV cache;
    final Accounts accounts;
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final Handler main = new Handler(Looper.getMainLooper());

    Roadmap rm;
    Content content;
    boolean ready, loading, loadFailed;

    Accounts.Account me;
    Progress S = new Progress();
    /** The signed-in account's API key, "" when none. Never kept past sign-out. */
    String apiKey = "";
    UiState ui = new UiState();
    /** Changes on every sign-in and sign-out; a Claude reply from an earlier value is dropped. */
    int epoch;
    Host host;

    private App(Context c) {
        ctx = c;
        store = new Prefs(c, "sdm");
        cache = new Prefs(c, "sdm_cache");
        accounts = new Accounts(store);
        ui.authTab = accounts.count() > 0 ? "in" : "up";
    }

    /** Reads and parses the bundled roadmap and lessons off the main thread, then restores the session. */
    void load() {
        if (ready || loading) return;
        loading = true;
        loadFailed = false;
        pool.execute(() -> {
            Roadmap r = null;
            Content c = null;
            try {
                r = Roadmap.parse(asset("roadmap.txt"));
                c = Content.parse(asset("content.json"));
            } catch (Exception e) {
                r = null;
            }
            final Roadmap fr = r;
            final Content fc = c;
            main.post(() -> {
                loading = false;
                if (fr == null || fc == null) {
                    loadFailed = true;
                } else {
                    rm = fr;
                    content = fc;
                    ready = true;
                    String email = accounts.session();
                    if (email != null) enter(email);
                }
                if (host != null) host.onReady();
            });
        });
    }

    private String asset(String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    long now() { return System.currentTimeMillis(); }

    String today() { return LocalDate.now().toString(); }

    void enter(String email) {
        me = accounts.get(email);
        if (me == null) return;
        if (!accounts.setSession(email)) toast(SAVE_FAILED);
        S = Progress.fromJson(store.get(Accounts.progressKey(me)));
        apiKey = accounts.apiKey(me);
        ui = new UiState();
        ui.owner = me.id;
        epoch++;
    }

    void signOut() {
        me = null;
        accounts.clearSession();
        S = new Progress();
        apiKey = "";
        ui = new UiState();
        epoch++;
    }

    /** Uses saved screen state, but only for the account it was saved for. */
    void adopt(UiState saved) {
        if (saved == null) return;
        String owner = me == null ? "" : me.id;
        if (!owner.equals(saved.owner)) return;
        saved.clearInFlight();
        ui = saved;
    }

    boolean hasAI() { return me != null && !apiKey.isEmpty(); }

    String aiStatus() {
        if (me == null) return "";
        return hasAI() ? "Claude key connected: each mode can also generate a fresh version." : "Everything here works offline.";
    }

    void toast(String m) { if (host != null) host.toast(m); }

    /** Writes progress without marking it as changed (used when only the last-opened stop moves). */
    void persist() {
        if (me == null) return;
        if (!store.put(Accounts.progressKey(me), S.toJson())) toast(SAVE_FAILED);
    }

    void save() {
        S.updated = now();
        persist();
    }

    void award(String key, char pip, int xp, String label) {
        int gain = S.award(key, pip, xp, now(), today());
        save();
        toast("+" + gain + " XP · " + label);
    }

    void addXp(int n, String label) {
        S.addXp(n, today());
        save();
        toast("+" + n + " XP · " + label);
    }

    static String cacheKey(String key, String lens) { return "sdm.c." + key + "." + lens; }

    /**
     * Asks Claude for one JSON object on a background thread and answers on the main thread.
     * A reply that lands after sign-out or another sign-in is dropped: it must not touch whoever is current now.
     */
    void ask(final String prompt, boolean quick, final Reply reply) {
        if (!hasAI()) { reply.fail(new Gen.Fail("not_granted", "")); return; }
        final int mine = epoch;
        final String key = apiKey;
        final String model = quick ? Gen.MODEL_QUICK : Gen.MODEL_DEFAULT;
        pool.execute(() -> {
            JSONObject json = null;
            Gen.Fail fail = null;
            try {
                json = Claude.askJson(prompt, model, key);
            } catch (Gen.Fail f) {
                fail = f;
            } catch (Throwable t) {
                fail = new Gen.Fail("unknown", String.valueOf(t.getMessage()));
            }
            final JSONObject fj = json;
            final Gen.Fail ff = fail;
            main.post(() -> {
                if (epoch != mine) return;
                try {
                    if (ff != null) reply.fail(ff); else reply.ok(fj);
                } catch (Throwable t) {
                    reply.fail(new Gen.Fail("unknown", String.valueOf(t.getMessage())));
                }
            });
        });
    }
}
