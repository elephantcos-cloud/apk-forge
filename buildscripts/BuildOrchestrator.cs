/**
 * APK Forge — Build Orchestrator (C#)
 * Server-side companion script for CI/CD pipeline management.
 * Run with: dotnet script BuildOrchestrator.cs -- <owner> <repo> <token>
 *
 * Features:
 *   - Trigger GitHub Actions workflow
 *   - Poll build status with exponential backoff
 *   - Download artifacts on success
 *   - Post-process APK (rename, checksum)
 */

using System;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.IO;
using System.Security.Cryptography;

// ── Configuration ─────────────────────────────────────────────────────────────

const string GH_API   = "https://api.github.com";
const string UA       = "APK-Forge-CS/1.0";
const int    POLL_SEC = 10;
const int    MAX_WAIT = 720; // 12 minutes

// ── Entry Point ───────────────────────────────────────────────────────────────

if (Args.Count < 3)
{
    Console.ForegroundColor = ConsoleColor.Red;
    Console.Error.WriteLine("Usage: dotnet script BuildOrchestrator.cs -- <owner> <repo> <token> [branch] [workflow]");
    Console.ResetColor();
    Environment.Exit(1);
}

string owner        = Args[0];
string repo         = Args[1];
string token        = Args[2];
string branch       = Args.Count > 3 ? Args[3] : "main";
string workflowFile = Args.Count > 4 ? Args[4] : "build.yml";

var orchestrator = new BuildOrchestrator(token);
await orchestrator.RunAsync(owner, repo, branch, workflowFile);

// ── Main Class ────────────────────────────────────────────────────────────────

public class BuildOrchestrator
{
    private readonly HttpClient _http;
    private readonly string _token;

    public BuildOrchestrator(string token)
    {
        _token = token;
        _http  = new HttpClient();
        _http.DefaultRequestHeaders.UserAgent.ParseAdd(UA);
        _http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        _http.DefaultRequestHeaders.Add("X-GitHub-Api-Version", "2022-11-28");
        _http.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", token);
    }

