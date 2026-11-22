package com.sdmetro.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** Shared helpers: the real bundled assets, and an in-memory store. */
final class TestData {
    private TestData() { }

    static String asset(String name) throws Exception { return read(Paths.get("src", "main", "assets", name)); }

    static String resource(String name) throws Exception { return read(Paths.get("src", "test", "resources", name)); }

    private static String read(Path p) throws Exception {
        if (!Files.exists(p)) p = Paths.get("app").resolve(p);
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    static Roadmap roadmap() throws Exception { return Roadmap.parse(asset("roadmap.txt")); }

    static final class MemKV implements KV {
        final Map<String, String> map = new HashMap<>();
        boolean failWrites;
        @Override public String get(String key) { return map.get(key); }
        @Override public boolean put(String key, String value) { if (failWrites) return false; map.put(key, value); return true; }
        @Override public boolean remove(String key) { map.remove(key); return true; }
    }
}
