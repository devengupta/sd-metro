package com.sdmetro.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;

public class GenTest {
    private static final String RIDE = "{\"tagline\":\"A sorted shortcut to rows\",\"explain\":[\"p1\",\"p2\",\"p3\"],"
            + "\"map\":[{\"lens\":\"dabba code\",\"real\":\"index key\"}],\"rule_of_thumb\":\"Index what you filter on\",\"seen_in\":\"PostgreSQL B-trees\","
            + "\"trap\":\"Indexing everything\",\"remember\":\"Indexes trade writes for reads\","
            + "\"check\":[{\"q\":\"Q1\",\"options\":[\"a\",\"b\",\"c\",\"d\"],\"answer\":2,\"why\":\"because\"},{\"q\":\"Q2\",\"options\":[\"a\",\"b\"],\"answer\":0,\"why\":\"so\"}]}";
    private static final String ROUND = "\"alert\":\"p99 latency 4s on orders\",\"context\":\"Scans everywhere\",\"metrics\":[{\"name\":\"p99\",\"value\":\"4s\",\"state\":\"bad\"},{\"name\":\"cpu\",\"value\":95,\"state\":\"hot\"}],\"choices\":[\"Add index\",\"Restart\",\"Cache\"]";

    private static JSONObject obj(String s) throws Exception { return new JSONObject(s); }

    private static JSONObject ride() throws Exception { return obj(RIDE); }

    private static void rejected(String what, ThrowingCall c) {
        try {
            c.run();
            fail("accepted: " + what);
        } catch (Gen.Fail f) {
            assertEquals(what, "bad_output", f.code);
            assertEquals("Claude replied in an unexpected format.", f.getMessage());
        } catch (Exception e) {
            fail(what + " threw " + e);
        }
    }

    interface ThrowingCall { void run() throws Exception; }

    /* ---------------- prompts and messages: compared with what the web version's own code produced ---------------- */

