package com.sdmetro.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class AccountsTest {
    /** Produced by the WebView version's native bridge (MainActivity.Bridge.hash) for a throwaway test account. */
    private static final String SALT = "bbf28a45d016913f26420bd9cec8017f";
    private static final String HASH = "bf8bb4ecc47773ac4732c53ddbf402883d2f480a6bb896ae255cd3152e4984af";

    @Test public void pbkdf2MatchesTheExistingImplementation() throws Exception {
        assertEquals(HASH, Crypto.hash("throwaway1", SALT));
        assertNotEquals(HASH, Crypto.hash("throwaway2", SALT));
        assertEquals(120000, Crypto.ITERATIONS);
    }

    @Test public void pbkdf2MatchesAnIndependentImplementation() throws Exception {
        // Independent check of the primitive: PBKDF2-HMAC-SHA256("password", "salt", 120000, 32) computed with Python's hashlib.
        assertEquals(PY_VECTOR, Crypto.hash("password", "73616c74"));
    }
    private static final String PY_VECTOR = "b77d7003dbed5d2b1f2b7fd03480004a49b12ef613eb01ccd79a51be683fb6a9";

    @Test public void randHex() {
        String a = Crypto.randHex(16), b = Crypto.randHex(16);
        assertEquals(32, a.length());
        assertTrue(a.matches("[0-9a-f]{32}"));
        assertNotEquals(a, b);
        assertEquals(12, Crypto.randHex(6).length());
    }

    @Test public void formValidationMessages() {
        Accounts acc = new Accounts(new TestData.MemKV());
        assertEquals("Enter a valid email address.", acc.validate(true, "A", "nope", "secret1", "secret1"));
        assertEquals("Enter a valid email address.", acc.validate(false, "", "a b@c.d", "x", ""));
        assertEquals("Enter your name.", acc.validate(true, "  ", "a@b.co", "secret1", "secret1"));
        assertEquals("Use a password of at least 6 characters.", acc.validate(true, "A", "a@b.co", "12345", "12345"));
        assertEquals("The two passwords do not match.", acc.validate(true, "A", "a@b.co", "secret1", "secret2"));
        assertEquals("No account with this email on this phone. Create one first.", acc.validate(false, "", "a@b.co", "secret1", ""));
        assertNull(acc.validate(true, "A", " A@B.co ", "secret1", "secret1"));
    }

    @Test public void signUpThenSignIn() throws Exception {
        TestData.MemKV kv = new TestData.MemKV();
        Accounts acc = new Accounts(kv);
        assertEquals(0, acc.count());
        assertNull(acc.finish(true, " Test User ", "Test@Example.com", "throwaway1", 1795352776612L));
        assertEquals(1, acc.count());
        Accounts.Account a = acc.get("test@example.com");
        assertNotNull(a);
        assertEquals("Test User", a.name);
        assertEquals(12, a.id.length());
        assertEquals(32, a.salt.length());
        assertEquals(Crypto.hash("throwaway1", a.salt), a.hash);

        // stored shape: email -> {id,name,email,salt,hash,created}
        JSONObject stored = new JSONObject(kv.get("sdm.accounts")).getJSONObject("test@example.com");
        assertEquals(a.id, stored.getString("id"));
        assertEquals("Test User", stored.getString("name"));
        assertEquals("test@example.com", stored.getString("email"));
        assertEquals(a.salt, stored.getString("salt"));
        assertEquals(a.hash, stored.getString("hash"));
        assertEquals(1795352776612L, stored.getLong("created"));

        assertEquals("An account with this email already exists on this phone. Sign in instead.", acc.validate(true, "X", "test@example.com", "secret1", "secret1"));
        assertEquals("Wrong password.", acc.finish(false, "", "test@example.com", "wrongpass", 0));
        assertNull(acc.finish(false, "", " TEST@example.com", "throwaway1", 0));

        Accounts reloaded = new Accounts(kv);
        assertEquals(1, reloaded.count());
        assertNull(reloaded.finish(false, "", "test@example.com", "throwaway1", 0));
    }

    @Test public void readsAnAccountStoredByTheWebVersion() {
        TestData.MemKV kv = new TestData.MemKV();
        kv.put("sdm.accounts", "{\"test@example.com\":{\"id\":\"3065aa95754b\",\"name\":\"Test\",\"email\":\"test@example.com\",\"salt\":\"" + SALT + "\",\"hash\":\"" + HASH + "\",\"created\":1795352776612}}");
        kv.put("sdm.session", "\"test@example.com\"");
        kv.put("sdm.apikey.3065aa95754b", "\"sk-ant-FAKE-not-a-real-key-0000\"");
        Accounts acc = new Accounts(kv);
        assertEquals("test@example.com", acc.session());
        Accounts.Account a = acc.get("test@example.com");
        assertEquals("sdm.v1.3065aa95754b", Accounts.progressKey(a));
        assertEquals("sk-ant-FAKE-not-a-real-key-0000", acc.apiKey(a));
        assertNull(acc.finish(false, "", "test@example.com", "throwaway1", 0));
        assertEquals("Wrong password.", acc.finish(false, "", "test@example.com", "throwaway2", 0));
    }

    @Test public void sessionAndPerAccountKey() {
        TestData.MemKV kv = new TestData.MemKV();
        Accounts acc = new Accounts(kv);
        acc.finish(true, "A", "a@example.com", "secret1", 1);
        acc.finish(true, "B", "b@example.com", "secret2", 2);
        Accounts.Account a = acc.get("a@example.com"), b = acc.get("b@example.com");
        assertNull(acc.session());
        assertTrue(acc.setSession("a@example.com"));
        assertEquals("\"a@example.com\"", kv.get("sdm.session"));
        assertEquals("a@example.com", acc.session());

        assertEquals("", acc.apiKey(a));
        assertTrue(acc.setApiKey(a, "sk-ant-FAKE-aaaaaaaaaaaaaaaa"));
        assertEquals("sk-ant-FAKE-aaaaaaaaaaaaaaaa", acc.apiKey(a));
        assertEquals("", acc.apiKey(b));                         // the key belongs to one account
        assertEquals("\"sk-ant-FAKE-aaaaaaaaaaaaaaaa\"", kv.get("sdm.apikey." + a.id));
        acc.removeApiKey(a);
        assertEquals("", acc.apiKey(a));

        acc.clearSession();
        assertNull(acc.session());
        kv.put("sdm.session", "\"gone@example.com\"");
        assertNull(acc.session());
    }

    @Test public void deleteRemovesProgressAndKey() throws Exception {
        TestData.MemKV kv = new TestData.MemKV();
        Accounts acc = new Accounts(kv);
        acc.finish(true, "A", "a@example.com", "secret1", 1);
        acc.finish(true, "B", "b@example.com", "secret2", 2);
        Accounts.Account a = acc.get("a@example.com"), b = acc.get("b@example.com");
        kv.put(Accounts.progressKey(a), new Progress().toJson());
        kv.put(Accounts.progressKey(b), new Progress().toJson());
        acc.setApiKey(a, "sk-ant-FAKE-aaaaaaaaaaaaaaaa");
        acc.setApiKey(b, "sk-ant-FAKE-bbbbbbbbbbbbbbbb");
        assertTrue(acc.delete(a));
        assertNull(kv.get("sdm.v1." + a.id));
        assertNull(kv.get("sdm.apikey." + a.id));
        assertNull(acc.get("a@example.com"));
        assertNotNull(kv.get("sdm.v1." + b.id));
        assertEquals("sk-ant-FAKE-bbbbbbbbbbbbbbbb", acc.apiKey(b));
        assertFalse(new JSONObject(kv.get("sdm.accounts")).has("a@example.com"));
        assertEquals(1, new Accounts(kv).count());
    }

    @Test public void aFailedWriteIsReportedAndRolledBack() {
        TestData.MemKV kv = new TestData.MemKV();
        Accounts acc = new Accounts(kv);
        kv.failWrites = true;
        assertEquals("This phone would not save the account. Free up some storage and try again.", acc.finish(true, "A", "a@example.com", "secret1", 1));
        assertEquals(0, acc.count());
        assertFalse(acc.setSession("a@example.com"));
        kv.failWrites = false;
        assertNull(acc.finish(true, "A", "a@example.com", "secret1", 1));
        kv.failWrites = true;
        assertFalse(acc.setApiKey(acc.get("a@example.com"), "sk-ant-FAKE-aaaaaaaaaaaaaaaa"));
    }
}
