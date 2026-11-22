package com.sdmetro.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude-generated modes: the prompts, and strict reading of the replies.
 * A reply whose shape is wrong is rejected here, before anything is cached or drawn. Pure Java.
 */
public final class Gen {
    private Gen() { }

    public static final String MODEL_QUICK = "claude-haiku-5-5", MODEL_DEFAULT = "claude-sonnet-5-5";
    public static final String JSON_ONLY = "Reply with ONLY one JSON object. No prose before or after, no code fences.\n\n";
    public static final String[][] LENSES = {
        {"plain", "Straight engineering"}, {"dabba", "Mumbai dabbawalas"}, {"cricket", "A cricket match"},
        {"kitchen", "Restaurant kitchen"}, {"railways", "Indian Railways"}, {"wedding", "Big Indian wedding"}
    };

    /** A failed generation: code as in the earlier versions (not_granted, bad_key, offline, rate_limited, bad_output, truncated, signed_out, unknown). */
    public static final class Fail extends Exception {
        private static final long serialVersionUID = 1L;
        public final String code;
        public Fail(String code, String message) { super(message); this.code = code; }
    }

    private static Fail badShape() { return new Fail("bad_output", "Claude replied in an unexpected format."); }

    public static String lensName(String lens) {
        for (String[] l : LENSES) if (l[0].equals(lens)) return l[1];
        return LENSES[0][1];
    }

    /** What the user reads when a generation fails. For not_granted and bad_key the word "Profile" is a link. */
    public static String errText(String code, String message) {
        if ("short".equals(code)) return "Give Ravi at least a sentence or two.";
        if ("not_granted".equals(code)) return "This mode needs your Anthropic API key. Add it in Profile.";
        if ("bad_key".equals(code)) return "Anthropic rejected the API key. Check it in Profile.";
        if ("offline".equals(code)) return "No internet connection. This mode needs to reach Claude; the Arcade and reviews work offline.";
        if ("rate_limited".equals(code) || "resource_exhausted".equals(code)) return "Claude is busy right now. Wait a minute, then try again.";
        String m = message != null && !message.isEmpty() ? message : code != null && !code.isEmpty() ? code : "unknown error";
        return "That run did not come through (" + m + "). Try again.";
    }

    public static boolean linksToProfile(String code) { return "not_granted".equals(code) || "bad_key".equals(code); }

    /* ---------------- prompts ---------------- */

    public static String ctxOf(Roadmap.Stop t, Roadmap rm) {
        return "the topic \"" + t.name + "\" from the system design roadmap section \"" + rm.sec(t.sec).name + "\""
                + (t.parent != null ? ", under \"" + t.parent.name + "\"" : "");
    }

    public static String ridePrompt(String ctx, String lens) {
        boolean plain = "plain".equals(lens);
        return "You are an outstanding system design teacher. Teach " + ctx + ".\n"
                + "Learner: preparing for system design interviews; smart, but new to this concept.\n"
                + "Lens: " + (plain ? "no analogy, straight engineering intuition"
                        : "build the intuition through \"" + lensName(lens) + "\" (an Indian everyday context), mapped carefully onto the real mechanics") + ".\n"
                + "JSON shape:\n"
                + "{\"tagline\": \"what it is, max 12 words\",\n"
                + " \"explain\": [\"paragraph 1: intuition" + (plain ? "" : " through the lens") + "\", \"paragraph 2: how it really works\", \"paragraph 3: when to use it and its trade-off\"],  (each paragraph under 60 words, plain text)\n"
                + " \"map\": [{\"lens\":\"element of the analogy\",\"real\":\"technical counterpart\"}]  (3 items, or [] if no analogy),\n"
                + " \"rule_of_thumb\": \"one widely documented number, limit or heuristic engineers use for this; empty string if none is well established\",\n"
                + " \"seen_in\": \"one well-known real system that uses it and how, max 25 words\",\n"
                + " \"trap\": \"the most common interview mistake about it, max 25 words\",\n"
                + " \"remember\": \"a memorable one-line hook, max 18 words\",\n"
                + " \"check\": [{\"q\":\"short scenario question\",\"options\":[\"\",\"\",\"\",\"\"],\"answer\":0,\"why\":\"max 30 words\"}, {second question}]}\n"
                + "Questions should test application, not definitions. Put the correct option at varied positions. Only state facts you are confident are accurate.";
    }

