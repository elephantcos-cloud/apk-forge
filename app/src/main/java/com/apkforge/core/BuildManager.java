package com.apkforge.core;

import android.content.Context;
import android.util.Log;

import com.apkforge.model.BuildJob;
import com.apkforge.model.BuildStatus;
import com.apkforge.network.GithubApiClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * APK Forge — Build Manager (Java)
 * Orchestrates the entire build pipeline:
 *   1. Trigger GitHub Actions workflow
 *   2. Poll run status every N seconds
 *   3. Fetch artifacts when complete
 *   4. Notify observers via callback
 */
public class BuildManager {

    private static final String TAG = "BuildManager";
    private static final int POLL_INTERVAL_SEC = 8;
    private static final int MAX_POLL_ATTEMPTS = 90;  // 12 minutes max

    private final GithubApiClient apiClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pollingTask;
    private volatile int pollCount = 0;

    // ── Singleton ──────────────────────────────────────────────────────────
    private static volatile BuildManager instance;

    private BuildManager(Context ctx) {
        this.apiClient = GithubApiClient.getInstance();
        Log.i(TAG, "BuildManager initialized");
    }

    public static BuildManager getInstance(Context ctx) {
        if (instance == null) {
            synchronized (BuildManager.class) {
                if (instance == null) instance = new BuildManager(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    // ── Build Callbacks ────────────────────────────────────────────────────

    public interface BuildCallback {
        void onBuildQueued(long runId);
        void onStatusUpdate(long runId, BuildStatus status, int progress);
        void onBuildComplete(long runId, List<ArtifactInfo> artifacts);
        void onBuildFailed(long runId, String reason);
        void onError(String message);
    }

    // ── Main API ───────────────────────────────────────────────────────────

    /**
     * Trigger a new build and start monitoring it
     */
    public void startBuild(BuildJob job, BuildCallback callback) {
        if (!apiClient.hasToken()) {
            callback.onError("GitHub token not configured");
            return;
        }

        Log.i(TAG, "Starting build: " + job.getOwner() + "/" + job.getRepo() + " [" + job.getBranch() + "]");

        // Trigger workflow in background
        scheduler.execute(() -> {
            GithubApiClient.ApiResult<Void> triggerResult = apiClient.triggerWorkflow(
                    job.getOwner(), job.getRepo(), job.getWorkflowFile(), job.getBranch());

            if (!triggerResult.success) {
                String msg = buildErrorMessage(triggerResult);
                Log.e(TAG, "Trigger failed: " + msg);
                callback.onError(msg);
                return;
            }

            Log.i(TAG, "Workflow triggered, waiting for run to appear...");

            // Wait 3s then find the new run ID
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

            long runId = findLatestRunId(job);
            if (runId < 0) {
                callback.onError("Could not find workflow run after trigger");
                return;
            }

            Log.i(TAG, "Found run ID: " + runId);
            callback.onBuildQueued(runId);
            startPolling(job, runId, callback);
        });
    }

    /**
     * Attach to an existing run and monitor it
     */
    public void monitorRun(BuildJob job, long runId, BuildCallback callback) {
        startPolling(job, runId, callback);
    }

    /**
     * Cancel a running build
     */
    public void cancelBuild(String owner, String repo, long runId, BuildCallback callback) {
        stopPolling();
        scheduler.execute(() -> {
            GithubApiClient.ApiResult<Void> result = apiClient.cancelWorkflowRun(owner, repo, runId);
            if (result.success) {
                callback.onBuildFailed(runId, "Cancelled by user");
            } else {
                callback.onError("Failed to cancel: " + buildErrorMessage(result));
            }
        });
    }

    public void stopPolling() {
        if (pollingTask != null && !pollingTask.isDone()) {
            pollingTask.cancel(false);
            Log.d(TAG, "Polling stopped");
        }
        pollCount = 0;
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private void startPolling(BuildJob job, long runId, BuildCallback callback) {
        stopPolling();
        pollCount = 0;

        pollingTask = scheduler.scheduleWithFixedDelay(() -> {
            if (pollCount++ >= MAX_POLL_ATTEMPTS) {
                stopPolling();
                callback.onBuildFailed(runId, "Build timed out after 12 minutes");
                return;
            }

            GithubApiClient.ApiResult<JsonObject> result =
                    apiClient.getWorkflowRun(job.getOwner(), job.getRepo(), runId);

            if (!result.success) {
                Log.w(TAG, "Poll failed: " + buildErrorMessage(result));
                return;
            }

            JsonObject run = result.data;
            String status     = run.get("status").getAsString();
            String conclusion = run.has("conclusion") && !run.get("conclusion").isJsonNull()
                    ? run.get("conclusion").getAsString() : "";

            int progress = estimateProgress(status, pollCount);
            BuildStatus buildStatus = mapStatus(status, conclusion);
            callback.onStatusUpdate(runId, buildStatus, progress);

            Log.d(TAG, "Run " + runId + " | status=" + status + " conclusion=" + conclusion);

            if ("completed".equals(status)) {
                stopPolling();
                if ("success".equals(conclusion)) {
                    fetchArtifacts(job, runId, callback);
                } else {
                    callback.onBuildFailed(runId, "Build " + conclusion);
                }
            }

        }, 0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void fetchArtifacts(BuildJob job, long runId, BuildCallback callback) {
        GithubApiClient.ApiResult<JsonObject> result =
                apiClient.listArtifacts(job.getOwner(), job.getRepo(), runId);

        if (!result.success) {
            callback.onBuildFailed(runId, "Failed to fetch artifacts: " + buildErrorMessage(result));
            return;
        }

        List<ArtifactInfo> artifacts = new ArrayList<>();
        JsonArray arr = result.data.getAsJsonArray("artifacts");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject a = arr.get(i).getAsJsonObject();
            ArtifactInfo info = new ArtifactInfo();
            info.id          = a.get("id").getAsLong();
            info.name        = a.get("name").getAsString();
            info.sizeInBytes = a.get("size_in_bytes").getAsLong();
            info.owner       = job.getOwner();
            info.repo        = job.getRepo();
            artifacts.add(info);
        }

        Log.i(TAG, "Found " + artifacts.size() + " artifact(s)");
        callback.onBuildComplete(runId, artifacts);
    }

    private long findLatestRunId(BuildJob job) {
        GithubApiClient.ApiResult<JsonObject> result =
                apiClient.listWorkflowRuns(job.getOwner(), job.getRepo(), job.getWorkflowFile(), 5);
        if (!result.success || !result.data.has("workflow_runs")) return -1;

        JsonArray runs = result.data.getAsJsonArray("workflow_runs");
        if (runs.size() == 0) return -1;

        return runs.get(0).getAsJsonObject().get("id").getAsLong();
    }

    private BuildStatus mapStatus(String status, String conclusion) {
        switch (status) {
            case "queued":     return BuildStatus.QUEUED;
            case "in_progress": return BuildStatus.RUNNING;
            case "completed":
                switch (conclusion) {
                    case "success":   return BuildStatus.SUCCESS;
                    case "failure":   return BuildStatus.FAILED;
                    case "cancelled": return BuildStatus.CANCELLED;
                    default:          return BuildStatus.FAILED;
                }
            default: return BuildStatus.UNKNOWN;
        }
    }

    private int estimateProgress(String status, int polls) {
        switch (status) {
            case "queued":      return 5;
            case "in_progress": return Math.min(10 + (polls * 5), 85);
            case "completed":   return 100;
            default:            return 0;
        }
    }

    private <T> String buildErrorMessage(GithubApiClient.ApiResult<T> result) {
        if (result.isAuthError())    return "Authentication failed. Check your GitHub token.";
        if (result.isNotFound())     return "Repository or workflow not found.";
        if (result.isNetworkError()) return "Network error: " + result.message;
        return "Error " + result.code + ": " + result.message;
    }

    // ── Data classes ───────────────────────────────────────────────────────

    public static class ArtifactInfo {
        public long   id;
        public String name;
        public long   sizeInBytes;
        public String owner;
        public String repo;

        public String getFormattedSize() {
            if (sizeInBytes < 1024) return sizeInBytes + " B";
            if (sizeInBytes < 1024 * 1024) return String.format("%.1f KB", sizeInBytes / 1024.0);
            return String.format("%.2f MB", sizeInBytes / (1024.0 * 1024));
        }
    }
}
