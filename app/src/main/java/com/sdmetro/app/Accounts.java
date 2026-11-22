package com.sdmetro.app;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Accounts on this device: "sdm.accounts" (email -> account), "sdm.session" (signed-in email),
 * "sdm.apikey.&lt;id&gt;" (that account's Anthropic key) and "sdm.v1.&lt;id&gt;" (its progress).
 */
public final class Accounts {
    public static final String AK = "sdm.accounts", SK = "sdm.session", KK = "sdm.apikey", PROGRESS = "sdm.v1";
    private static final Pattern EMAIL = Pattern.compile("^\\S+@\\S+\\.\\S+$");

    public static final class Account {
        public String id, name, email, salt, hash;
        public long created;
    }

    private final KV kv;
    private final Map<String, Account> all = new LinkedHashMap<>();

    public Accounts(KV kv) {
        this.kv = kv;
        try {
            String raw = kv.get(AK);
            if (raw != null) {
                JSONObject o = new JSONObject(raw);
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String email = it.next();
                    JSONObject v = o.optJSONObject(email);
                    if (v == null) continue;
                    Account a = new Account();
                    a.id = v.optString("id"); a.name = v.optString("name"); a.email = v.optString("email", email);
                    a.salt = v.optString("salt"); a.hash = v.optString("hash"); a.created = v.optLong("created", 0);
                    if (a.id.length() > 0) all.put(email, a);
                }
            }
        } catch (Exception ignored) { }
    }

    public int count() { return all.size(); }

    public Account get(String email) { return email == null ? null : all.get(email); }

    public static String cleanEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }

    /** Checks the form before any hashing. Returns the message to show, or null when it is fine. */
    public String validate(boolean signUp, String name, String email, String pw, String pw2) {
        email = cleanEmail(email);
        if (!EMAIL.matcher(email).matches()) return "Enter a valid email address.";
        if (signUp) {
            if (name == null || name.trim().isEmpty()) return "Enter your name.";
            if (pw == null || pw.length() < 6) return "Use a password of at least 6 characters.";
            if (!pw.equals(pw2)) return "The two passwords do not match.";
            if (all.containsKey(email)) return "An account with this email already exists on this phone. Sign in instead.";
        } else {
            if (!all.containsKey(email)) return "No account with this email on this phone. Create one first.";
        }
        return null;
    }

    /** The slow part (password hashing): call off the main thread, after validate(). Returns an error message or null. */
    public String finish(boolean signUp, String name, String email, String pw, long now) {
        email = cleanEmail(email);
        try {
            if (signUp) {
                Account a = new Account();
                a.salt = Crypto.randHex(16);
                a.hash = Crypto.hash(pw, a.salt);
                a.id = Crypto.randHex(6); a.name = name.trim(); a.email = email; a.created = now;
                synchronized (this) {
                    if (all.containsKey(email)) return "An account with this email already exists on this phone. Sign in instead.";
                    all.put(email, a);
                    if (!save()) {
                        all.remove(email);
                        return "This phone would not save the account. Free up some storage and try again.";
                    }
                }
            } else {
                Account a = all.get(email);
                if (a == null) return "No account with this email on this phone. Create one first.";
                if (!Crypto.hash(pw, a.salt).equals(a.hash)) return "Wrong password.";
            }
        } catch (Exception e) {
            return "Could not check the password on this device. Try again.";
        }
        return null;
    }

    private boolean save() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Account> e : all.entrySet()) {
                Account a = e.getValue();
                o.put(e.getKey(), new JSONObject().put("id", a.id).put("name", a.name).put("email", a.email)
                        .put("salt", a.salt).put("hash", a.hash).put("created", a.created));
            }
            return kv.put(AK, o.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** Email of the signed-in account, or null. */
    public String session() {
        String e = readString(SK);
        return e != null && all.containsKey(e) ? e : null;
    }

    public boolean setSession(String email) { return kv.put(SK, JSONObject.quote(email)); }

    public void clearSession() { kv.remove(SK); }

    public static String progressKey(Account a) { return PROGRESS + "." + a.id; }

    private static String apiKeyKey(Account a) { return KK + "." + a.id; }

    /** This account's API key, or "" when none is saved. The key belongs to one account, not to the phone. */
    public String apiKey(Account a) { String k = readString(apiKeyKey(a)); return k == null ? "" : k; }

    public boolean setApiKey(Account a, String key) { return kv.put(apiKeyKey(a), JSONObject.quote(key)); }

    public void removeApiKey(Account a) { kv.remove(apiKeyKey(a)); }

    /** Removes the account with its progress and API key. */
    public synchronized boolean delete(Account a) {
        kv.remove(progressKey(a));
        kv.remove(apiKeyKey(a));
        all.remove(a.email);
        return save();
    }

    private String readString(String key) {
        try {
            String raw = kv.get(key);
            if (raw == null) return null;
            Object v = new JSONTokener(raw).nextValue();
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }
}