    public static String pagerStartPrompt(String ctx, String stopName) {
        return "Run a production incident simulation that teaches " + ctx + ". The learner is the on-call engineer.\n"
                + "Invent a fictional company (an Indian consumer app context is welcome) and a realistic incident where understanding \"" + stopName + "\" is the key to resolving it. Never name the concept in the alert or context.\n"
                + "JSON shape: {\"company\":\"fictional name\",\"service\":\"service name\",\"alert\":\"pager text, max 20 words\",\"context\":\"what is happening and what you can see, max 55 words\",\"metrics\":[{\"name\":\"\",\"value\":\"\",\"state\":\"bad|warn|ok\"}] (3 or 4),\"choices\":[\"\",\"\",\"\"]}\n"
                + "Choices: 3 concrete actions, max 18 words each: one strong, one tempting but wrong, one partial. Random order. No hints about which is which.";
    }

    /** The rounds so far, as the model sees them. The last round carries the choice just made and no result yet. */
    public static String historyJson(List<Round> rounds) {
        try {
            JSONArray a = new JSONArray();
            for (int i = 0; i < rounds.size(); i++) {
                Round x = rounds.get(i);
                JSONObject o = new JSONObject();
                o.put("round", i + 1);
                o.put("alert", x.alert);
                o.put("context", x.context);
                o.put("choices", new JSONArray(x.choices));
                if (x.chosen >= 0 && x.chosen < x.choices.size()) o.put("chosen", x.choices.get(x.chosen));
                if (x.quality != null) o.put("result", x.outcome);
                a.put(o);
            }
            return a.toString();
        } catch (JSONException e) {
            return "[]";
        }
    }

    public static String pagerChoosePrompt(String ctx, String company, int n, String historyJson, String choice) {
        return "Incident simulation teaching " + ctx + ". Company: " + company + ". This is round " + n + " of at most 3.\n"
                + "History so far: " + historyJson + "\n"
                + "The learner just chose: \"" + choice + "\".\n"
                + "Judge that choice honestly, as a senior SRE would, then continue the story.\n"
                + "JSON shape: {\"quality\":\"strong|partial|wrong\",\"outcome\":\"what happens because of the choice, max 45 words\",\"lesson\":\"the underlying principle, max 30 words\",\n"
                + " \"next\": " + (n >= 3 ? "null" : "null if the incident is fully resolved, otherwise {\"alert\":\"\",\"context\":\"\",\"metrics\":[{\"name\":\"\",\"value\":\"\",\"state\":\"bad|warn|ok\"}],\"choices\":[\"\",\"\",\"\"]} with the same rules as before (one strong, one tempting but wrong, one partial, random order)") + ",\n"
                + " \"debrief\": null unless next is null; then {\"survived\":true or false,\"summary\":\"how the night went, max 50 words\",\"better_move\":\"the ideal sequence of moves, max 35 words\"}}";
    }

    public static String teachAskPrompt(String ctx) {
        return "You play Ravi, a sharp junior engineer six months out of college, asking a senior colleague about " + ctx + ".\n"
                + "Ask ONE question that forces a real explanation: a \"why\", \"what happens when\" or \"how would you choose\" question, the kind an interviewer would ask. Casual, friendly tone.\n"
                + "JSON shape: {\"question\":\"max 35 words\"}";
    }

    public static String teachGradePrompt(String ctx, String question, String answer) {
        String a = answer.length() > 3000 ? answer.substring(0, 3000) : answer;
        return "Grade a learner's explanation of " + ctx + ", as a fair but demanding system design interviewer would.\n"
                + "Question (asked by Ravi, a junior engineer): \"" + question + "\"\n"
                + "Learner's answer: \"\"\"" + a + "\"\"\"\n"
                + "Scoring: 0 = wrong or empty, 1 = vague or partly wrong, 2 = correct and clear, 3 = correct with trade-offs or edge cases.\n"
                + "JSON shape: {\"score\":0,\"reaction\":\"Ravi's honest reply in his voice; if something is missing, he asks about it. max 30 words\",\"nailed\":[\"up to 3 short items\"],\"missing\":[\"up to 3 short items\"],\"model_answer\":\"a crisp answer in 3 sentences\"}";
    }

    /* ---------------- reply shapes ---------------- */

    public static final class Check implements Serializable {
        private static final long serialVersionUID = 1L;
        public String q, why;
        public String[] options;
        public int answer;
    }

    public static final class Ride implements Serializable {
        private static final long serialVersionUID = 1L;
        public String tagline, ruleOfThumb, seenIn, trap, remember;
        public String[] explain;
        /** Pairs: element of the analogy, technical counterpart. */
        public String[][] map;
        public Check[] check;
        /** The reply as received, for the cache. */
        public String json;
    }

