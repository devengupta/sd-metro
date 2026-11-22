package com.sdmetro.app;

/** A small string key-value store. Keys and JSON-encoded values follow the earlier localStorage layout. */
public interface KV {
    /** The stored value, or null. */
    String get(String key);

    /** Stores a value durably; false when the device would not save it. */
    boolean put(String key, String value);

    boolean remove(String key);
}
