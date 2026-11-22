package com.sdmetro.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The roadmap text tree turned into lines (sections), stops and the final goals. Pure Java. */
public final class Roadmap {
    public static final class Phase {
        public final String code, name;
        public final int color, on;
        Phase(String code, String name, int color) { this.code = code; this.name = name; this.color = color; this.on = 0xFF0B1C4A; }
    }

    public static final class Stop {
        public String name, key;
        public int depth, idx, sec, kids;
        public Stop parent;
    }

    public static final class Section {
        public int n;
        public String name, hook;
        public final List<Stop> stops = new ArrayList<>();
        public Phase phase;
    }

    public static final class Loc {
        public final int sec, idx;
        Loc(int sec, int idx) { this.sec = sec; this.idx = idx; }
    }

    /** Legend order matters: Foundations, Data, Scale, Distributed, Operations, Architecture, Craft. */
    public static final Phase[] PHASES = {
        new Phase("F", "Foundations", 0xFFFF9A85), new Phase("D", "Data", 0xFFFFD27A),
        new Phase("S", "Scale", 0xFF7EE8B0), new Phase("X", "Distributed", 0xFF9FD0FF),
        new Phase("O", "Operations", 0xFFC9AEFF), new Phase("A", "Architecture", 0xFF6FE3E3),
        new Phase("C", "Craft", 0xFFFFA8D8)
    };
    /** Phase code of section 1..25. */
    private static final String SEC_PHASE = "FFFFDDSXXSOAAAOOOAXCCCCCC";

    public static final String[] HOOKS = {
        "",
        "Packets, ports and processes: what actually happens under every request.",
        "The shapes software takes, and why each one exists.",
        "Turning \"it should be fast\" into numbers you can design against.",
        "How services talk: protocols, APIs and the cost of every hop.",
        "Where truth lives, and how it stays true under load.",
        "Remembering answers so you never compute them twice.",
        "Spreading traffic so no single box becomes the bottleneck.",
        "Many machines acting as one, and what that costs you.",
        "Decoupling producers from consumers with queues and logs.",
        "Find the bottleneck, then move it somewhere cheaper.",
        "Designing for the failure that will certainly happen.",
        "Splitting a system along business boundaries.",
        "Named solutions to problems that keep coming back.",
        "Who you are, what you may do, and keeping secrets secret.",
        "Seeing inside a running system before users tell you.",
        "Where the system runs and how code gets there.",
        "Bytes at rest: blocks, files, objects and their metadata.",
        "Pushing updates to users the moment they happen.",
        "Clocks, gossip and the deep mechanics of distributed state.",
        "Classes, contracts and code that survives change.",
        "From a vague prompt to a defended architecture.",
        "Real systems, taken apart end to end.",
        "The tools you name in a design and actually run.",
        "Reps: design, defend, repeat.",
        "Thinking like the architect in the room."
    };

    /** Stop names that share lesson content with another name (normalised name -> content key). */
    private static final String[] ALIAS_PAIRS = {
        "server sent events", "server sent events sse",
        "tls", "tls ssl",
        "encryption in transit", "tls ssl",
        "kafka", "apache kafka",
        "database sharding", "sharding partitioning",
        "compression", "data compression",
        "blob storage", "object storage",
        "object storage s3", "object storage",
        "content delivery networks", "cdn edge computing",
        "pub sub architecture", "publish subscribe",
        "synchronous communication", "synchronous vs asynchronous communication",
        "asynchronous communication", "synchronous vs asynchronous communication",
        "synchronous vs asynchronous processing", "synchronous vs asynchronous communication",
        "inter service communication", "synchronous vs asynchronous communication",
        "monolith vs microservices", "microservices architecture",
        "stateless services", "stateful vs stateless systems",
        "rate limiting", "api rate limiting",
        "health monitoring", "health checks",
        "fault tolerance fundamentals", "fault tolerance",
        "reliability fault tolerance", "fault tolerance",
        "what is a distributed system", "distributed systems basics",
        "data replication strategies", "replication",
        "container orchestration", "kubernetes",
        "serverless infrastructure", "serverless architecture",
        "horizontal vs vertical scaling", "horizontal scaling",
        "backup strategies", "backup restore",
        "identifying bottlenecks", "performance bottleneck analysis",
        "bottleneck identification", "performance bottleneck analysis",
        "requirement analysis", "requirement gathering",
        "functional non functional requirements", "requirement gathering",
        "scalability trade offs", "architectural trade offs",
        "trade off analysis", "architectural trade offs",
        "identify trade offs", "architectural trade offs",
        "technical documentation", "architecture documentation",
        "document design decisions", "architecture documentation",
        "security considerations", "security",
        "security first design", "security",
        "scaling strategies", "scalability",
        "reliability engineering", "reliability",
        "distributed systems thinking", "distributed system challenges",
        "mock design interviews", "system design interviews",
        "database schema design", "schema design",
        "data modeling", "schema design",
        "message brokers", "message queue fundamentals",
        "latency monitoring", "latency throughput",
        "throughput monitoring", "latency throughput",
        "extensibility maintainability", "maintainability",
        "concurrency in lld", "thread safety",
        "databases", "sql vs nosql",
        "database design decisions", "sql vs nosql",
        "database internals", "storage engines",
        "networking proxy", "reverse proxy",
        "infrastructure", "infrastructure as code",
        "monitoring", "metrics",
        "design a url shortener", "url shortener tinyurl",
        "design a rate limiter", "rate limiter",
        "design a notification service", "notification system",
        "design a chat application", "chat application whatsapp",
        "design a news feed", "news feed facebook instagram",
        "design a video streaming platform", "video streaming youtube netflix",
        "design a ride hailing platform", "ride hailing uber",
        "design a payment service", "payment system",
        "design a distributed cache", "distributed cache redis",
        "design a search engine", "search engine",
        "design a file storage service", "file storage google drive dropbox",
        "design a ticket booking system", "ticket booking bookmyshow",
        "review existing architectures", "architecture reviews",
        "build end to end projects", "end to end system design",
        "real time data processing", "stream processing",
        "high availability", "availability",
        "rest apis", "rest architecture",
        "browser caching", "client side caching",
        "retry mechanisms", "retry",
        "live chat architecture", "chat application whatsapp",
        "real time notifications", "notification system",
        "component diagrams", "architecture diagrams",
        "data flow diagrams", "architecture diagrams",
    };
    private static final Map<String, String> ALIAS = new HashMap<>();
    static { for (int i = 0; i < ALIAS_PAIRS.length; i += 2) ALIAS.put(ALIAS_PAIRS[i], ALIAS_PAIRS[i + 1]); }

