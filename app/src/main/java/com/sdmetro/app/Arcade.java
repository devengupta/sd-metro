package com.sdmetro.app;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

/** The three arcade drills and their scoring. Pure Java. */
public final class Arcade {
    private Arcade() { }

    public static final String[] GAME_KEYS = {"ladder", "napkin", "pattern"};
    public static final String[][] GAMES = {
        {"Latency Ladder", "Order five operations from fastest to slowest."},
        {"Napkin Math", "Back-of-the-envelope estimates, by order of magnitude."},
        {"Pattern Match", "Symptom in, pattern out."}
    };

    public static String title(String key) {
        for (int i = 0; i < GAME_KEYS.length; i++) if (GAME_KEYS[i].equals(key)) return GAMES[i][0];
        return GAMES[0][0];
    }

    /** Classic "latency numbers every programmer should know" (Jeff Dean / Peter Norvig), in ns. */
    public static final String[] LAT_NAME = {
        "L1 cache reference", "Branch mispredict", "L2 cache reference", "Mutex lock/unlock", "Main memory reference",
        "Compress 1 KB with a fast compressor", "Send 1 KB over a 1 Gbps network", "Read 4 KB randomly from SSD",
        "Read 1 MB sequentially from memory", "Round trip within the same datacenter", "Read 1 MB sequentially from SSD",
        "Disk seek", "Read 1 MB sequentially from disk", "Packet California → Netherlands → California"
    };
    public static final double[] LAT_NS = {0.5, 5, 7, 25, 100, 3000, 10000, 150000, 250000, 500000, 1000000, 10000000, 20000000, 150000000};

