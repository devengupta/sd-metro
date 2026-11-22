package com.sdmetro.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoadmapTest {
    @Test public void parsesTheBundledRoadmap() throws Exception {
        Roadmap r = TestData.roadmap();
        assertEquals(25, r.sections.size());
        assertEquals(463, r.totalStops);
        int sum = 0;
        for (Roadmap.Section s : r.sections) sum += s.stops.size();
        assertEquals(463, sum);
        assertEquals(5, r.goals.size());
        assertEquals("Design Scalable Applications", r.goals.get(0));
        assertEquals("Design Systems Used by Millions of Users", r.goals.get(4));
        for (int i = 0; i < 25; i++) assertEquals(i + 1, r.sections.get(i).n);
        assertEquals("Computer Science Fundamentals", r.sec(1).name);
        assertEquals(16, r.sec(1).stops.size());
        assertEquals("How the Internet Works", r.stop(1, 0).name);
        assertEquals("Mastery & Career Preparation", r.sec(25).name);
        assertNull(r.sec(26));
        assertNull(r.stop(1, 16));
        assertNull(r.stop(1, -1));
    }

    @Test public void phasesAndHooks() throws Exception {
        Roadmap r = TestData.roadmap();
        String phases = "";
        for (Roadmap.Section s : r.sections) { assertNotNull(s.phase); phases += s.phase.code; }
        assertEquals("FFFFDDSXXSOAAAOOOAXCCCCCC", phases);
        assertEquals("Foundations", r.sec(1).phase.name);
        assertEquals(0xFFFF9A85, r.sec(1).phase.color);
        assertEquals("Where truth lives, and how it stays true under load.", r.sec(5).hook);
        assertEquals("Thinking like the architect in the room.", r.sec(25).hook);
        assertEquals(7, Roadmap.PHASES.length);
    }

    @Test public void nestedStopsKnowTheirParent() throws Exception {
        Roadmap r = TestData.roadmap();
        Roadmap.Section db = r.sec(5);
        Roadmap.Stop rel = db.stops.get(0), pg = db.stops.get(1);
        assertEquals("Relational Databases", rel.name);
        assertEquals(1, rel.depth);
        assertNull(rel.parent);
        assertEquals("PostgreSQL", pg.name);
        assertEquals(2, pg.depth);
        assertSame(rel, pg.parent);
        assertTrue(rel.kids >= 2);
        for (Roadmap.Section s : r.sections) for (int i = 0; i < s.stops.size(); i++) {
            Roadmap.Stop t = s.stops.get(i);
            assertEquals(i, t.idx);
            assertEquals(s.n, t.sec);
            assertTrue(t.depth >= 1);
        }
    }

    @Test public void normAndCanon() {
        assertEquals("http https", Roadmap.norm("HTTP & HTTPS"));
        assertEquals("dns domain name system", Roadmap.norm("DNS (Domain Name System)"));
        assertEquals("circuit breaker", Roadmap.norm("Circuit Breaker Pattern"));
        assertEquals("pattern matching", Roadmap.norm("Pattern Matching"));
        assertEquals("cpu i o basics", Roadmap.norm("CPU & I/O Basics"));
        assertEquals("what is system design", Roadmap.norm("  What is System Design? "));
        assertEquals("apache kafka", Roadmap.canon("Kafka"));
        assertEquals("tls ssl", Roadmap.canon("TLS"));
        assertEquals("tls ssl", Roadmap.canon("Encryption in Transit"));
        assertEquals("rest architecture", Roadmap.canon("REST APIs"));
        assertEquals("chat application whatsapp", Roadmap.canon("Design a Chat Application"));
        assertEquals("websockets", Roadmap.canon("WebSockets"));
    }

    @Test public void everyStopResolvesToContent() throws Exception {
        Roadmap r = TestData.roadmap();
        Content c = Content.parse(TestData.asset("content.json"));
        assertEquals(352, c.size());
        assertEquals(352, r.keyLoc.size());
        for (Roadmap.Section s : r.sections) for (Roadmap.Stop t : s.stops) {
            Content.Entry e = c.get(t.key);
            assertNotNull("no content for " + t.name + " -> " + t.key, e);
            assertTrue(e.e.length >= 1);
            assertEquals(6, e.q.length);
            assertEquals(6, e.i.length);
            assertEquals(4, e.a.length);
        }
        Set<String> used = new HashSet<>(r.keyLoc.keySet());
        for (String k : c.keys()) assertTrue("content never used: " + k, used.contains(k));
    }

    @Test public void interchangesShareAKey() throws Exception {
        Roadmap r = TestData.roadmap();
        int extra = 0, shared = 0;
        for (List<Roadmap.Loc> l : r.keyLoc.values()) { extra += l.size() - 1; if (l.size() > 1) shared += l.size(); }
        assertEquals(463 - 352, extra);
        assertTrue(shared > extra);
        Roadmap.Stop cs = r.stop(1, 1);
        assertEquals("Client-Server Architecture", cs.name);
        List<Roadmap.Loc> others = r.others(cs);
        assertEquals(1, others.size());
        assertEquals(2, others.get(0).sec);
        assertEquals("Client-Server Architecture", r.stop(others.get(0).sec, others.get(0).idx).name);
        assertTrue(r.others(r.stop(1, 0)).isEmpty());
    }

    @Test public void builtinPagerMoves() throws Exception {
        Content c = Content.parse(TestData.asset("content.json"));
        Content.Entry e = c.get("how the internet works");
        String[] strong = Gen.builtinMove(e, 0), wrong = Gen.builtinMove(e, 1), partial = Gen.builtinMove(e, 2);
        assertEquals("Put a CDN or regional edge in front and reuse connections", strong[0]);
        assertEquals("The dashboards settle and the page clears.", strong[1]);
        assertEquals("strong", strong[2]);
        assertEquals("Add more CPU to the Virginia servers", wrong[0]);
        assertEquals("Server time was never the problem; nothing changes", wrong[1]);
        assertEquals("wrong", wrong[2]);
        assertEquals("Raise client timeouts", partial[0]);
        assertEquals("partial", partial[2]);
        // (h*31 + c) >>> 0 over the key, modulo 8 companies: same pick as the web version showed.
        assertEquals("TiffinBox", Gen.companyFor("how the internet works"));
    }
}