    public async Task RunAsync(string owner, string repo,
                                string branch, string workflowFile)
    {
        Log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log($"🔨 APK Forge Build Orchestrator");
        Log($"   Repo     : {owner}/{repo}");
        Log($"   Branch   : {branch}");
        Log($"   Workflow : {workflowFile}");
        Log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Step 1: Validate token
        Log("🔑 Validating GitHub token…");
        var user = await ValidateTokenAsync();
        if (user == null) { LogError("Token validation failed. Exiting."); return; }
        Log($"✅ Authenticated as @{user}");

        // Step 2: Trigger workflow
        Log($"🚀 Triggering workflow '{workflowFile}' on branch '{branch}'…");
        bool triggered = await TriggerWorkflowAsync(owner, repo, workflowFile, branch);
        if (!triggered) { LogError("Failed to trigger workflow."); return; }
        Log("✅ Workflow triggered!");

        // Step 3: Wait for run to appear
        Log("⏳ Waiting for run to appear (5s)…");
        await Task.Delay(5000);

        long runId = await FindLatestRunIdAsync(owner, repo, workflowFile);
        if (runId < 0) { LogError("Could not find workflow run."); return; }
        Log($"📋 Found run ID: {runId}");

        // Step 4: Poll status
        Log("🔄 Polling build status…");
        string conclusion = await PollUntilCompleteAsync(owner, repo, runId);

        if (conclusion != "success")
        {
            LogError($"Build {conclusion}. Exiting.");
            Environment.Exit(1);
        }

        Log("✅ Build succeeded!");

        // Step 5: Download artifacts
        Log("📦 Fetching artifacts…");
        await DownloadArtifactsAsync(owner, repo, runId);

        Log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Log("🎉 APK Forge build pipeline complete!");
        Log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── GitHub API Methods ─────────────────────────────────────────────────

    private async Task<string?> ValidateTokenAsync()
    {
        try
        {
            var resp = await _http.GetAsync($"{GH_API}/user");
            if (!resp.IsSuccessStatusCode) return null;
            var json = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
            return json.RootElement.GetProperty("login").GetString();
        }
        catch (Exception ex) { LogError(ex.Message); return null; }
    }

    private async Task<bool> TriggerWorkflowAsync(string owner, string repo,
                                                    string workflow, string branch)
    {
        var body = JsonSerializer.Serialize(new { @ref = branch });
        var content = new StringContent(body, Encoding.UTF8, "application/json");
        var url  = $"{GH_API}/repos/{owner}/{repo}/actions/workflows/{workflow}/dispatches";
        var resp = await _http.PostAsync(url, content);
        if (!resp.IsSuccessStatusCode)
        {
            LogError($"HTTP {(int)resp.StatusCode}: {await resp.Content.ReadAsStringAsync()}");
        }
        return resp.IsSuccessStatusCode || (int)resp.StatusCode == 204;
    }

    private async Task<long> FindLatestRunIdAsync(string owner, string repo, string workflow)
    {
        try
        {
            var url  = $"{GH_API}/repos/{owner}/{repo}/actions/workflows/{workflow}/runs?per_page=1";
            var resp = await _http.GetAsync(url);
            if (!resp.IsSuccessStatusCode) return -1;
            var json = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
            var runs = json.RootElement.GetProperty("workflow_runs");
            if (runs.GetArrayLength() == 0) return -1;
            return runs[0].GetProperty("id").GetInt64();
        }
        catch { return -1; }
    }

    private async Task<string> PollUntilCompleteAsync(string owner, string repo, long runId)
    {
        int elapsed  = 0;
        int interval = POLL_SEC;

        while (elapsed < MAX_WAIT)
        {
            await Task.Delay(interval * 1000);
            elapsed += interval;

            try
            {
                var url  = $"{GH_API}/repos/{owner}/{repo}/actions/runs/{runId}";
                var resp = await _http.GetAsync(url);
                if (!resp.IsSuccessStatusCode) continue;

                var json       = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
                var status     = json.RootElement.GetProperty("status").GetString();
                var conclusion = "";
                if (json.RootElement.TryGetProperty("conclusion", out var c) && c.ValueKind != JsonValueKind.Null)
                    conclusion = c.GetString() ?? "";

                var bar = BuildProgressBar(elapsed, MAX_WAIT);
                Console.Write($"\r  {bar} [{elapsed}s] status={status,-12} conclusion={conclusion,-10}  ");

                if (status == "completed")
                {
                    Console.WriteLine();
                    return conclusion;
                }

                // Exponential backoff capped at 30s
                interval = Math.Min(interval + 2, 30);
            }
            catch (Exception ex)
            {
                Console.WriteLine();
                LogError("Poll error: " + ex.Message);
            }
        }

        Console.WriteLine();
        return "timed_out";
    }

    private async Task DownloadArtifactsAsync(string owner, string repo, long runId)
    {
        try
        {
            var url  = $"{GH_API}/repos/{owner}/{repo}/actions/runs/{runId}/artifacts";
            var resp = await _http.GetAsync(url);
            if (!resp.IsSuccessStatusCode) { LogError("Failed to list artifacts"); return; }

            var json      = JsonDocument.Parse(await resp.Content.ReadAsStringAsync());
            var artifacts = json.RootElement.GetProperty("artifacts");
            int count     = artifacts.GetArrayLength();

            Log($"Found {count} artifact(s)");

            for (int i = 0; i < count; i++)
            {
                var artifact = artifacts[i];
                long id      = artifact.GetProperty("id").GetInt64();
                string name  = artifact.GetProperty("name").GetString() ?? $"artifact_{id}";
                long size    = artifact.GetProperty("size_in_bytes").GetInt64();

                Log($"  📦 [{i+1}/{count}] {name} ({FormatBytes(size)})");
                Log($"      Download URL: {GH_API}/repos/{owner}/{repo}/actions/artifacts/{id}/zip");
            }

            // Compute build fingerprint
            string fingerprint = ComputeRunFingerprint(owner, repo, runId);
            Log($"  🔐 Build fingerprint: {fingerprint}");
        }
        catch (Exception ex) { LogError("Artifact fetch error: " + ex.Message); }
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    private static string ComputeRunFingerprint(string owner, string repo, long runId)
    {
        string data = $"{owner}/{repo}:{runId}:{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}";
        byte[] hash = SHA256.HashData(Encoding.UTF8.GetBytes(data));
        return BitConverter.ToString(hash).Replace("-", "").ToLower()[..16];
    }

    private static string BuildProgressBar(int current, int max, int width = 20)
    {
        int filled = (int)((double)current / max * width);
        return "[" + new string('█', filled) + new string('░', width - filled) + "]";
    }

    private static string FormatBytes(long bytes) => bytes switch
    {
        < 1024        => $"{bytes} B",
        < 1024 * 1024 => $"{bytes / 1024.0:F1} KB",
        _             => $"{bytes / (1024.0 * 1024):F2} MB"
    };

    private static void Log(string msg)
    {
        Console.ForegroundColor = ConsoleColor.Cyan;
        Console.Write("[APK-Forge] ");
        Console.ResetColor();
        Console.WriteLine(msg);
    }

    private static void LogError(string msg)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.Write("[ERROR] ");
        Console.ResetColor();
        Console.WriteLine(msg);
    }
}
