// 文件: main/java/com/chaomixian/vflow/ui/app_picker/UnifiedAppPickerSheet.kt
// 描述: 统一的应用/Activity选择器，支持两种模式：选择应用、选择Activity
package com.chaomixian.vflow.ui.app_picker

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.system.InstalledAppQueryMatcher
import com.chaomixian.vflow.core.workflow.module.system.InstalledAppSearchSupport
import com.chaomixian.vflow.databinding.SheetUnifiedAppPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 选择模式
 */
enum class AppPickerMode {
    SELECT_APP,       // 只选择应用包名
    SELECT_ACTIVITY   // 选择应用和Activity
}

/**
 * 统一应用选择器
 * 以BottomSheet形式展示，支持展开Activity列表
 */
class UnifiedAppPickerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetUnifiedAppPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var appAdapter: ExpandableAppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    // 选择模式
    private var mode: AppPickerMode = AppPickerMode.SELECT_ACTIVITY

    // 是否显示系统应用
    private var showSystemApps = false
    private var showAllUsers = false

    private var loadAppsJob: Job? = null
    private var filterAppsJob: Job? = null
    private var searchActivitiesJob: Job? = null
    private var warmActivitiesJob: Job? = null
    private val activitySearchCache = mutableMapOf<String, List<ActivityItem>>()
    private val activityIndexMutex = Mutex()

    // 回调
    private var onResultCallback: ((Intent) -> Unit)? = null

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SELECTED_PACKAGE_NAME = "selected_package_name"
        const val EXTRA_SELECTED_ACTIVITY_NAME = "selected_activity_name"
        const val EXTRA_SELECTED_USER_ID = "selected_user_id"

        fun newInstance(mode: AppPickerMode = AppPickerMode.SELECT_ACTIVITY): UnifiedAppPickerSheet {
            return UnifiedAppPickerSheet().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_MODE, mode.name)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString(EXTRA_MODE)?.let {
            mode = try { AppPickerMode.valueOf(it) } catch (e: Exception) { AppPickerMode.SELECT_ACTIVITY }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetUnifiedAppPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupRecyclerView()
        setupSearch()
        loadApps()
    }

    private fun setupUI() {
        // 根据模式设置标题
        binding.titleText.text = when (mode) {
            AppPickerMode.SELECT_APP -> getString(R.string.text_select_app)
            AppPickerMode.SELECT_ACTIVITY -> getString(R.string.text_select_activity)
        }

        // 系统应用开关
        binding.systemAppChip.apply {
            isChecked = showSystemApps
            setOnCheckedChangeListener { _, isChecked ->
                if (showSystemApps != isChecked) {
                    showSystemApps = isChecked
                    loadApps()
                }
            }
        }

        binding.root.findViewWithTag<Chip>("all_users_chip")?.apply {
            isChecked = showAllUsers
            setOnCheckedChangeListener { _, isChecked ->
                if (showAllUsers != isChecked) {
                    showAllUsers = isChecked
                    appAdapter.setShowUserChip(isChecked)
                    loadApps()
                }
            }
        }

        // 返回按钮
        binding.backButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        appAdapter = ExpandableAppListAdapter(
            mode = mode,
            onAppClick = { appInfo ->
                when (mode) {
                    AppPickerMode.SELECT_APP -> {
                        // 直接返回包名
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                            putExtra(EXTRA_SELECTED_USER_ID, appInfo.userId)
                        }
                        onResultCallback?.invoke(resultIntent)
                        dismiss()
                    }
                    AppPickerMode.SELECT_ACTIVITY -> {
                        // 展开Activity列表或显示Activity选择Sheet
                        if (appAdapter.isExpanded(appInfo)) {
                            appAdapter.collapse(appInfo)
                        } else {
                            // 加载并显示Activity
                            loadActivitiesForApp(appInfo) { activities ->
                                appAdapter.expand(appInfo, activities)
                            }
                        }
                    }
                }
            },
            onActivityClick = { appInfo, activityName ->
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_PACKAGE_NAME, appInfo.packageName)
                    putExtra(EXTRA_SELECTED_ACTIVITY_NAME, activityName)
                    putExtra(EXTRA_SELECTED_USER_ID, appInfo.userId)
                }
                onResultCallback?.invoke(resultIntent)
                dismiss()
            }
        )

        appAdapter.setShowUserChip(showAllUsers)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText)
                return true
            }
        })
    }

    private fun loadApps() {
        val context = context ?: return
        val pm = context.packageManager
        val currentUserId = AppUserSupport.getCurrentUserId()
        val currentUserLabel = AppUserSupport.getUserLabel(context, currentUserId)

        loadAppsJob?.cancel()
        loadAppsJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val appList = if (showAllUsers) {
                loadAppsForAllUsers(context, pm)
            } else if (showSystemApps) {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                installedApps.mapNotNull { appInfo ->
                    val label = appInfo.loadLabel(pm).toString()
                    if (label.isNotEmpty()) {
                        AppInfo(
                            appName = label,
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(pm),
                            userId = currentUserId,
                            userLabel = currentUserLabel
                        )
                    } else null
                }
            } else {
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
                )
                val uniquePackages = mutableMapOf<String, ResolveInfo>()
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (!uniquePackages.containsKey(packageName)) {
                        uniquePackages[packageName] = resolveInfo
                    }
                }
                uniquePackages.values.map { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    AppInfo(
                        appName = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm),
                        userId = currentUserId,
                        userLabel = currentUserLabel
                    )
                }
            }.sortedBy { it.appName.lowercase(Locale.getDefault()) }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                allApps = appList
                activitySearchCache.keys.retainAll(appList.mapTo(mutableSetOf()) { it.stableId })
                appAdapter.setShowUserChip(showAllUsers)
                appAdapter.updateData(allApps)
                if (mode == AppPickerMode.SELECT_ACTIVITY && appList.isNotEmpty()) {
                    warmActivitySearchCache(appList)
                }
            }
        }
    }

    private fun warmActivitySearchCache(apps: List<AppInfo>) {
        val context = context ?: return
        val launchLabel = getString(R.string.text_launch_app)
        warmActivitiesJob?.cancel()
        warmActivitiesJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(350L)
            val activitiesMap = mutableMapOf<String, List<ActivityItem>>()
            val cacheSnapshot = activitySearchCache.toMap()
            val loadedActivities = activityIndexMutex.withLock {
                withContext(Dispatchers.IO) {
                    loadActivitySearchIndex(context, apps, launchLabel, cacheSnapshot, activitiesMap)
                }
            }
            if (_binding == null) return@launch
            activitySearchCache.putAll(loadedActivities)
            val currentQuery = binding.searchView.query?.toString()?.trim().orEmpty()
            if (currentQuery.isNotBlank()) {
                val filteredApps = filterAppsForQuery(currentQuery, activitySearchCache)
            appAdapter.updateData(filteredApps)
            appAdapter.expandAll(filteredApps, activitySearchCache)
            }
        }
    }

    private suspend fun loadAppsForAllUsers(
        context: android.content.Context,
        pm: PackageManager
    ): List<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val appsByUser = linkedMapOf<String, AppInfo>()
        val currentUserId = AppUserSupport.getCurrentUserId()
        val defaultIcon = pm.defaultActivityIcon

        val currentLaunchableApps = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }.let { mainIntent ->
            pm.queryIntentActivities(
                mainIntent,
                PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
            )
        }.map { it.activityInfo.applicationInfo }
            .associateBy { it.packageName }

        val currentInstalledApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .associateBy { it.packageName }

        val users = try {
            AppUserSupport.getAvailableUsersForPicker(context)
        } catch (_: Exception) {
            AppUserSupport.getAvailableUsers(context)
        }

        for (user in users) {
            val launcherActivities = if (launcherApps != null && user.handle != null) {
                try {
                    launcherApps.getActivityList(null, user.handle)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            launcherActivities.forEach { activityInfo ->
                val appInfo = activityInfo.applicationInfo
                val label = activityInfo.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: appInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                    ?: appInfo.packageName
                val key = "${appInfo.packageName}@${user.userId}"
                appsByUser.putIfAbsent(
                    key,
                    AppInfo(
                        appName = label,
                        packageName = appInfo.packageName,
                        icon = activityInfo.getBadgedIcon(0),
                        userId = user.userId,
                        userLabel = AppUserSupport.getUserLabel(context, user.userId)
                    )
                )
            }

            if (user.userId == currentUserId) {
                val currentUserApps = if (showSystemApps) currentInstalledApps.values else currentLaunchableApps.values
                currentUserApps.forEach { appInfo ->
                    val label = appInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() }
                        ?: return@forEach
                    val key = "${appInfo.packageName}@${user.userId}"
                    appsByUser.putIfAbsent(
                        key,
                        AppInfo(
                            appName = label,
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(pm),
                            userId = user.userId,
                            userLabel = AppUserSupport.getUserLabel(context, user.userId)
                        )
                    )
                }
                continue
            }

            if (launcherActivities.isNotEmpty()) {
                continue
            }

            val shellPackages = try {
                AppUserSupport.listPackagesForUser(context, user.userId)
            } catch (_: Exception) {
                emptySet()
            }

            if (shellPackages.isEmpty()) {
                continue
            }

            val visiblePackages = if (showSystemApps) {
                shellPackages
            } else {
                shellPackages.filterTo(linkedSetOf()) { currentLaunchableApps.containsKey(it) }
            }

            visiblePackages.forEach { packageName ->
                val currentUserAppInfo = currentInstalledApps[packageName] ?: currentLaunchableApps[packageName]
                val label = currentUserAppInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: packageName
                val icon = currentUserAppInfo?.loadIcon(pm) ?: defaultIcon
                val key = "$packageName@${user.userId}"
                appsByUser.putIfAbsent(
                    key,
                    AppInfo(
                        appName = label,
                        packageName = packageName,
                        icon = icon,
                        userId = user.userId,
                        userLabel = AppUserSupport.getUserLabel(context, user.userId)
                    )
                )
            }
        }

        return appsByUser.values.sortedWith(
            compareBy<AppInfo> { it.appName.lowercase(Locale.getDefault()) }
                .thenBy { it.userId }
        )
    }

    private fun loadActivitiesForApp(appInfo: AppInfo, onLoaded: (List<ActivityItem>) -> Unit) {
        val context = context ?: return
        val launchLabel = getString(R.string.text_launch_app)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val activities = loadActivitiesForAppInternal(context, appInfo, launchLabel)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                onLoaded(activities)
            }
        }
    }

    private fun filterApps(query: String?) {
        if (query.isNullOrBlank()) {
            // 无搜索词时，恢复正常显示
            searchActivitiesJob?.cancel()
            filterAppsJob?.cancel()
            appAdapter.setSearchQuery("")
            appAdapter.collapseAll()
            appAdapter.updateData(allApps)
        } else {
            val normalizedQuery = query.trim()
            filterAppsJob?.cancel()
            filterAppsJob = viewLifecycleOwner.lifecycleScope.launch {
                filterAppsAsync(normalizedQuery)
            }
        }
    }

    private suspend fun filterAppsAsync(normalizedQuery: String) {
        val appsSnapshot = allApps
        val matcher = InstalledAppSearchSupport.createMatcher(normalizedQuery)

        if (mode != AppPickerMode.SELECT_ACTIVITY) {
            val filteredApps = withContext(Dispatchers.Default) {
                appsSnapshot.filter { app ->
                    app.matchesQuery(matcher)
                }
            }
            if (_binding == null || binding.searchView.query?.toString()?.trim() != normalizedQuery) return
            appAdapter.updateData(filteredApps, normalizedQuery)
            return
        }

        val visibleStableIds = appsSnapshot.mapTo(mutableSetOf()) { it.stableId }
        val cachedActivities = activitySearchCache.filterKeys { it in visibleStableIds }
        val immediateMatches = withContext(Dispatchers.Default) {
            filterAppsForQuery(appsSnapshot, matcher, cachedActivities)
        }
        if (_binding == null || binding.searchView.query?.toString()?.trim() != normalizedQuery) return
        appAdapter.updateData(immediateMatches, normalizedQuery)
        if (cachedActivities.isNotEmpty()) {
            appAdapter.expandAll(immediateMatches, cachedActivities)
        }

        if (visibleStableIds.all { it in activitySearchCache }) {
            return
        }

        val expectedQuery = normalizedQuery
        loadActivitiesForAllApps(appsSnapshot, debounceMillis = 250L) { activitiesMap ->
            val currentQuery = binding.searchView.query?.toString()?.trim().orEmpty()
            if (currentQuery != expectedQuery) {
                return@loadActivitiesForAllApps
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val expectedMatcher = InstalledAppSearchSupport.createMatcher(expectedQuery)
                val filteredApps = withContext(Dispatchers.Default) {
                    filterAppsForQuery(appsSnapshot, expectedMatcher, activitiesMap)
                }
                if (_binding == null || binding.searchView.query?.toString()?.trim() != expectedQuery) return@launch
                appAdapter.updateData(filteredApps, expectedQuery)
                appAdapter.expandAll(filteredApps, activitiesMap)
            }
        }
    }

    private fun filterAppsForQuery(
        apps: List<AppInfo>,
        matcher: InstalledAppQueryMatcher,
        activitiesMap: Map<String, List<ActivityItem>>,
    ): List<AppInfo> {
        return apps.filter { app ->
            app.matchesQuery(matcher) ||
                activitiesMap[app.stableId].orEmpty().any { activity ->
                    activity.matchesQuery(matcher)
                }
        }
    }

    private fun filterAppsForQuery(
        query: String,
        activitiesMap: Map<String, List<ActivityItem>>,
    ): List<AppInfo> {
        return filterAppsForQuery(allApps, InstalledAppSearchSupport.createMatcher(query), activitiesMap)
    }

    private fun AppInfo.matchesQuery(matcher: InstalledAppQueryMatcher): Boolean {
        return InstalledAppSearchSupport.valueMatchesQuery(appName, matcher) ||
            InstalledAppSearchSupport.valueMatchesQuery(packageName, matcher) ||
            InstalledAppSearchSupport.valueMatchesQuery(userLabel, matcher)
    }

    private fun ActivityItem.matchesQuery(matcher: InstalledAppQueryMatcher): Boolean {
        return name != "LAUNCH" && (
            InstalledAppSearchSupport.valueMatchesQuery(name, matcher) ||
                InstalledAppSearchSupport.valueMatchesQuery(label, matcher)
        )
    }

    /**
     * 批量加载多个应用的 Activity
     */
    private fun loadActivitiesForAllApps(
        apps: List<AppInfo>,
        debounceMillis: Long = 0L,
        onLoaded: (Map<String, List<ActivityItem>>) -> Unit
    ) {
        val context = context ?: return
        val launchLabel = getString(R.string.text_launch_app)

        searchActivitiesJob?.cancel()
        searchActivitiesJob = viewLifecycleOwner.lifecycleScope.launch {
            if (debounceMillis > 0L) {
                delay(debounceMillis)
            }

            val activitiesMap = mutableMapOf<String, List<ActivityItem>>()
            val cacheSnapshot = activitySearchCache.toMap()

            val loadedActivities = activityIndexMutex.withLock {
                withContext(Dispatchers.IO) {
                    loadActivitySearchIndex(context, apps, launchLabel, cacheSnapshot, activitiesMap)
                }
            }

            if (_binding == null) return@launch
            activitySearchCache.putAll(loadedActivities)
            onLoaded(activitiesMap)
        }
    }

    private suspend fun loadActivitySearchIndex(
        context: android.content.Context,
        apps: List<AppInfo>,
        launchLabel: String,
        cacheSnapshot: Map<String, List<ActivityItem>>,
        activitiesMap: MutableMap<String, List<ActivityItem>>,
    ): Map<String, List<ActivityItem>> {
        val loadedActivities = mutableMapOf<String, List<ActivityItem>>()
        val missingApps = apps.filter { app ->
            val cached = cacheSnapshot[app.stableId]
            if (cached != null) {
                activitiesMap[app.stableId] = cached
                false
            } else {
                true
            }
        }
        if (missingApps.isEmpty()) return loadedActivities

        val pm = context.packageManager
        val currentUserId = AppUserSupport.getCurrentUserId()
        val currentUserPackages = missingApps
            .filter { it.userId == currentUserId }
            .mapTo(mutableSetOf()) { it.packageName }

        if (currentUserPackages.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val packagesWithActivities = try {
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            } catch (_: Exception) {
                emptyList()
            }
            packagesWithActivities.forEach { packageInfo ->
                currentCoroutineContext().ensureActive()
                val packageName = packageInfo.packageName
                if (packageName !in currentUserPackages) return@forEach
                val activityItems = packageInfo.activities
                    ?.distinctBy { it.name }
                    ?.map { activityInfo ->
                        ActivityItem(
                            name = activityInfo.name,
                            label = activityInfo.nonLocalizedLabel?.toString().orEmpty(),
                            isExported = activityInfo.exported
                        )
                    }
                    .orEmpty()
                if (activityItems.isNotEmpty()) {
                    val stableId = "$packageName@$currentUserId"
                    val activities = listOf(launchActivityItem(launchLabel)) + activityItems
                    activitiesMap[stableId] = activities
                    loadedActivities[stableId] = activities
                }
            }
        }

        val unresolvedApps = missingApps.filter { it.stableId !in activitiesMap }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null && unresolvedApps.isNotEmpty()) {
            unresolvedApps.groupBy { it.userId }.forEach { (userId, userApps) ->
                currentCoroutineContext().ensureActive()
                val userHandle = AppUserSupport.findUserHandle(context, userId) ?: return@forEach
                val packagesForUser = userApps.mapTo(mutableSetOf()) { it.packageName }
                val launcherActivities = try {
                    launcherApps.getActivityList(null, userHandle)
                } catch (_: Exception) {
                    emptyList()
                }
                launcherActivities
                    .filter { it.applicationInfo.packageName in packagesForUser }
                    .groupBy { it.applicationInfo.packageName }
                    .forEach { (packageName, activities) ->
                        val stableId = "$packageName@$userId"
                        val activityItems = activities
                            .distinctBy { it.componentName.className }
                            .map { launcherActivity ->
                                ActivityItem(
                                    name = launcherActivity.componentName.className,
                                    label = launcherActivity.label?.toString().orEmpty(),
                                    isExported = true
                                )
                            }
                        val indexedActivities = listOf(launchActivityItem(launchLabel)) + activityItems
                        activitiesMap[stableId] = indexedActivities
                        loadedActivities[stableId] = indexedActivities
                    }
            }
        }

        missingApps
            .filter { it.stableId !in activitiesMap }
            .forEach { app ->
                val activities = listOf(launchActivityItem(launchLabel))
                activitiesMap[app.stableId] = activities
                loadedActivities[app.stableId] = activities
            }

        return loadedActivities
    }

    private fun launchActivityItem(launchLabel: String): ActivityItem {
        return ActivityItem(
            name = "LAUNCH",
            label = launchLabel,
            isExported = true
        )
    }

    private fun loadActivitiesForAppInternal(
        context: android.content.Context,
        appInfo: AppInfo,
        launchLabel: String
    ): List<ActivityItem> {
        val pm = context.packageManager
        val activities = mutableListOf<ActivityItem>()
        activities.add(launchActivityItem(launchLabel))

        val manifestActivities = try {
            val packageInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_ACTIVITIES)
            packageInfo.activities
                ?.distinctBy { it.name }
                ?.map { activityInfo ->
                    ActivityItem(
                        name = activityInfo.name,
                        label = activityInfo.loadLabel(pm).toString(),
                        isExported = activityInfo.exported
                    )
                }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        if (manifestActivities.isNotEmpty()) {
            activities.addAll(manifestActivities)
            return activities
        }

        val userHandle = AppUserSupport.findUserHandle(context, appInfo.userId) ?: return activities
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return activities
        val launcherActivities = try {
            launcherApps.getActivityList(appInfo.packageName, userHandle)
        } catch (_: Exception) {
            emptyList()
        }

        launcherActivities
            .distinctBy { it.componentName.className }
            .forEach { launcherActivity ->
                activities.add(
                    ActivityItem(
                        name = launcherActivity.componentName.className,
                        label = launcherActivity.label?.toString().orEmpty(),
                        isExported = true
                    )
                )
            }

        return activities
    }

    fun setOnResultCallback(callback: (Intent) -> Unit) {
        onResultCallback = callback
    }

    override fun onDestroyView() {
        loadAppsJob?.cancel()
        loadAppsJob = null
        filterAppsJob?.cancel()
        filterAppsJob = null
        searchActivitiesJob?.cancel()
        searchActivitiesJob = null
        warmActivitiesJob?.cancel()
        warmActivitiesJob = null
        super.onDestroyView()
        _binding = null
    }
}
