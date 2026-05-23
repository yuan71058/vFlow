package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import com.chaomixian.vflow.ui.app_picker.AppUserSupport
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class InstalledAppSearchCandidate(
    val appName: String,
    val packageName: String,
    val activityName: String,
    val userId: Int,
    val userLabel: String,
    val isLaunchable: Boolean,
    val description: String = "",
)

internal data class InstalledAppSearchMatch(
    val candidate: InstalledAppSearchCandidate,
    val score: Int,
    val isExactMatch: Boolean,
)

internal data class InstalledAppQueryMatcher(
    val raw: String,
    val lower: String,
    val normalized: String,
) {
    val isBlank: Boolean
        get() = lower.isBlank() || normalized.isBlank()
}

internal object InstalledAppSearchSupport {
    private val matchTokenRegex = Regex("""[\p{L}\p{N}]+""")

    suspend fun searchApps(
        context: Context,
        query: String,
        userId: Int?,
        launchableOnly: Boolean,
        maxResults: Int,
    ): List<InstalledAppSearchMatch> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeForMatching(query)
        if (normalizedQuery.isBlank()) {
            return@withContext emptyList()
        }

        val targetUserId = userId ?: AppUserSupport.getCurrentUserId()
        val candidates = collectCandidates(
            context = context,
            userId = targetUserId,
            launchableOnly = launchableOnly,
        )