    public static final class Q implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String q, w;
        public final String[] o;
        public final int a;
        Q(String q, String[] o, int a, String w) { this.q = q; this.o = o; this.a = a; this.w = w; }
    }

    private static Q q(String q, String o0, String o1, String o2, String o3, int a, String w) { return new Q(q, new String[]{o0, o1, o2, o3}, a, w); }

    public static final Q[] NAPKIN = {
        q("100M daily active users each make 10 reads a day. Average read QPS?", "1.2K", "12K", "120K", "1.2M", 1, "10⁹ reads ÷ 86,400 s ≈ 11,600/s. Round a day to 10⁵ seconds for fast mental math."),
        q("500M posts a day, 300 bytes of text each. Raw text storage per year?", "5.5 TB", "55 TB", "550 TB", "5.5 PB", 1, "500M × 300 B = 150 GB/day; × 365 ≈ 55 TB/year before replication."),
        q("A URL shortener creates 100M links a month at 500 bytes each. Storage for 5 years?", "300 GB", "3 TB", "30 TB", "300 TB", 1, "100M × 60 months = 6B links; × 500 B = 3 TB."),
        q("1M video uploads a day, averaging 100 MB each. New raw storage per day?", "1 TB", "10 TB", "100 TB", "1 PB", 2, "10⁶ × 10⁸ B = 10¹⁴ B = 100 TB/day, before transcoded renditions."),
        q("Peak traffic is 6,000 QPS and one server safely handles 500 QPS. Servers needed at peak (no headroom)?", "3", "12", "40", "120", 1, "6,000 ÷ 500 = 12. Add headroom and N+1 for failures in a real plan."),
        q("10K QPS, each response averages 50 KB. Egress bandwidth?", "400 Mbps", "4 Gbps", "40 Gbps", "400 Gbps", 1, "10⁴ × 50 KB = 500 MB/s; × 8 bits = 4 Gbps."),
        q("50M daily users send 40 chat messages each per day. Average messages per second?", "2.3K", "23K", "230K", "2.3M", 1, "2 × 10⁹ ÷ 86,400 ≈ 23,000/s. Peak is often 2 to 5× that."),
        q("Cache the hottest 20% of 1B items at 1 KB each. Cache memory needed?", "2 GB", "20 GB", "200 GB", "2 TB", 2, "0.2 × 10⁹ × 1 KB = 200 GB, spread over several cache nodes."),
        q("1 million requests per day is roughly how many per second?", "1.2", "12", "120", "1,200", 1, "10⁶ ÷ 86,400 ≈ 11.6/s. A handy anchor: 1M/day ≈ 12/s."),
        q("2M photos a day at 2 MB each, stored with 3 replicas. Raw storage per day?", "1.2 TB", "12 TB", "120 TB", "1.2 PB", 1, "2M × 2 MB = 4 TB; × 3 replicas = 12 TB/day."),
        q("A log stream carries 1M events/s at 1 KB each, retained 7 days. Storage?", "6 TB", "60 TB", "600 TB", "6 PB", 2, "1 GB/s × 604,800 s ≈ 605 TB before replication."),
        q("40K read QPS; each read replica handles 5K QPS. Replicas needed?", "2", "8", "20", "80", 1, "40,000 ÷ 5,000 = 8 replicas, plus spare capacity for a replica failure.")
    };

    public static final Q[] PATTERNS = {
        q("A downstream API keeps timing out. You want to stop calling it for a while and fail fast.", "Circuit breaker", "Bulkhead", "Saga", "Sidecar", 0, "A breaker opens after repeated failures and probes later, so callers stop waiting on a dead dependency."),
        q("A slow reporting feature exhausts the shared thread pool, and checkout dies with it.", "Retry with backoff", "Bulkhead", "CQRS", "Circuit breaker", 1, "Bulkheads give each workload its own pool so one cannot sink the others."),
        q("Order, payment and inventory each own a database. You need all steps done, or compensated.", "Outbox", "Strangler fig", "Saga", "CQRS", 2, "A saga runs local transactions in sequence with compensating actions on failure."),
        q("You write to the DB and must publish an event, without losing it if the broker is down.", "Saga", "Outbox", "Ambassador", "Circuit breaker", 1, "Write the event to an outbox table in the same transaction; a relay publishes it later."),
        q("Replace a legacy monolith piece by piece, behind the same routes.", "Backend for frontend", "Bulkhead", "Strangler fig", "Saga", 2, "Route slices of traffic to new services until the old system can be retired."),
        q("Mobile and web need differently shaped responses from the same services.", "Backend for frontend", "Sidecar", "CQRS", "Strangler fig", 0, "A BFF per client shapes and aggregates calls for that client."),
        q("Reads vastly outnumber writes and need a different shape than the write model.", "Saga", "Outbox", "Bulkhead", "CQRS", 3, "CQRS separates the write model from one or more read-optimised projections."),
        q("Add mTLS, retries and metrics to every service without touching app code.", "Adapter", "Sidecar proxy", "Factory", "Saga", 1, "A sidecar proxy (the basis of a service mesh) handles cross-cutting network concerns."),
        q("A hot cache key expires and thousands of requests hit the DB at once.", "Write-back cache", "Sticky sessions", "Request coalescing", "Read replicas", 2, "Let one request rebuild the value while others wait (single-flight), or refresh early."),
        q("A client retries a payment request after a timeout. It must not charge twice.", "Exponential backoff", "Idempotency key", "Circuit breaker", "Read-your-writes", 1, "The server stores the result per idempotency key and returns it for duplicates."),
        q("After an outage, all clients retry in lockstep and crush the recovering service.", "Sticky sessions", "Consistent hashing", "Backoff with jitter", "Bulkhead", 2, "Exponential backoff with random jitter spreads retries out over time."),
        q("Adding one cache node remaps almost every key and the hit rate collapses.", "Round robin", "Consistent hashing", "IP hash", "LRU eviction", 1, "Consistent hashing moves only about 1/N of keys when a node joins or leaves.")
    };

    /** A shuffled copy of 0..n-1 (Fisher-Yates). */
    public static int[] shuffled(int n, Random rnd) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int x = a[i]; a[i] = a[j]; a[j] = x;
        }
        return a;
    }

    /** "0.5 ns", "3 µs", "150 ms": the value in the largest unit below 1000, without trailing zeros. */
    public static String fmtNs(double ns) {
        if (ns < 1000) return num(ns) + " ns";
        if (ns < 1e6) return num(ns / 1000) + " µs";
        if (ns < 1e9) return num(ns / 1e6) + " ms";
        return num(ns / 1e9) + " s";
    }

    private static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /** Latency Ladder: five operations to tap from fastest to slowest. */
    public static final class Ladder implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Indexes into LAT_NAME / LAT_NS, in the order shown. */
        public final int[] items;
        /** Positions (0..4 into items) in the order tapped. */
        public final ArrayList<Integer> pick = new ArrayList<>();
        public int score;

        public Ladder(Random rnd) {
            int[] all = shuffled(LAT_NAME.length, rnd);
            items = new int[5];
            System.arraycopy(all, 0, items, 0, 5);
        }

        /** For tests: a ladder over the given LAT indexes. */
        public Ladder(int[] items) { this.items = items.clone(); }

        public String name(int i) { return LAT_NAME[items[i]]; }

        public double ns(int i) { return LAT_NS[items[i]]; }

        public boolean done() { return pick.size() == items.length; }

        /** 1-based order of a picked position, or 0 when it is not picked. */
        public int orderOf(int i) { return pick.indexOf(i) + 1; }

        /** Tapping an unpicked position picks it next; tapping a picked one removes only that pick. Returns true when this tap completed the round. */
        public boolean tap(int i) {
            if (done() || i < 0 || i >= items.length) return false;
            int p = pick.indexOf(i);
            if (p >= 0) pick.remove(p); else pick.add(i);
            if (!done()) return false;
            int[] sorted = sorted();
            score = 0;
            for (int j = 0; j < items.length; j++) if (ns(pick.get(j)) == ns(sorted[j])) score++;
            return true;
        }

        /** Positions from fastest to slowest. */
        public int[] sorted() {
            Integer[] idx = new Integer[items.length];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(ns(a), ns(b)));
            int[] out = new int[idx.length];
            for (int i = 0; i < out.length; i++) out[i] = idx[i];
            return out;
        }

        /** True when the j-th slot of the right order was filled with something else. */
        public boolean miss(int j) { return ns(pick.get(j)) != ns(sorted()[j]); }

        /** Bar width in percent, on a log scale relative to the slowest item. */
        public double barPct(int i) {
            int[] s = sorted();
            double mx = Math.log10(ns(s[s.length - 1])) + 1.5;
            return Math.max(3, (Math.log10(ns(i)) + 1.5) / mx * 100);
        }
    }

    /** Napkin Math and Pattern Match: five multiple-choice questions with shuffled options. */
    public static final class Quiz implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String game;
        public final Q[] qs;
        public int i, score;
        /** Chosen option of the current question, or -1. */
        public int ans = -1;

        public Quiz(String game, Random rnd) {
            this.game = game;
            Q[] bank = "napkin".equals(game) ? NAPKIN : PATTERNS;
            int[] order = shuffled(bank.length, rnd);
            qs = new Q[5];
            for (int n = 0; n < 5; n++) {
                Q src = bank[order[n]];
                int[] oo = shuffled(src.o.length, rnd);
                String[] o = new String[oo.length];
                int a = 0;
                for (int k = 0; k < oo.length; k++) { o[k] = src.o[oo[k]]; if (oo[k] == src.a) a = k; }
                qs[n] = new Q(src.q, o, a, src.w);
            }
        }

        public boolean finished() { return i >= qs.length; }

        public Q current() { return qs[i]; }

        public void answer(int option) {
            if (finished() || ans >= 0) return;
            ans = option;
            if (option == current().a) score++;
        }

        /** Moves on; returns true when that was the last question. */
        public boolean next() {
            if (finished()) return false;
            i++;
            ans = -1;
            return finished();
        }

        public String verdict() {
            return score == 5 ? "Flawless." : score >= 3 ? "Solid. Run it again to see new questions." : "Run it again; the explanations are the lesson.";
        }
    }
}