    @Test public void promptsAndErrorTextsMatchTheOriginal() throws Exception {
        // prompts.json was produced by evaluating the template strings and errText() of the last WebView build (assets/index.html).
        JSONArray cases = new JSONArray(TestData.resource("prompts.json"));
        int seen = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String kind = c.getString("kind"), got;
            switch (kind) {
                case "ride": got = Gen.ridePrompt(c.getString("ctx"), c.getString("lens")); break;
                case "pagerStart": got = Gen.pagerStartPrompt(c.getString("ctx"), c.getString("name")); break;
                case "pagerChoose": got = Gen.pagerChoosePrompt(c.getString("ctx"), c.getString("company"), c.getInt("n"), c.getString("history"), c.getString("choice")); break;
                case "teachAsk": got = Gen.teachAskPrompt(c.getString("ctx")); break;
                case "teachGrade": got = Gen.teachGradePrompt(c.getString("ctx"), c.getString("question"), c.getString("answer")); break;
                case "err": got = Gen.errText(c.getString("code"), c.getString("message")); break;
                default: throw new AssertionError(kind);
            }
            assertEquals(kind + " #" + i, c.getString("expect"), got);
            seen++;
        }
        assertEquals(19, seen);
    }

    @Test public void contextLineAndModels() throws Exception {
        Roadmap rm = TestData.roadmap();
        assertEquals("the topic \"How the Internet Works\" from the system design roadmap section \"Computer Science Fundamentals\"", Gen.ctxOf(rm.stop(1, 0), rm));
        assertEquals("the topic \"PostgreSQL\" from the system design roadmap section \"Database Fundamentals\", under \"Relational Databases\"", Gen.ctxOf(rm.stop(5, 1), rm));
        assertEquals("claude-haiku-5-5", Gen.MODEL_QUICK);
        assertEquals("claude-sonnet-5-5", Gen.MODEL_DEFAULT);
        assertEquals("Reply with ONLY one JSON object. No prose before or after, no code fences.\n\n", Gen.JSON_ONLY);
        assertEquals(6, Gen.LENSES.length);
        assertEquals("Mumbai dabbawalas", Gen.lensName("dabba"));
        assertEquals("Straight engineering", Gen.lensName("nonsense"));
        assertTrue(Gen.linksToProfile("bad_key"));
        assertTrue(Gen.linksToProfile("not_granted"));
        assertFalse(Gen.linksToProfile("offline"));
        assertEquals("That run did not come through (the reply was cut off before it finished). Try again.", Gen.errText("truncated", "the reply was cut off before it finished"));
    }

    @Test public void historySentToTheModel() throws Exception {
        ArrayList<Gen.Round> rounds = new ArrayList<>();
        Gen.Round a = Gen.round(obj("{" + ROUND + "}"));
        a.chosen = 2; a.quality = "partial"; a.outcome = "Helps a little"; a.lesson = "l";
        Gen.Round b = Gen.round(obj("{" + ROUND + "}"));
        b.chosen = 0;
        rounds.add(a); rounds.add(b);
        JSONArray h = new JSONArray(Gen.historyJson(rounds));
        assertEquals(2, h.length());
        assertEquals(1, h.getJSONObject(0).getInt("round"));
        assertEquals("Cache", h.getJSONObject(0).getString("chosen"));
        assertEquals("Helps a little", h.getJSONObject(0).getString("result"));
        assertEquals(3, h.getJSONObject(0).getJSONArray("choices").length());
        assertEquals("Add index", h.getJSONObject(1).getString("chosen"));
        assertFalse(h.getJSONObject(1).has("result"));
    }

    /* ---------------- reading the reply ---------------- */

    @Test public void extractsTheObjectFromSurroundingText() throws Exception {
        assertEquals("x", Gen.extractJson("Sure! ```json\n{\"question\":\"x\"}\n``` hope that helps").getString("question"));
        assertEquals(1, Gen.extractJson("{\"a\":{\"b\":1}}").getJSONObject("a").getInt("b"));
        rejected("no braces", () -> Gen.extractJson("no json here"));
        rejected("empty", () -> Gen.extractJson(""));
        rejected("null", () -> Gen.extractJson(null));
        rejected("backwards", () -> Gen.extractJson("} {"));
        rejected("cut off", () -> Gen.extractJson("{\"tagline\":\"a\",\"explain\":[\"p1\","));
        rejected("garbage in braces", () -> Gen.extractJson("{this is not json}"));
    }

    @Test public void acceptsAWellFormedRide() throws Exception {
        Gen.Ride r = Gen.ride(ride());
        assertEquals("A sorted shortcut to rows", r.tagline);
        assertArrayEquals(new String[]{"p1", "p2", "p3"}, r.explain);
        assertEquals(1, r.map.length);
        assertArrayEquals(new String[]{"dabba code", "index key"}, r.map[0]);
        assertEquals("Index what you filter on", r.ruleOfThumb);
        assertEquals("Indexes trade writes for reads", r.remember);
        assertEquals(2, r.check.length);
        assertEquals(2, r.check[0].answer);
        assertEquals(4, r.check[0].options.length);
        // what goes to the cache reads back the same
        Gen.Ride again = Gen.ride(new JSONObject(r.json));
        assertEquals(r.tagline, again.tagline);
        assertEquals(2, again.check.length);
    }

    @Test public void rideOptionalPartsMayBeMissing() throws Exception {
        JSONObject d = ride();
        d.remove("map"); d.remove("rule_of_thumb"); d.put("seen_in", JSONObject.NULL); d.put("trap", "");
        Gen.Ride r = Gen.ride(d);
        assertEquals(0, r.map.length);
        assertEquals("", r.ruleOfThumb);
        assertEquals("", r.seenIn);
        JSONObject e = ride();
        e.put("map", new JSONArray());
        assertEquals(0, Gen.ride(e).map.length);
    }

    @Test public void rejectsRidesOfTheWrongShape() throws Exception {
        rejected("explain as string", () -> Gen.ride(ride().put("explain", "one paragraph")));
        rejected("explain as object", () -> Gen.ride(ride().put("explain", new JSONObject().put("0", "p"))));
        rejected("explain empty", () -> Gen.ride(ride().put("explain", new JSONArray())));
        rejected("explain missing", () -> { JSONObject d = ride(); d.remove("explain"); Gen.ride(d); });
        rejected("explain of objects", () -> Gen.ride(ride().put("explain", new JSONArray().put(new JSONObject()))));
        rejected("map as string", () -> Gen.ride(ride().put("map", "none")));
        rejected("map as object", () -> Gen.ride(ride().put("map", new JSONObject().put("lens", "a").put("real", "b"))));
        rejected("map of strings", () -> Gen.ride(ride().put("map", new JSONArray().put("a -> b"))));
        rejected("map row without real", () -> Gen.ride(ride().put("map", new JSONArray().put(new JSONObject().put("lens", "a")))));
        rejected("tagline missing", () -> { JSONObject d = ride(); d.remove("tagline"); Gen.ride(d); });
        rejected("tagline as array", () -> Gen.ride(ride().put("tagline", new JSONArray().put("x"))));
        rejected("remember missing", () -> { JSONObject d = ride(); d.remove("remember"); Gen.ride(d); });
        rejected("trap as object", () -> Gen.ride(ride().put("trap", new JSONObject())));
        rejected("check missing", () -> { JSONObject d = ride(); d.remove("check"); Gen.ride(d); });
        rejected("check empty", () -> Gen.ride(ride().put("check", new JSONArray())));
        rejected("check as object", () -> Gen.ride(ride().put("check", new JSONObject())));
        rejected("check of strings", () -> Gen.ride(ride().put("check", new JSONArray().put("q?"))));
        rejected("options as string", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("options", "a,b,c"); Gen.ride(d); });
        rejected("one option", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("options", new JSONArray().put("a")); Gen.ride(d); });
        rejected("answer out of range", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("answer", 4); Gen.ride(d); });
        rejected("answer negative", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("answer", -1); Gen.ride(d); });
        rejected("answer as text", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("answer", "b"); Gen.ride(d); });
        rejected("answer fractional", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(0).put("answer", 1.5); Gen.ride(d); });
        rejected("answer missing", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(1).remove("answer"); Gen.ride(d); });
        rejected("question missing", () -> { JSONObject d = ride(); d.getJSONArray("check").getJSONObject(1).remove("q"); Gen.ride(d); });
        rejected("null object", () -> Gen.ride(null));
        rejected("empty object", () -> Gen.ride(new JSONObject()));
    }

    @Test public void pagerStartAndRounds() throws Exception {
        Gen.PagerStart p = Gen.pagerStart(obj("{\"company\":\"ChaiCart\",\"service\":\"orders-api\"," + ROUND + "}"));
        assertEquals("ChaiCart", p.company);
        assertEquals("orders-api", p.service);
        assertEquals("p99 latency 4s on orders", p.round.alert);
        assertEquals(3, p.round.choices.size());
        assertEquals(-1, p.round.chosen);
        assertNull(p.round.quality);
        assertEquals(2, p.round.metrics.size());
        assertArrayEquals(new String[]{"p99", "4s", "bad"}, p.round.metrics.get(0));
        assertArrayEquals(new String[]{"cpu", "95", ""}, p.round.metrics.get(1));   // number shown as text, unknown state dropped

        assertEquals("", Gen.pagerStart(obj("{\"company\":\"C\"," + ROUND + "}")).service);
        assertEquals(0, Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":[\"x\",\"y\"]}")).metrics.size());
        rejected("company missing", () -> Gen.pagerStart(obj("{" + ROUND + "}")));
        rejected("choices as string", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":\"x, y, z\"}")));
        rejected("choices missing", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\"}")));
        rejected("one choice", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":[\"x\"]}")));
        rejected("five choices", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}")));
        rejected("blank choice", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":[\"x\",\" \"]}")));
        rejected("choices of objects", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"choices\":[{\"t\":\"x\"},{\"t\":\"y\"}]}")));
        rejected("metrics as object", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"metrics\":{\"p99\":\"4s\"},\"choices\":[\"x\",\"y\"]}")));
        rejected("metrics of strings", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":\"c\",\"metrics\":[\"p99 4s\"],\"choices\":[\"x\",\"y\"]}")));
        rejected("alert missing", () -> Gen.round(obj("{\"context\":\"c\",\"choices\":[\"x\",\"y\"]}")));
        rejected("context as array", () -> Gen.round(obj("{\"alert\":\"a\",\"context\":[\"c\"],\"choices\":[\"x\",\"y\"]}")));
    }

    @Test public void judgingAMove() throws Exception {
        Gen.Judge next = Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"next\":{" + ROUND + "},\"debrief\":null}"), 1);
        assertEquals("strong", next.quality);
        assertNotNull(next.next);
        assertNull(next.debrief);

        Gen.Judge end = Gen.judge(obj("{\"quality\":\"wrong\",\"outcome\":\"o\",\"lesson\":\"l\",\"next\":null,\"debrief\":{\"survived\":false,\"summary\":\"rough night\",\"better_move\":\"index first\"}}"), 2);
        assertNull(end.next);
        assertFalse(end.debrief.survived);
        assertEquals("rough night", end.debrief.summary);
        assertEquals("index first", end.debrief.betterMove);

        // round 3 always ends, even if the model offers another round
        Gen.Judge third = Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"next\":{" + ROUND + "},\"debrief\":{\"survived\":true,\"summary\":\"s\",\"better_move\":\"b\"}}"), 3);
        assertNull(third.next);
        assertTrue(third.debrief.survived);

        // no debrief given: built from the verdict, as before
        Gen.Judge bare = Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"fixed\",\"lesson\":\"measure first\"}"), 2);
        assertTrue(bare.debrief.survived);
        assertEquals("fixed", bare.debrief.summary);
        assertEquals("measure first", bare.debrief.betterMove);
        assertFalse(Gen.judge(obj("{\"quality\":\"partial\",\"outcome\":\"o\",\"lesson\":\"l\"}"), 1).debrief.survived);

        assertEquals("partial", Gen.judge(obj("{\"quality\":\"excellent\",\"outcome\":\"o\",\"lesson\":\"l\"}"), 1).quality);
        assertFalse(Gen.judge(obj("{\"outcome\":\"o\",\"lesson\":\"l\",\"debrief\":{\"survived\":\"yes\",\"summary\":\"s\",\"better_move\":\"b\"}}"), 1).debrief.survived);

        rejected("outcome missing", () -> Gen.judge(obj("{\"quality\":\"strong\",\"lesson\":\"l\"}"), 1));
        rejected("lesson as object", () -> Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":{}}"), 1));
        rejected("next as string", () -> Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"next\":\"more\"}"), 1));
        rejected("next without choices", () -> Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"next\":{\"alert\":\"a\",\"context\":\"c\"}}"), 1));
        rejected("debrief as string", () -> Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"debrief\":\"fine\"}"), 1));
        rejected("debrief without summary", () -> Gen.judge(obj("{\"quality\":\"strong\",\"outcome\":\"o\",\"lesson\":\"l\",\"debrief\":{\"survived\":true}}"), 1));
    }

    @Test public void teachQuestionAndGrade() throws Exception {
        assertEquals("Why not index everything?", Gen.question(obj("{\"question\":\"Why not index everything?\"}")));
        rejected("question missing", () -> Gen.question(obj("{\"q\":\"x\"}")));
        rejected("question blank", () -> Gen.question(obj("{\"question\":\"  \"}")));
        rejected("question as array", () -> Gen.question(obj("{\"question\":[\"x\"]}")));

        Gen.Grade g = Gen.grade(obj("{\"score\":2,\"reaction\":\"Oh, got it\",\"nailed\":[\"a\",\"b\"],\"missing\":[\"c\"],\"model_answer\":\"m\"}"));
        assertEquals(2, g.score);
        assertEquals("Oh, got it", g.reaction);
        assertArrayEquals(new String[]{"a", "b"}, g.nailed);
        assertArrayEquals(new String[]{"c"}, g.missing);
        assertEquals("m", g.modelAnswer);

        String rest = ",\"reaction\":\"r\"}";
        int[][] scores = {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {7, 3}, {-2, 0}};
        for (int[] s : scores) assertEquals(s[1], Gen.grade(obj("{\"score\":" + s[0] + rest)).score);
        assertEquals(3, Gen.grade(obj("{\"score\":2.5" + rest)).score);
        assertEquals(2, Gen.grade(obj("{\"score\":\"2\"" + rest)).score);
        assertEquals(0, Gen.grade(obj("{\"score\":\"great\"" + rest)).score);
        assertEquals(0, Gen.grade(obj("{\"reaction\":\"r\"}")).score);
        assertEquals(0, Gen.grade(obj("{\"score\":1" + rest)).nailed.length);

        rejected("reaction missing", () -> Gen.grade(obj("{\"score\":2}")));
        rejected("nailed as string", () -> Gen.grade(obj("{\"score\":2,\"reaction\":\"r\",\"nailed\":\"everything\"}")));
        rejected("missing as object", () -> Gen.grade(obj("{\"score\":2,\"reaction\":\"r\",\"missing\":{\"0\":\"x\"}}")));
        rejected("score as array", () -> Gen.grade(obj("{\"score\":[2],\"reaction\":\"r\"}")));
        rejected("model answer as array", () -> Gen.grade(obj("{\"score\":2,\"reaction\":\"r\",\"model_answer\":[\"a\"]}")));
    }

    /* ---------------- the HTTP reply ---------------- */

    @Test public void readsTheTextOfASuccessfulReply() throws Exception {
        String body = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"question\\\":\"},{\"type\":\"text\",\"text\":\"\\\"why?\\\"}\"}],\"stop_reason\":\"end_turn\"}";
        String text = Claude.parse(200, body);
        assertEquals("{\"question\":\"why?\"}", text);
        assertEquals("why?", Gen.question(Gen.extractJson(text)));
    }

    @Test public void aReplyCutOffAtTheTokenLimitIsAFailure() throws Exception {
        String body = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"tagline\\\":\\\"A sorted\"}],\"stop_reason\":\"max_tokens\"}";
        try {
            Claude.parse(200, body);
            fail("truncated reply accepted");
        } catch (Gen.Fail f) {
            assertEquals("truncated", f.code);
            assertTrue(Gen.errText(f.code, f.getMessage()).startsWith("That run did not come through ("));
        }
        // even when the cut-off text happens to parse, it is still refused
        try {
            Claude.parse(200, "{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}],\"stop_reason\":\"max_tokens\"}");
            fail("truncated reply accepted");
        } catch (Gen.Fail f) {
            assertEquals("truncated", f.code);
        }
        assertEquals(4000, Claude.MAX_TOKENS);
    }

    @Test public void httpErrorsMapToTheSameCodesAsBefore() {
        Object[][] cases = {
            {401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}", "bad_key", "invalid x-api-key"},
            {403, "{}", "bad_key", "HTTP 403"},
            {429, "{\"error\":{\"message\":\"slow down\"}}", "rate_limited", "slow down"},
            {529, "overloaded", "rate_limited", "HTTP 529"},
            {500, "<html>oops</html>", "unknown", "HTTP 500"},
            {400, "{\"error\":{\"message\":\"model: not found\"}}", "unknown", "model: not found"}
        };
        for (Object[] c : cases) {
            try {
                Claude.parse((Integer) c[0], (String) c[1]);
                fail("no failure for " + c[0]);
            } catch (Gen.Fail f) {
                assertEquals(c[2], f.code);
                assertEquals(c[3], f.getMessage());
            }
        }
        assertEquals("Anthropic rejected the API key. Check it in Profile.", Gen.errText("bad_key", "invalid x-api-key"));
    }

    @Test public void aMalformedSuccessBodyIsAFailureNotACrash() {
        for (String body : new String[]{"", "not json", "{}", "{\"content\":\"text\"}", "[]"}) {
            try {
                Claude.parse(200, body);
                fail("accepted: " + body);
            } catch (Gen.Fail f) {
                assertEquals("bad_output", f.code);
            }
        }
    }
}