    public static final class Round implements Serializable {
        private static final long serialVersionUID = 1L;
        public String alert, context;
        /** Triples: name, value, state (bad, warn or ok). */
        public ArrayList<String[]> metrics = new ArrayList<>();
        public ArrayList<String> choices = new ArrayList<>();
        /** Index of the chosen move, or -1. */
        public int chosen = -1;
        /** Set once the choice is judged: strong, partial or wrong. */
        public String quality, outcome, lesson;
    }

    public static final class PagerStart {
        public String company, service;
        public Round round;
    }

    public static final class Debrief implements Serializable {
        private static final long serialVersionUID = 1L;
        public boolean survived;
        public String summary, betterMove;
    }

    public static final class Judge {
        public String quality, outcome, lesson;
        /** The next round, or null when the incident is over. */
        public Round next;
        public Debrief debrief;
    }

    public static final class Grade implements Serializable {
        private static final long serialVersionUID = 1L;
        public int score;
        public String reaction, modelAnswer;
        public String[] nailed, missing;
    }

    /** The JSON object inside a model reply: from the first "{" to the last "}". */
    public static JSONObject extractJson(String txt) throws Fail {
        if (txt == null) txt = "";
        int a = txt.indexOf('{'), b = txt.lastIndexOf('}');
        if (a < 0 || b < a) throw badShape();
        try {
            return new JSONObject(txt.substring(a, b + 1));
        } catch (JSONException e) {
            throw badShape();
        }
    }

    private static boolean absent(Object v) { return v == null || v == JSONObject.NULL; }

    /** A text field. Numbers and booleans are shown as text; arrays and objects are a wrong shape. */
    private static String str(JSONObject o, String key, boolean required) throws Fail {
        Object v = o.opt(key);
        if (absent(v)) { if (required) throw badShape(); return ""; }
        if (v instanceof JSONObject || v instanceof JSONArray) throw badShape();
        String s = String.valueOf(v);
        if (required && s.trim().isEmpty()) throw badShape();
        return s;
    }

    private static String[] strings(JSONObject o, String key, int min) throws Fail {
        Object v = o.opt(key);
        if (absent(v)) { if (min > 0) throw badShape(); return new String[0]; }
        if (!(v instanceof JSONArray)) throw badShape();
        JSONArray a = (JSONArray) v;
        if (a.length() < min) throw badShape();
        String[] out = new String[a.length()];
        for (int i = 0; i < out.length; i++) {
            Object x = a.opt(i);
            if (absent(x) || x instanceof JSONObject || x instanceof JSONArray) throw badShape();
            out[i] = String.valueOf(x);
        }
        return out;
    }

    /** A generated Ride. Everything the screen reads is checked: explain and check must be non-empty lists, map a list (or absent). */
    public static Ride ride(JSONObject d) throws Fail {
        if (d == null) throw badShape();
        Ride r = new Ride();
        r.tagline = str(d, "tagline", true);
        r.explain = strings(d, "explain", 1);
        Object m = d.opt("map");
        if (absent(m)) {
            r.map = new String[0][];
        } else {
            if (!(m instanceof JSONArray)) throw badShape();
            JSONArray a = (JSONArray) m;
            r.map = new String[a.length()][];
            for (int i = 0; i < a.length(); i++) {
                JSONObject row = a.optJSONObject(i);
                if (row == null) throw badShape();
                r.map[i] = new String[]{str(row, "lens", true), str(row, "real", true)};
            }
        }
        r.ruleOfThumb = str(d, "rule_of_thumb", false);
        r.seenIn = str(d, "seen_in", false);
        r.trap = str(d, "trap", false);
        r.remember = str(d, "remember", true);
        Object c = d.opt("check");
        if (!(c instanceof JSONArray) || ((JSONArray) c).length() == 0) throw badShape();
        JSONArray ca = (JSONArray) c;
        r.check = new Check[ca.length()];
        for (int i = 0; i < ca.length(); i++) {
            JSONObject q = ca.optJSONObject(i);
            if (q == null) throw badShape();
            Check k = new Check();
            k.q = str(q, "q", true);
            k.options = strings(q, "options", 2);
            k.why = str(q, "why", false);
            Object ans = q.opt("answer");
            if (!(ans instanceof Number)) throw badShape();
            double av = ((Number) ans).doubleValue();
            if (av != Math.rint(av) || av < 0 || av >= k.options.length) throw badShape();
            k.answer = (int) av;
            r.check[i] = k;
        }
        r.json = d.toString();
        return r;
    }

