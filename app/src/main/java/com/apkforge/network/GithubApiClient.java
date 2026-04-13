package com.apkforge.network;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * APK Forge — GitHub API Client (Java)
 * Handles all GitHub REST API v3 interactions:
 * - Trigger workflow dispatch
 * - Poll run status
 * - List artifacts
 * - Download artifact URLs
 */
public class GithubApiClient {

    private static final String TAG = "GithubApiClient";
    private static final String BASE_URL = "https://api.github.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private volatile String authToken;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile GithubApiClient instance;

    private GithubApiClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(
                msg -> Log.d(TAG, msg));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", "APK-Forge/1.0");
                    if (authToken != null && !authToken.isEmpty()) {
                        builder.header("Authorization", "Bearer " + authToken);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();
    }

    public static GithubApiClient getInstance() {
        if (instance == null) {
            synchronized (GithubApiClient.class) {
                if (instance == null) instance = new GithubApiClient();
            }
        }
        return instance;
    }

    // ── Token management ───────────────────────────────────────────────────
    public void setToken(String token) {
        this.authToken = token;
        Log.i(TAG, "GitHub token updated");
    }

    public boolean hasToken() {
        return authToken != null && !authToken.isEmpty();
    }

    // ── API Methods ────────────────────────────────────────────────────────

    /**
     * Validate token by fetching authenticated user
     */
    public ApiResult<JsonObject> validateToken() {
        Request req = new Request.Builder()
                .url(BASE_URL + "/user")
                .get()
                .build();
        return execute(req);
    }

    /**
     * Get repository info
     */
    public ApiResult<JsonObject> getRepository(String owner, String repo) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo)
                .get()
                .build();
        return execute(req);
    }

    /**
     * List branches of a repository
     */
    public ApiResult<JsonArray> listBranches(String owner, String repo) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo + "/branches?per_page=50")
                .get()
                .build();
        return executeArray(req);
    }

    /**
     * Trigger a workflow_dispatch event
     * @param owner   GitHub username/org
     * @param repo    Repository name
     * @param workflowFile  Workflow filename e.g. "build.yml"
     * @param branch  Branch to run on
     */
    public ApiResult<Void> triggerWorkflow(String owner, String repo,
                                            String workflowFile, String branch) {
        JsonObject body = new JsonObject();
        body.addProperty("ref", branch);

        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/workflows/" + workflowFile + "/dispatches")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        return executeVoid(req);
    }

    /**
     * List workflow runs (most recent first)
     */
    public ApiResult<JsonObject> listWorkflowRuns(String owner, String repo,
                                                   String workflowFile, int perPage) {
        String url = BASE_URL + "/repos/" + owner + "/" + repo
                + "/actions/workflows/" + workflowFile
                + "/runs?per_page=" + perPage + "&status=all";
        Request req = new Request.Builder().url(url).get().build();
        return execute(req);
    }

    /**
     * Get a single workflow run by ID
     */
    public ApiResult<JsonObject> getWorkflowRun(String owner, String repo, long runId) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo + "/actions/runs/" + runId)
                .get()
                .build();
        return execute(req);
    }

    /**
     * List artifacts from a run
     */
    public ApiResult<JsonObject> listArtifacts(String owner, String repo, long runId) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/runs/" + runId + "/artifacts")
                .get()
                .build();
        return execute(req);
    }

    /**
     * Get download URL for an artifact
     */
    public ApiResult<String> getArtifactDownloadUrl(String owner, String repo, long artifactId) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/artifacts/" + artifactId + "/zip")
                .get()
                .build();
        try (Response response = httpClient.newCall(req).execute()) {
            if (response.isRedirect() || response.code() == 302) {
                String location = response.header("Location");
                return ApiResult.success(location);
            }
            if (response.isSuccessful()) {
                return ApiResult.success(response.request().url().toString());
            }
            return ApiResult.failure(response.code(), "Failed: " + response.message());
        } catch (IOException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    /**
     * Cancel a running workflow
     */
    public ApiResult<Void> cancelWorkflowRun(String owner, String repo, long runId) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/runs/" + runId + "/cancel")
                .post(RequestBody.create("", JSON))
                .build();
        return executeVoid(req);
    }

    /**
     * Get workflow run logs URL
     */
    public ApiResult<JsonObject> getRunLogs(String owner, String repo, long runId) {
        Request req = new Request.Builder()
                .url(BASE_URL + "/repos/" + owner + "/" + repo
                        + "/actions/runs/" + runId + "/logs")
                .get()
                .build();
        return execute(req);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private ApiResult<JsonObject> execute(Request req) {
        try (Response response = httpClient.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (response.isSuccessful()) {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                return ApiResult.success(json);
            }
            return ApiResult.failure(response.code(), extractErrorMessage(body));
        } catch (IOException e) {
            Log.e(TAG, "Network error: " + e.getMessage());
            return ApiResult.error(e.getMessage());
        }
    }

    private ApiResult<JsonArray> executeArray(Request req) {
        try (Response response = httpClient.newCall(req).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.isSuccessful()) {
                JsonArray json = JsonParser.parseString(body).getAsJsonArray();
                return ApiResult.success(json);
            }
            return ApiResult.failure(response.code(), extractErrorMessage(body));
        } catch (IOException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    private ApiResult<Void> executeVoid(Request req) {
        try (Response response = httpClient.newCall(req).execute()) {
            if (response.isSuccessful() || response.code() == 204) {
                return ApiResult.success(null);
            }
            String body = response.body() != null ? response.body().string() : "";
            return ApiResult.failure(response.code(), extractErrorMessage(body));
        } catch (IOException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    private String extractErrorMessage(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("message")) return obj.get("message").getAsString();
        } catch (Exception ignored) {}
        return json.isEmpty() ? "Unknown error" : json;
    }

    // ── Result wrapper ─────────────────────────────────────────────────────

    public static class ApiResult<T> {
        public final boolean success;
        public final T data;
        public final int code;
        public final String message;

        private ApiResult(boolean success, T data, int code, String message) {
            this.success = success;
            this.data = data;
            this.code = code;
            this.message = message;
        }

        public static <T> ApiResult<T> success(T data) {
            return new ApiResult<>(true, data, 200, null);
        }

        public static <T> ApiResult<T> failure(int code, String message) {
            return new ApiResult<>(false, null, code, message);
        }

        public static <T> ApiResult<T> error(String message) {
            return new ApiResult<>(false, null, -1, message);
        }

        public boolean isNetworkError() { return code == -1; }
        public boolean isAuthError()    { return code == 401 || code == 403; }
        public boolean isNotFound()     { return code == 404; }
    }
}
