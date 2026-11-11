package com.sdmetro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** One screen: the bundled web UI in a WebView, plus a small bridge for password hashing and Claude calls. */
public class MainActivity extends Activity {
    private static final String HOME = "file:///android_asset/index.html";
    private WebView web;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF16337F);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // localStorage: accounts and progress live here
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "SDMNative");
        setContentView(web);
        web.loadUrl(HOME);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void reply(final String id, final boolean ok, final String text) {
        final String js = "window.__sdmCb&&window.__sdmCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(text) + ")";
        runOnUiThread(new Runnable() {
            @Override public void run() { if (web != null) web.evaluateJavascript(js, null); }
        });
    }

    private static String err(String code, String message) {
        try { return new JSONObject().put("code", code).put("message", message).toString(); }
        catch (Exception e) { return "{\"code\":\"unknown\",\"message\":\"error\"}"; }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private class Bridge {
        /** PBKDF2-HMAC-SHA256 of a password; returns hex, or "" on failure. */
        @JavascriptInterface
        public String hash(String password, String saltHex) {
            try {
                byte[] salt = new byte[saltHex.length() / 2];
                for (int i = 0; i < salt.length; i++) salt[i] = (byte) Integer.parseInt(saltHex.substring(i * 2, i * 2 + 2), 16);
                PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
                byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                StringBuilder sb = new StringBuilder();
                for (byte b : out) sb.append(String.format("%02x", b & 0xff));
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        /** Sends one prompt to the Anthropic Messages API and calls window.__sdmCb(id, ok, text). */
        @JavascriptInterface
        public void ask(final String id, final String prompt, final String model, final String apiKey) {
            pool.execute(new Runnable() {
                @Override public void run() {
                    HttpURLConnection c = null;
                    try {
                        JSONObject body = new JSONObject();
                        body.put("model", model);
                        body.put("max_tokens", 2000);
                        body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
                        c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                        c.setRequestMethod("POST");
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(90000);
                        c.setDoOutput(true);
                        c.setRequestProperty("content-type", "application/json");
                        c.setRequestProperty("x-api-key", apiKey);
                        c.setRequestProperty("anthropic-version", "2023-06-01");
                        OutputStream os = c.getOutputStream();
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                        os.close();
                        int code = c.getResponseCode();
                        String txt = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
                        if (code >= 400) {
                            String msg = "HTTP " + code;
                            try { msg = new JSONObject(txt).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
                            String ec = (code == 401 || code == 403) ? "bad_key" : (code == 429 || code == 529) ? "rate_limited" : "unknown";
                            reply(id, false, err(ec, msg));
                            return;
                        }
                        JSONArray content = new JSONObject(txt).getJSONArray("content");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < content.length(); i++) {
                            JSONObject part = content.getJSONObject(i);
                            if ("text".equals(part.optString("type"))) sb.append(part.optString("text"));
                        }
                        reply(id, true, sb.toString());
                    } catch (java.net.UnknownHostException e) {
                        reply(id, false, err("offline", "No internet connection."));
                    } catch (java.net.SocketTimeoutException e) {
                        reply(id, false, err("unknown", "Claude took too long to answer."));
                    } catch (java.io.IOException e) {
                        reply(id, false, err("offline", "Network error."));
                    } catch (Exception e) {
                        reply(id, false, err("unknown", String.valueOf(e.getMessage())));
                    } finally {
                        if (c != null) c.disconnect();
                    }
                }
            });
        }
    }
}