    /** One pager round: alert, context, metrics and two to four moves. */
    public static Round round(JSONObject d) throws Fail {
        if (d == null) throw badShape();
        Round r = new Round();
        r.alert = str(d, "alert", true);
        r.context = str(d, "context", true);
        Object m = d.opt("metrics");
        if (!absent(m)) {
            if (!(m instanceof JSONArray)) throw badShape();
            JSONArray a = (JSONArray) m;
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) throw badShape();
                String state = str(x, "state", false);
                if (!"bad".equals(state) && !"warn".equals(state) && !"ok".equals(state)) state = "";
                r.metrics.add(new String[]{str(x, "name", false), str(x, "value", false), state});
            }
        }
        String[] ch = strings(d, "choices", 2);
        if (ch.length > 4) throw badShape();
        for (String s : ch) { if (s.trim().isEmpty()) throw badShape(); r.choices.add(s); }
        return r;
    }

    public static PagerStart pagerStart(JSONObject d) throws Fail {
        if (d == null) throw badShape();
        PagerStart p = new PagerStart();
        p.company = str(d, "company", true);
        p.service = str(d, "service", false);
        p.round = round(d);
        return p;
    }

    /** The verdict on a move in round n (1..3), with either the next round or the debrief. */
    public static Judge judge(JSONObject d, int n) throws Fail {
        if (d == null) throw badShape();
        Judge j = new Judge();
        String q = str(d, "quality", false);
        j.quality = "strong".equals(q) || "partial".equals(q) || "wrong".equals(q) ? q : "partial";
        j.outcome = str(d, "outcome", true);
        j.lesson = str(d, "lesson", true);
        Object next = d.opt("next");
        if (!absent(next) && n < 3) {
            if (!(next instanceof JSONObject)) throw badShape();
            j.next = round((JSONObject) next);
            return j;
        }
        Object db = d.opt("debrief");
        j.debrief = new Debrief();
        if (absent(db)) {
            j.debrief.survived = "strong".equals(j.quality);
            j.debrief.summary = j.outcome;
            j.debrief.betterMove = j.lesson;
        } else {
            if (!(db instanceof JSONObject)) throw badShape();
            JSONObject o = (JSONObject) db;
            Object s = o.opt("survived");
            j.debrief.survived = s instanceof Boolean && (Boolean) s;
            j.debrief.summary = str(o, "summary", true);
            j.debrief.betterMove = str(o, "better_move", true);
        }
        return j;
    }

    public static String question(JSONObject d) throws Fail {
        if (d == null) throw badShape();
        return str(d, "question", true);
    }

    /** Ravi's grade; the score is clamped to 0..3. */
    public static Grade grade(JSONObject d) throws Fail {
        if (d == null) throw badShape();
        Grade g = new Grade();
        Object s = d.opt("score");
        double v = 0;
        if (s instanceof Number) v = ((Number) s).doubleValue();
        else if (s instanceof String) { try { v = Double.parseDouble(((String) s).trim()); } catch (NumberFormatException e) { v = 0; } }
        else if (!absent(s)) throw badShape();
        if (Double.isNaN(v)) v = 0;
        g.score = (int) Math.max(0, Math.min(3, Math.round(v)));
        g.reaction = str(d, "reaction", true);
        g.nailed = strings(d, "nailed", 0);
        g.missing = strings(d, "missing", 0);
        g.modelAnswer = str(d, "model_answer", false);
        return g;
    }

    /* ---------------- built-in pager ---------------- */

    public static final String[] COMPANIES = {"ChaiCart", "RickshawGo", "TiffinBox", "PaisaPay", "KiranaKart", "CricScore Live", "BazaarBolt", "MetroMeals"};
    public static final String[] QUALS = {"strong", "wrong", "partial"};

    /** The fictional company of a stop's built-in incident: stable per content key. */
    public static String companyFor(String key) {
        long h = 0;
        for (int i = 0; i < key.length(); i++) h = (h * 31 + key.charAt(i)) & 0xFFFFFFFFL;
        return COMPANIES[(int) (h % COMPANIES.length)];
    }

    /** A built-in move: {text, consequence, quality}. src is 0 strong, 1 wrong, 2 partial. */
    public static String[] builtinMove(Content.Entry c, int src) {
        String raw = c.i[2 + src];
        int at = raw.indexOf(" => ");
        String text = at < 0 ? raw : raw.substring(0, at);
        String cons = "The dashboards settle and the page clears.";
        if (at >= 0) {
            String rest = raw.substring(at + 4);
            int more = rest.indexOf(" => ");
            String second = more < 0 ? rest : rest.substring(0, more);
            if (!second.isEmpty()) cons = second;
        }
        return new String[]{text, cons, QUALS[src]};
    }
}
