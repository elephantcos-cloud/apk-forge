package com.apkforge.webhook;

import android.util.Log;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * APK Forge — Webhook Trigger (Java)
 * Sends build trigger events to custom webhook endpoints
 * (e.g. Render-hosted build service, self-hosted CI)
 */
public class WebhookTrigger {

    private static final String TAG = "WebhookTrigger";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;

    public WebhookTrigger() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    // ── Result ─────────────────────────────────────────────────────────────

    public static class WebhookResult {
        public final boolean success;
        public final int statusCode;
        public final String response;
        public final String error;

        public WebhookResult(boolean success, int statusCode, String response, String error) {
            this.success = success;
            this.statusCode = statusCode;
            this.response = response;
            this.error = error;
        }
    }

    // ── Main Methods ───────────────────────────────────────────────────────

    /**
     * Trigger a generic webhook with JSON payload
     */
    public WebhookResult trigger(String webhookUrl, Map<String, Object> payload) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() instanceof String)
                json.addProperty(entry.getKey(), (String) entry.getValue());
            else if (entry.getValue() instanceof Number)
                json.addProperty(entry.getKey(), (Number) entry.getValue());
            else if (entry.getValue() instanceof Boolean)
                json.addProperty(entry.getKey(), (Boolean) entry.getValue());
        }
        return sendPost(webhookUrl, json.toString(), null);
    }

    /**
     * Trigger with secret header (HMAC-style)
     */
    public WebhookResult triggerSecure(String webhookUrl,
                                        Map<String, Object> payload,
                                        String secretHeader,
                                        String secretValue) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() instanceof String)
                json.addProperty(entry.getKey(), (String) entry.getValue());
        }
        return sendPost(webhookUrl, json.toString(), new String[]{secretHeader, secretValue});
    }

    /**
     * Send a Render deploy hook (just a GET request)
     */
    public WebhookResult triggerRenderDeploy(String deployHookUrl) {
        Request req = new Request.Builder()
                .url(deployHookUrl)
                .get()
                .build();
        try (Response response = client.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            return new WebhookResult(response.isSuccessful(), response.code(), body, null);
        } catch (IOException e) {
            Log.e(TAG, "Render deploy hook error: " + e.getMessage());
            return new WebhookResult(false, -1, null, e.getMessage());
        }
    }

    // ── Private ────────────────────────────────────────────────────────────

    private WebhookResult sendPost(String url, String jsonBody, String[] secretHeader) {
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", "APK-Forge-Webhook/1.0");

        if (secretHeader != null && secretHeader.length == 2) {
            reqBuilder.header(secretHeader[0], secretHeader[1]);
        }

        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            Log.i(TAG, "Webhook response [" + response.code() + "]: " + respBody);
            return new WebhookResult(response.isSuccessful(), response.code(), respBody, null);
        } catch (IOException e) {
            Log.e(TAG, "Webhook error: " + e.getMessage());
            return new WebhookResult(false, -1, null, e.getMessage());
        }
    }
}