    private static final Pattern TRAILING_PATTERN = Pattern.compile("\\s+pattern$");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern BRANCH = Pattern.compile("^([│ ]*)(├── |└── )(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\.\\s*(.*)$");

    public static String norm(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        t = TRAILING_PATTERN.matcher(t).replaceFirst("");
        return NON_ALNUM.matcher(t).replaceAll(" ").trim();
    }

    /** Content key of a stop name. */
    public static String canon(String name) {
        String k = norm(name);
        String a = ALIAS.get(k);
        return a != null ? a : k;
    }

    public final List<Section> sections = new ArrayList<>();
    public final List<String> goals = new ArrayList<>();
    private final Map<Integer, Section> byNumber = new HashMap<>();
    /** Content key -> every place it appears, in roadmap order. More than one place means an interchange. */
    public final Map<String, List<Loc>> keyLoc = new LinkedHashMap<>();
    public int totalStops;

    private Roadmap() { }

    public static Roadmap parse(String raw) {
        Roadmap r = new Roadmap();
        Section cur = null;
        boolean inGoal = false;
        Map<Integer, Stop> lastAt = new HashMap<>();
        for (String ln : raw.split("\n")) {
            if (ln.endsWith("\r")) ln = ln.substring(0, ln.length() - 1);
            if (ln.startsWith("FINAL GOAL")) { inGoal = true; continue; }
            Matcher m = BRANCH.matcher(ln);
            if (!m.matches()) continue;
            int depth = (int) Math.round(m.group(1).length() / 4.0);
            String name = m.group(3).trim();
            if (inGoal) { if (depth == 0) r.goals.add(name); continue; }
            if (depth == 0) {
                Matcher sm = NUMBERED.matcher(name);
                if (!sm.matches()) continue;
                cur = new Section();
                cur.n = Integer.parseInt(sm.group(1));
                cur.name = sm.group(2);
                r.sections.add(cur);
                continue;
            }
            if (cur == null) continue;
            Stop stop = new Stop();
            stop.name = name; stop.depth = depth; stop.idx = cur.stops.size(); stop.sec = cur.n;
            stop.key = canon(name);
            stop.parent = depth > 1 ? lastAt.get(depth - 1) : null;
            if (stop.parent != null) stop.parent.kids++;
            cur.stops.add(stop);
            lastAt.put(depth, stop);
        }
        for (Section s : r.sections) {
            char code = s.n >= 1 && s.n <= SEC_PHASE.length() ? SEC_PHASE.charAt(s.n - 1) : 'C';
            for (Phase p : PHASES) if (p.code.charAt(0) == code) s.phase = p;
            s.hook = s.n >= 1 && s.n < HOOKS.length ? HOOKS[s.n] : "";
            r.byNumber.put(s.n, s);
            for (Stop t : s.stops) {
                List<Loc> l = r.keyLoc.get(t.key);
                if (l == null) { l = new ArrayList<>(); r.keyLoc.put(t.key, l); }
                l.add(new Loc(s.n, t.idx));
            }
            r.totalStops += s.stops.size();
        }
        return r;
    }

    public Section sec(int n) { return byNumber.get(n); }

    /** The stop at a location, or null when the location does not exist. */
    public Stop stop(int sec, int idx) {
        Section s = sec(sec);
        return s != null && idx >= 0 && idx < s.stops.size() ? s.stops.get(idx) : null;
    }

    /** Other places on the map that hold the same concept as this stop. */
    public List<Loc> others(Stop t) {
        List<Loc> out = new ArrayList<>();
        List<Loc> all = keyLoc.get(t.key);
        if (all != null) for (Loc l : all) if (!(l.sec == t.sec && l.idx == t.idx)) out.add(l);
        return out;
    }
}
