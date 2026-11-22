package com.sdmetro.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** The built-in lesson for every content key. Pure Java (org.json only). */
public final class Content {
    public static final class Entry {
        /** Explanation paragraphs. */
        public String[] e;
        /** Tagline, interview trap, pocket card. */
        public String t, p, k;
        /** Question, correct option, three wrong options, why. */
        public String[] q;
        /** Alert, context, strong move, "wrong => consequence", "partial => consequence", lesson. */
        public String[] i;
        /** Ravi's question and three key points. */
        public String[] a;
    }

    private final Map<String, Entry> byKey = new HashMap<>();

    public static Content parse(String json) throws JSONException {
        Content c = new Content();
        JSONObject o = new JSONObject(json);
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject v = o.getJSONObject(key);
            Entry e = new Entry();
            e.e = strings(v.getJSONArray("e"));
            e.t = v.getString("t");
            e.p = v.getString("p");
            e.k = v.getString("k");
            e.q = strings(v.getJSONArray("q"));
            e.i = strings(v.getJSONArray("i"));
            e.a = strings(v.getJSONArray("a"));
            if (e.q.length < 6 || e.i.length < 6 || e.a.length < 4) throw new JSONException("short entry: " + key);
            c.byKey.put(key, e);
        }
        return c;
    }

    private static String[] strings(JSONArray a) throws JSONException {
        String[] out = new String[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.getString(i);
        return out;
    }

    public Entry get(String key) { return byKey.get(key); }

    public int size() { return byKey.size(); }

    public Iterable<String> keys() { return byKey.keySet(); }
}
