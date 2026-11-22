package com.sdmetro.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ArcadeTest {
    /** Mutex (25 ns), L1 (0.5), disk seek (10 ms), main memory (100 ns), SSD 1 MB (1 ms). */
    private static Arcade.Ladder ladder() { return new Arcade.Ladder(new int[]{3, 0, 11, 4, 10}); }

    @Test public void banks() {
        assertEquals(14, Arcade.LAT_NAME.length);
        assertEquals(14, Arcade.LAT_NS.length);
        assertEquals(12, Arcade.NAPKIN.length);
        assertEquals(12, Arcade.PATTERNS.length);
        for (Arcade.Q q : Arcade.NAPKIN) { assertEquals(4, q.o.length); assertTrue(q.a >= 0 && q.a < 4); }
        for (Arcade.Q q : Arcade.PATTERNS) { assertEquals(4, q.o.length); assertTrue(q.a >= 0 && q.a < 4); }
        assertEquals("Circuit breaker", Arcade.PATTERNS[0].o[Arcade.PATTERNS[0].a]);
        assertEquals("12K", Arcade.NAPKIN[0].o[Arcade.NAPKIN[0].a]);
        assertEquals("Napkin Math", Arcade.title("napkin"));
    }

    @Test public void ladderPerfectOrder() {
        Arcade.Ladder g = ladder();
        assertArrayEquals(new int[]{1, 0, 3, 4, 2}, g.sorted());
        int[] taps = {1, 0, 3, 4};
        for (int t : taps) assertFalse(g.tap(t));
        assertFalse(g.done());
        assertTrue(g.tap(2));
        assertTrue(g.done());
        assertEquals(5, g.score);
        for (int j = 0; j < 5; j++) assertFalse(g.miss(j));
        assertFalse(g.tap(0));                     // a finished round ignores taps
        assertEquals(5, g.pick.size());
    }

    @Test public void ladderScoresSlotBySlot() {
        Arcade.Ladder g = ladder();
        for (int t : new int[]{0, 1, 3, 4, 2}) g.tap(t);   // first two swapped
        assertEquals(3, g.score);
        assertTrue(g.miss(0));
        assertTrue(g.miss(1));
        assertFalse(g.miss(2));
        Arcade.Ladder worst = ladder();
        for (int t : new int[]{2, 4, 1, 0, 3}) worst.tap(t);
        assertEquals(0, worst.score);
    }

    @Test public void undoRemovesOnlyTheTappedPick() {
        Arcade.Ladder g = ladder();
        g.tap(0); g.tap(1); g.tap(2); g.tap(3);
        assertEquals(Arrays.asList(0, 1, 2, 3), g.pick);
        assertEquals(2, g.orderOf(1));
        g.tap(1);                                   // undo the second of four
        assertEquals(Arrays.asList(0, 2, 3), g.pick);
        assertEquals(0, g.orderOf(1));
        assertEquals(1, g.orderOf(0));
        assertEquals(2, g.orderOf(2));
        assertEquals(3, g.orderOf(3));
        g.tap(1);                                   // picked again: goes to the end
        assertEquals(Arrays.asList(0, 2, 3, 1), g.pick);
        assertEquals(4, g.orderOf(1));
        assertFalse(g.done());
        assertTrue(g.tap(4));
        assertEquals(Arrays.asList(0, 2, 3, 1, 4), g.pick);
    }

    @Test public void ladderDealsFiveDistinctItems() {
        for (int seed = 0; seed < 50; seed++) {
            Arcade.Ladder g = new Arcade.Ladder(new Random(seed));
            Set<Integer> seen = new HashSet<>();
            for (int it : g.items) { assertTrue(it >= 0 && it < 14); seen.add(it); }
            assertEquals(5, seen.size());
            int[] s = g.sorted();
            for (int j = 1; j < 5; j++) assertTrue(g.ns(s[j - 1]) < g.ns(s[j]));
            assertEquals(100, g.barPct(s[4]), 1e-9);
            assertTrue(g.barPct(s[0]) >= 3);
        }
    }

    @Test public void quizShufflesOptionsButKeepsTheAnswer() {
        for (String game : new String[]{"napkin", "pattern"}) {
            Arcade.Q[] bank = "napkin".equals(game) ? Arcade.NAPKIN : Arcade.PATTERNS;
            for (int seed = 0; seed < 40; seed++) {
                Arcade.Quiz g = new Arcade.Quiz(game, new Random(seed));
                assertEquals(5, g.qs.length);
                Set<String> asked = new HashSet<>();
                for (Arcade.Q q : g.qs) {
                    assertTrue(asked.add(q.q));
                    Arcade.Q src = null;
                    for (Arcade.Q b : bank) if (b.q.equals(q.q)) src = b;
                    assertEquals(src.o[src.a], q.o[q.a]);
                    assertEquals(new HashSet<>(Arrays.asList(src.o)), new HashSet<>(Arrays.asList(q.o)));
                }
            }
        }
    }

    @Test public void quizScoring() {
        Arcade.Quiz g = new Arcade.Quiz("pattern", new Random(7));
        boolean last = false;
        for (int n = 0; n < 5; n++) {
            assertFalse(g.finished());
            Arcade.Q q = g.current();
            int pick = n < 3 ? q.a : (q.a + 1) % 4;
            g.answer(pick);
            g.answer(q.a);                          // a second tap on an answered question changes nothing
            assertEquals(pick, g.ans);
            last = g.next();
            assertEquals(n == 4, last);
        }
        assertTrue(g.finished());
        assertEquals(3, g.score);
        assertEquals("Solid. Run it again to see new questions.", g.verdict());
        g.score = 5; assertEquals("Flawless.", g.verdict());
        g.score = 2; assertEquals("Run it again; the explanations are the lesson.", g.verdict());
    }

    @Test public void formatsNanoseconds() {
        assertEquals("0.5 ns", Arcade.fmtNs(0.5));
        assertEquals("5 ns", Arcade.fmtNs(5));
        assertEquals("100 ns", Arcade.fmtNs(100));
        assertEquals("3 µs", Arcade.fmtNs(3000));
        assertEquals("150 µs", Arcade.fmtNs(150000));
        assertEquals("1 ms", Arcade.fmtNs(1000000));
        assertEquals("20 ms", Arcade.fmtNs(20000000));
        assertEquals("150 ms", Arcade.fmtNs(150000000));
        assertEquals("2.5 s", Arcade.fmtNs(2.5e9));
    }

    @Test public void shuffleIsAPermutation() {
        int[] a = Arcade.shuffled(6, new Random(1));
        int[] sorted = a.clone();
        Arrays.sort(sorted);
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, sorted);
    }
}
