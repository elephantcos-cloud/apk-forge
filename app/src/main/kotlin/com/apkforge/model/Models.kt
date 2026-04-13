package com.apkforge.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Represents a build job configuration
 */
@Parcelize
data class BuildJob(
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val workflowFile: String = "build.yml",
    val tag: String = "",
    val webhookUrl: String = ""
) : Parcelable {
    val fullRepoPath get() = "$owner/$repo"
    val isValid get() = owner.isNotBlank() && repo.isNotBlank()
}

/**
 * Build status states
 */
enum class BuildStatus(val displayName: String, val emoji: String) {
    UNKNOWN("Unknown", "❓"),
    QUEUED("Queued", "⏳"),
    RUNNING("Running", "🔨"),
    SUCCESS("Success", "✅"),
    FAILED("Failed", "❌"),
    CANCELLED("Cancelled", "🚫");

    val isTerminal get() = this == SUCCESS || this == FAILED || this == CANCELLED
    val isActive   get() = this == QUEUED  || this == RUNNING
}

/**
 * A recorded build history entry (stored in Room DB)
 */
@androidx.room.Entity(tableName = "build_history")
data class BuildRecord(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: Long,
    val owner: String,
    val repo: String,
    val branch: String,
    val workflowFile: String,
    val status: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val artifactName: String? = null,
    val artifactSizeBytes: Long? = null,
    val localApkPath: String? = null,
    val commitSha: String? = null,
    val notes: String? = null
) {
    val duration: Long? get() = finishedAt?.minus(startedAt)
    val buildStatus: BuildStatus get() = try {
        BuildStatus.valueOf(status.uppercase())
    } catch (e: Exception) {
        BuildStatus.UNKNOWN
    }
    val formattedDuration: String get() = duration?.let {
        val sec = it / 1000
        if (sec < 60) "${sec}s" else "${sec/60}m ${sec%60}s"
    } ?: "--"
}

/**
 * Repository info model
 */
data class RepoInfo(
    val name: String,
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val stars: Int,
    val language: String?,
    val updatedAt: String?
)

/**
 * Workflow file info
 */
data class WorkflowInfo(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
) {
    val fileName get() = path.substringAfterLast("/")
    val isActive get() = state == "active"
}
