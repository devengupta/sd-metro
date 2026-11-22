package com.sdmetro.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class ProgressTest {
    private static final long T0 = 1795000000000L;
    private static final String D = "2026-11-22";

    @Test public void firstPipGivesFullXpThenFive() {
        Progress p = new Progress();
        assertEquals(20, p.award("k", 'e', 20, T0, D));
        assertEquals(5, p.award("k", 'e', 20, T0 + 1, D));
        assertEquals(40, p.award("k", 'i', 40, T0 + 2, D));
        assertEquals(60, p.award("k", 't', 60, T0 + 3, D));
        assertEquals(125, p.xp);
        assertEquals(3, p.pipCount("k"));
        assertEquals(T0, p.pipsOf("k").e);
        assertEquals(0, p.pipCount("other"));
        // the review is scheduled once, at the first pip
        assertEquals(0, p.rev.get("k").n);
        assertEquals(T0, p.rev.get("k").at);
        assertEquals(4, p.dailyCount(D));
        assertEquals(Arrays.asList(D), p.days);
    }

    @Test public void rankThresholds() {
        Progress p = new Progress();
        Object[][] cases = {
            {0L, "Commuter", "Regular", 0, 200L}, {199L, "Commuter", "Regular", 100, 1L}, {200L, "Regular", "Line Inspector", 0, 400L},
            {135L, "Commuter", "Regular", 68, 65L}, {599L, "Regular", "Line Inspector", 100, 1L}, {600L, "Line Inspector", "Station Master", 0, 900L},
            {1500L, "Station Master", "Network Planner", 0, 2000L}, {3500L, "Network Planner", "Chief Architect", 0, 3500L},
            {5250L, "Network Planner", "Chief Architect", 50, 1750L}, {7000L, "Chief Architect", null, 100, 0L}, {99999L, "Chief Architect", null, 100, 0L}
        };
        for (Object[] c : cases) {
            p.xp = (Long) c[0];
            Progress.Rank r = p.rank();
            assertEquals("xp " + c[0], c[1], r.name);
            assertEquals("xp " + c[0], c[2], r.next);
            assertEquals("xp " + c[0], ((Integer) c[3]).intValue(), r.pct);
            assertEquals("xp " + c[0], ((Long) c[4]).longValue(), r.need);
        }
    }

    @Test public void streakCountsBackFromTodayOrYesterday() {
        Progress p = new Progress();
        LocalDate today = LocalDate.of(2026, 3, 2);
        assertEquals(0, p.streak(today));
        p.days.addAll(Arrays.asList("2026-02-27", "2026-02-28", "2026-03-01"));
        assertEquals(3, p.streak(today));              // nothing yet today: yesterday's run still stands
        p.days.add("2026-03-02");
        assertEquals(4, p.streak(today));
        assertEquals(0, p.streak(LocalDate.of(2026, 3, 4)));   // a full day missed
        Progress gap = new Progress();
        gap.days.addAll(Arrays.asList("2026-02-25", "2026-02-27", "2026-03-01", "2026-03-02"));
        assertEquals(2, gap.streak(today));
        Progress year = new Progress();
        year.days.addAll(Arrays.asList("2025-12-31", "2026-01-01"));
        assertEquals(2, year.streak(LocalDate.of(2026, 1, 1)));
    }

    @Test public void reviewsComeDueOnTheIntervalLadder() throws Exception {
        Roadmap rm = TestData.roadmap();
        Progress p = new Progress();
        String k = "how the internet works";
        p.award(k, 'e', 20, T0, D);
        assertTrue(p.dueList(T0 + Progress.DAY - 1, rm).isEmpty());
        assertEquals(Arrays.asList(k), p.dueList(T0 + Progress.DAY, rm));
        int[] days = {1, 3, 7, 16, 35, 80, 80, 80};
        for (int n = 0; n < days.length; n++) assertEquals(days[n] * Progress.DAY, Progress.intervalMs(n));

        long now = T0 + Progress.DAY;
        p.rate(k, 1, now);                                   // got it: one step
        assertEquals(1, p.rev.get(k).n);
        assertEquals(now, p.rev.get(k).at);
        assertTrue(p.dueList(now + 3 * Progress.DAY - 1, rm).isEmpty());
        assertEquals(1, p.dueList(now + 3 * Progress.DAY, rm).size());
        p.rate(k, 2, now);                                   // easy: two steps
        assertEquals(3, p.rev.get(k).n);
        assertTrue(p.dueList(now + 16 * Progress.DAY - 1, rm).isEmpty());
        assertEquals(1, p.dueList(now + 16 * Progress.DAY, rm).size());
        p.rate(k, 0, now);                                   // forgot: back to the start
        assertEquals(0, p.rev.get(k).n);
        assertEquals(1, p.dueList(now + Progress.DAY, rm).size());
    }

    @Test public void dueListIsOldestFirstAndSkipsUnknownKeys() throws Exception {
        Roadmap rm = TestData.roadmap();
        Progress p = new Progress();
        p.award("tcp udp", 'e', 20, T0 + 500, D);
        p.award("how the internet works", 'e', 20, T0 + 100, D);
        p.award("no such concept", 'e', 20, T0, D);
        List<String> due = p.dueList(T0 + 2 * Progress.DAY, rm);
        assertEquals(Arrays.asList("how the internet works", "tcp udp"), due);
    }

    @Test public void dailyQuestCountsEveryAction() {
        Progress p = new Progress();
        assertEquals(0, p.dailyCount(D));
        p.award("a", 'e', 20, T0, D);
        p.addXp(10, D);
        p.finishGame("ladder", 3, D);
        assertEquals(3, p.dailyCount(D));
        p.addXp(10, "2026-11-23");
        assertEquals(3, p.dailyCount(D));
        assertEquals(1, p.dailyCount("2026-11-23"));
        assertEquals(Arrays.asList(D, "2026-11-23"), p.days);
        assertEquals(5, Progress.DAILY_GOAL);
    }

    @Test public void arcadeBestAndXp() {
        Progress p = new Progress();
        assertEquals(0, p.best("ladder"));
        assertEquals(20, p.finishGame("ladder", 3, D));
        assertEquals(3, p.best("ladder"));
        assertEquals(10, p.finishGame("ladder", 1, D));     // a worse round keeps the best
        assertEquals(3, p.best("ladder"));
        assertEquals(30, p.finishGame("ladder", 5, D));
        assertEquals(5, p.best("ladder"));
        assertEquals(5, p.finishGame("napkin", 0, D));      // showing up still earns 5
        assertEquals(0, p.best("napkin"));
        assertEquals(65, p.xp);
    }

    @Test public void lineProgressMasteredAndVisited() throws Exception {
        Roadmap rm = TestData.roadmap();
        Progress p = new Progress();
        Roadmap.Section s = rm.sec(1);
        assertEquals(0, p.secProgress(s), 1e-9);
        p.award(s.stops.get(0).key, 'e', 20, T0, D);
        assertEquals(1.0 / (16 * 3), p.secProgress(s), 1e-9);
        p.award(s.stops.get(0).key, 'i', 40, T0, D);
        p.award(s.stops.get(0).key, 't', 60, T0, D);
        p.award(s.stops.get(1).key, 'e', 20, T0, D);
        assertEquals(1, p.mastered(rm));
        assertEquals(2, p.touched(rm));
        // the second stop is an interchange with line 2, so line 2 moves too
        assertTrue(p.secProgress(rm.sec(2)) > 0);
    }

    @Test public void suggestions() throws Exception {
        Roadmap rm = TestData.roadmap();
        Progress p = new Progress();
        List<Roadmap.Stop> s = p.suggestions(rm);
        assertEquals(4, s.size());
        assertEquals("How the Internet Works", s.get(0).name);
        assertEquals(2, s.get(1).sec);
        p.lastSec = 5; p.lastIdx = 3;
        s = p.suggestions(rm);
        assertEquals(5, s.get(0).sec);
        assertEquals(3, s.get(0).idx);
        assertEquals(1, s.get(1).sec);
        assertEquals(4, s.size());
    }

    @Test public void jsonKeepsTheStoredShape() throws Exception {
        Progress p = new Progress();
        assertNull(null, new JSONObject(p.toJson()).opt("nope"));
        JSONObject blank = new JSONObject(p.toJson());
        assertEquals(1, blank.getInt("v"));
        assertTrue(blank.isNull("last"));
        for (String k : new String[]{"xp", "days", "daily", "pips", "rev", "notes", "last", "arcade", "updated"}) assertTrue(k, blank.has(k));

        p.award("how the internet works", 'e', 20, T0, D);
        p.notes.put("how the internet works", "DNS finds it");
        p.lastSec = 1; p.lastIdx = 0;
        p.finishGame("napkin", 4, D);
        p.updated = T0 + 9;
        JSONObject j = new JSONObject(p.toJson());
        assertEquals(45, j.getLong("xp"));
        assertEquals(D, j.getJSONArray("days").getString(0));
        assertEquals(2, j.getJSONObject("daily").getInt(D));
        JSONObject pip = j.getJSONObject("pips").getJSONObject("how the internet works");
        assertEquals(T0, pip.getLong("e"));
        assertFalse(pip.has("i"));
        assertEquals(0, j.getJSONObject("rev").getJSONObject("how the internet works").getInt("n"));
        assertEquals(T0, j.getJSONObject("rev").getJSONObject("how the internet works").getLong("at"));
        assertEquals(1, j.getJSONObject("last").getInt("sec"));
        assertEquals(0, j.getJSONObject("last").getInt("idx"));
        assertEquals(4, j.getJSONObject("arcade").getInt("napkin"));

        Progress q = Progress.fromJson(p.toJson());
        assertEquals(new JSONObject(p.toJson()).toString().length(), new JSONObject(q.toJson()).toString().length());
        assertEquals(45, q.xp);
        assertEquals(1, q.pipCount("how the internet works"));
        assertEquals("DNS finds it", q.notes.get("how the internet works"));
        assertEquals(1, q.lastSec);
        assertEquals(T0 + 9, q.updated);
    }

    @Test public void readsWhatTheWebVersionWrote() {
        String stored = "{\"v\":1,\"xp\":135,\"days\":[\"2026-11-22\"],\"daily\":{\"2026-11-22\":5},\"pips\":{\"how the internet works\":{\"e\":1795353260970,\"i\":1795353272055,\"t\":1795353282699}},\"rev\":{\"how the internet works\":{\"n\":0,\"at\":1795180460970}},\"notes\":{\"how the internet works\":\"DNS finds it, TCP connects, TLS secures, HTTP talks.\"},\"last\":{\"sec\":2,\"idx\":5},\"arcade\":{\"ladder\":1},\"updated\":1795353300000}";
        Progress p = Progress.fromJson(stored);
        assertEquals(135, p.xp);
        assertEquals(3, p.pipCount("how the internet works"));
        assertEquals(1795180460970L, p.rev.get("how the internet works").at);
        assertEquals(2, p.lastSec);
        assertEquals(5, p.lastIdx);
        assertEquals(1, p.best("ladder"));
        assertEquals(5, p.dailyCount("2026-11-22"));
    }

    @Test public void unreadableOrForeignDataGivesBlank() {
        assertEquals(0, Progress.fromJson(null).xp);
        assertEquals(0, Progress.fromJson("not json").xp);
        assertEquals(0, Progress.fromJson("{\"v\":2,\"xp\":50}").xp);
        assertEquals(0, Progress.fromJson("null").xp);
        assertEquals(-1, Progress.fromJson("{\"v\":1,\"last\":null}").lastSec);
    }
}