        return@withContext rankCandidates(
            query = query,
            candidates = candidates,
            maxResults = maxResults,
        )
    }

    internal fun rankCandidates(
        query: String,
        candidates: List<InstalledAppSearchCandidate>,
        maxResults: Int,
    ): List<InstalledAppSearchMatch> {
        val queryLower = query.trim().lowercase(Locale.ROOT)
        val normalizedQuery = normalizeForMatching(query)
        if (queryLower.isBlank() || normalizedQuery.isBlank()) {
            return emptyList()
        }

        val queryTokens = tokenize(query)
        return candidates.mapNotNull { candidate ->
            val (score, exactMatch) = scoreCandidate(
                queryLower = queryLower,
                normalizedQuery = normalizedQuery,
                queryTokens = queryTokens,
                candidate = candidate,
            )
            if (score <= 0) {
                null
            } else {
                InstalledAppSearchMatch(
                    candidate = candidate,
                    score = score,
                    isExactMatch = exactMatch,
                )
            }
        }.sortedWith(
            compareByDescending<InstalledAppSearchMatch> { it.score }
                .thenByDescending { it.candidate.isLaunchable }
                .thenByDescending { it.isExactMatch }
                .thenBy { it.candidate.appName.length }
                .thenBy { it.candidate.appName.lowercase(Locale.ROOT) }
                .thenBy { it.candidate.packageName }
        ).take(maxResults.coerceIn(1, 10))
    }

    internal fun normalizeForMatching(value: String): String {
        return matchTokenRegex
            .findAll(value.lowercase(Locale.ROOT))
            .joinToString(separator = "") { it.value }
    }

    internal fun createMatcher(query: String): InstalledAppQueryMatcher {
        return InstalledAppQueryMatcher(
            raw = query.trim(),
            lower = query.trim().lowercase(Locale.ROOT),
            normalized = normalizeForMatching(query),
        )
    }

    internal fun valueMatchesQuery(value: String, query: String): Boolean {
        return valueMatchesQuery(value, createMatcher(query))
    }

    internal fun valueMatchesQuery(value: String, matcher: InstalledAppQueryMatcher): Boolean {
        if (matcher.isBlank) return false
        val valueLower = value.lowercase(Locale.ROOT)
        if (valueLower.contains(matcher.lower)) return true
        return normalizeForMatching(value).contains(matcher.normalized)
    }

    private fun tokenize(value: String): List<String> {
        return matchTokenRegex
            .findAll(value.lowercase(Locale.ROOT))
            .map { it.value }
            .filter { it.length >= 2 }
            .toList()
    }

    private fun scoreCandidate(
        queryLower: String,
        normalizedQuery: String,
        queryTokens: List<String>,
        candidate: InstalledAppSearchCandidate,
    ): Pair<Int, Boolean> {
        val appLower = candidate.appName.lowercase(Locale.ROOT)
        val packageLower = candidate.packageName.lowercase(Locale.ROOT)
        val packageTail = candidate.packageName.substringAfterLast('.').lowercase(Locale.ROOT)
        val activityLower = candidate.activityName.lowercase(Locale.ROOT)
        val descriptionLower = candidate.description.lowercase(Locale.ROOT)

        val normalizedApp = normalizeForMatching(candidate.appName)
        val normalizedPackage = normalizeForMatching(candidate.packageName)
        val normalizedPackageTail = normalizeForMatching(packageTail)
        val normalizedActivity = normalizeForMatching(candidate.activityName)
        val normalizedDescription = normalizeForMatching(candidate.description)

        var score = 0
        var exactMatch = false

        fun scoreField(
            raw: String,
            normalized: String,
            exactScore: Int,
            prefixScore: Int,
            containsScore: Int,
        ) {
            if (raw.isBlank() && normalized.isBlank()) return
            if (queryLower == raw || normalizedQuery == normalized) {
                score = maxOf(score, exactScore)
                exactMatch = true
                return
            }
            if (raw.startsWith(queryLower) || normalized.startsWith(normalizedQuery)) {
                score = maxOf(score, prefixScore)
            }
            if (raw.contains(queryLower) || normalized.contains(normalizedQuery)) {
                score = maxOf(score, containsScore)
            }
        }

        scoreField(appLower, normalizedApp, exactScore = 1_200, prefixScore = 1_050, containsScore = 920)
        scoreField(packageTail, normalizedPackageTail, exactScore = 1_120, prefixScore = 980, containsScore = 860)
        scoreField(packageLower, normalizedPackage, exactScore = 1_100, prefixScore = 950, containsScore = 840)
        scoreField(activityLower, normalizedActivity, exactScore = 820, prefixScore = 760, containsScore = 680)
        scoreField(descriptionLower, normalizedDescription, exactScore = 780, prefixScore = 720, containsScore = 640)

        if (queryTokens.isNotEmpty()) {
            val matchedTokenCount = queryTokens.count { token ->
                val normalizedToken = normalizeForMatching(token)
                appLower.contains(token) ||
                    packageLower.contains(token) ||
                    activityLower.contains(token) ||
                    descriptionLower.contains(token) ||
                    normalizedApp.contains(normalizedToken) ||
                    normalizedPackage.contains(normalizedToken) ||
                    normalizedActivity.contains(normalizedToken) ||
                    normalizedDescription.contains(normalizedToken)
            }
            if (matchedTokenCount == queryTokens.size) {
                score = maxOf(score, 800 + matchedTokenCount * 15)
            }
        }

        if (candidate.isLaunchable && score > 0) {
            score += 10
        }

        if (candidate.appName == candidate.packageName && score > 0) {
            score -= 15
        }

        return score to exactMatch
    }

    private suspend fun collectCandidates(
        context: Context,
        userId: Int,
        launchableOnly: Boolean,
    ): List<InstalledAppSearchCandidate> {
        val currentUserId = AppUserSupport.getCurrentUserId()
        return if (userId == currentUserId) {
            collectCurrentUserCandidates(context, launchableOnly)
        } else {
            collectOtherUserCandidates(context, userId, launchableOnly)
        }
    }

    private fun collectCurrentUserCandidates(
        context: Context,
        launchableOnly: Boolean,
    ): List<InstalledAppSearchCandidate> {
        val pm = context.packageManager
        val currentUserId = AppUserSupport.getCurrentUserId()
        val userLabel = AppUserSupport.getUserLabel(context, currentUserId)
        val candidatesByKey = linkedMapOf<String, InstalledAppSearchCandidate>()

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(
            mainIntent,
            PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER,
        )

        resolveInfos.forEach { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@forEach
            val appInfo = activityInfo.applicationInfo
            val label = appInfo.loadLabel(pm).toString().ifBlank { appInfo.packageName }
            putCandidate(
                candidatesByKey,
                InstalledAppSearchCandidate(
                    appName = label,
                    packageName = appInfo.packageName,
                    activityName = activityInfo.name ?: "LAUNCH",
                    userId = currentUserId,
                    userLabel = userLabel,
                    isLaunchable = true,
                    description = resolveInfo.loadLabel(pm)?.toString().orEmpty(),
                )
            )
        }

        if (!launchableOnly) {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { appInfo ->
                val label = appInfo.loadLabel(pm).toString().ifBlank { appInfo.packageName }
                putCandidate(
                    candidatesByKey,
                    InstalledAppSearchCandidate(
                        appName = label,
                        packageName = appInfo.packageName,
                        activityName = "LAUNCH",
                        userId = currentUserId,
                        userLabel = userLabel,
                        isLaunchable = false,
                        description = appInfo.loadDescription(pm)?.toString().orEmpty(),
                    )
                )
            }
        }

        return candidatesByKey.values.toList()
    }

    private suspend fun collectOtherUserCandidates(
        context: Context,
        userId: Int,
        launchableOnly: Boolean,
    ): List<InstalledAppSearchCandidate> {
        val pm = context.packageManager
        val userLabel = AppUserSupport.getUserLabel(context, userId)
        val candidatesByKey = linkedMapOf<String, InstalledAppSearchCandidate>()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val userHandle = AppUserSupport.findUserHandle(context, userId)

        if (launcherApps != null && userHandle != null) {
            val launcherActivities = try {
                launcherApps.getActivityList(null, userHandle)
            } catch (_: Exception) {
                emptyList()
            }
            launcherActivities.forEach { launcherActivity ->
                val label = launcherActivity.label?.toString()?.ifBlank { null }
                    ?: launcherActivity.applicationInfo.loadLabel(pm).toString().ifBlank { null }
                    ?: launcherActivity.applicationInfo.packageName
                putCandidate(
                    candidatesByKey,
                    InstalledAppSearchCandidate(
                        appName = label,
                        packageName = launcherActivity.applicationInfo.packageName,
                        activityName = launcherActivity.componentName.className ?: "LAUNCH",
                        userId = userId,
                        userLabel = userLabel,
                        isLaunchable = true,
                        description = launcherActivity.label?.toString().orEmpty(),
                    )
                )
            }
        }

        if (!launchableOnly && AppUserSupport.isShellAvailable(context)) {
            val currentUserPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .associateBy { it.packageName }
            AppUserSupport.listPackagesForUser(context, userId).forEach { packageName ->
                val appInfo = currentUserPackages[packageName]
                val label = appInfo?.loadLabel(pm)?.toString()?.ifBlank { null } ?: packageName
                putCandidate(
                    candidatesByKey,
                    InstalledAppSearchCandidate(
                        appName = label,
                        packageName = packageName,
                        activityName = "LAUNCH",
                        userId = userId,
                        userLabel = userLabel,
                        isLaunchable = candidatesByKey["$packageName@$userId"]?.isLaunchable == true,
                        description = appInfo?.loadDescription(pm)?.toString().orEmpty(),
                    )
                )
            }
        }

        return candidatesByKey.values.toList()
    }

    private fun putCandidate(
        candidatesByKey: MutableMap<String, InstalledAppSearchCandidate>,
        candidate: InstalledAppSearchCandidate,
    ) {
        val key = "${candidate.packageName}@${candidate.userId}"
        val existing = candidatesByKey[key]
        if (
            existing == null ||
            (!existing.isLaunchable && candidate.isLaunchable) ||
            (existing.appName == existing.packageName && candidate.appName != candidate.packageName)
        ) {
            candidatesByKey[key] = candidate
        }
    }
}
