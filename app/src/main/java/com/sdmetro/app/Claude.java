package com.sdmetro.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One blocking call to the Anthropic Messages API. Call it off the main thread. Pure Java. */
public final class Claude {
    private Claude() { }

    public static final int MAX_TOKENS = 4000;
    private static final String URL_MESSAGES = "https://api.anthropic.com/v1/messages";

    /** Sends one prompt and returns the JSON object in the reply. */
    public static JSONObject askJson(String prompt, String model, String apiKey) throws Gen.Fail {
        return Gen.extractJson(ask(Gen.JSON_ONLY + prompt, model, apiKey));
    }

    public static String ask(String prompt, String model, String apiKey) throws Gen.Fail {
        HttpURLConnection c = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("max_tokens", MAX_TOKENS);
            body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", prompt)));
            c = (HttpURLConnection) new URL(URL_MESSAGES).openConnection();
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
            return parse(code, readAll(code >= 400 ? c.getErrorStream() : c.getInputStream()));
        } catch (Gen.Fail e) {
            throw e;
        } catch (java.net.UnknownHostException e) {
            throw new Gen.Fail("offline", "No internet connection.");
        } catch (java.net.SocketTimeoutException e) {
            throw new Gen.Fail("unknown", "Claude took too long to answer.");
        } catch (java.io.IOException e) {
            throw new Gen.Fail("offline", "Network error.");
        } catch (Exception e) {
            throw new Gen.Fail("unknown", String.valueOf(e.getMessage()));
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Turns an HTTP status and body into the reply text, or the failure to show. A reply cut off at the token limit is a failure. */
    public static String parse(int code, String txt) throws Gen.Fail {
        if (code >= 400) {
            String msg = "HTTP " + code;
            try { msg = new JSONObject(txt).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
            String ec = (code == 401 || code == 403) ? "bad_key" : (code == 429 || code == 529) ? "rate_limited" : "unknown";
            throw new Gen.Fail(ec, msg);
        }
        try {
            JSONObject o = new JSONObject(txt);
            if ("max_tokens".equals(o.optString("stop_reason"))) throw new Gen.Fail("truncated", "the reply was cut off before it finished");
            JSONArray content = o.getJSONArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject part = content.optJSONObject(i);
                if (part != null && "text".equals(part.optString("type"))) sb.append(part.optString("text"));
            }
            return sb.toString();
        } catch (Gen.Fail e) {
            throw e;
        } catch (Exception e) {
            throw new Gen.Fail("bad_output", "Claude replied in an unexpected format.");
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
