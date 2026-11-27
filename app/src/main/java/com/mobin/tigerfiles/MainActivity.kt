package com.mobin.tigerfiles

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mobin.tigerfiles.ui.theme.TigerFilesTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TigerFilesTheme { TigerFilesApp() } }
    }
}

private enum class AppScreen(val label: String) {
    HOME("خانه"), FILES("فایل‌ها"), FAVORITES("علاقه‌مندی‌ها"), SETTINGS("تنظیمات")
}
private enum class FileCategory(val label: String, val icon: ImageVector) {
    ALL("همه فایل‌ها", Icons.Default.FolderOpen),
    FOLDERS("پوشه‌ها", Icons.Default.Folder),
    IMAGES("تصاویر", Icons.Default.Image),
    VIDEOS("ویدئوها", Icons.Default.Movie),
    AUDIO("موسیقی", Icons.Default.MusicNote),
    DOCUMENTS("اسناد", Icons.Default.Description),
    APK("برنامه‌ها", Icons.Default.Android),
    ARCHIVES("فشرده", Icons.Default.Archive)
}
private enum class SortMode(val label: String) {
    NAME("نام"), SIZE("حجم"), DATE("آخرین تغییر"), TYPE("نوع فایل")
}
private data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
    val fraction: Float get() = if (total > 0L) used.toFloat() / total.toFloat() else 0f
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TigerFilesApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("tiger_files_preferences", Context.MODE_PRIVATE) }
    val initialRoot = remember(context) {
        prefs.getString("root_uri", null)?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull() }
    }
    var rootFolder by remember { mutableStateOf(initialRoot) }
    val pathStack = remember { mutableStateListOf<DocumentFile>().apply { initialRoot?.let { add(it) } } }
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var category by remember { mutableStateOf(FileCategory.ALL) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var gridMode by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var allFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var currentFolderFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var dashboardCounts by remember { mutableStateOf(FileCategory.values().associateWith { 0 }) }
    var dashboardFileCount by remember { mutableIntStateOf(0) }
    var dashboardFolderCount by remember { mutableIntStateOf(0) }
    var dashboardLargestFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    val selectedUris = remember { mutableStateListOf<String>() }
    val favoriteUris = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorites", emptySet()).orEmpty()) } }
    val recentUris = remember { mutableStateListOf<String>().apply { addAll(prefs.getString("recents", "").orEmpty().split("\n").filter { it.isNotBlank() }) } }
    var clipboardFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var clipboardIsCut by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createAsFile by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var detailsTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editorFile by remember { mutableStateOf<DocumentFile?>(null) }
    var editorText by remember { mutableStateOf("") }
    var editorLoading by remember { mutableStateOf(false) }
    var editorSaving by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentFolder = pathStack.lastOrNull()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val chosen = DocumentFile.fromTreeUri(context, uri)
                if (chosen != null) {
                    rootFolder = chosen
                    pathStack.clear()
                    pathStack.add(chosen)
                    prefs.edit().putString("root_uri", uri.toString()).apply()
                    selectedUris.clear()
                    query = ""
                    category = FileCategory.ALL
                    screen = AppScreen.HOME
                    refreshKey++
                }
            }.onFailure { Toast.makeText(context, "دسترسی به پوشه انتخاب‌شده ممکن نبود", Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(rootFolder, refreshKey) {
        scanning = true
        try {
            val summary = withContext(Dispatchers.IO) { scanFolderSummary(rootFolder) }
            allFiles = summary.files
            dashboardCounts = summary.categoryCounts
            dashboardFileCount = summary.fileCount
            dashboardFolderCount = summary.folderCount
            dashboardLargestFiles = summary.largestFiles
        } finally {
            scanning = false
        }
    }

    // Query the current folder off the main thread to keep navigation responsive.
    LaunchedEffect(currentFolder, refreshKey) {
        currentFolderFiles = emptyList()
        currentFolderFiles = withContext(Dispatchers.IO) {
            runCatching { currentFolder?.listFiles()?.toList().orEmpty().onEach(::cacheDocumentMetadata) }
                .getOrDefault(emptyList())
        }
    }

    val indexedFiles = remember(allFiles) { allFiles.associateBy { it.uri.toString() } }
    val favoriteKeys = favoriteUris.toList()
    val recentKeys = recentUris.toList()
    val selectedKeys = selectedUris.toSet()
    val favoriteFiles = remember(indexedFiles, favoriteKeys) { favoriteKeys.mapNotNull { indexedFiles[it] } }
    val recentFiles = remember(indexedFiles, recentKeys) {
        recentKeys.mapNotNull { indexedFiles[it] }.distinctBy { it.uri.toString() }.take(8)
    }
    val visibleFiles = remember(
        screen, favoriteFiles, allFiles, currentFolderFiles, query, showHidden, category, sortMode
    ) {
        val source = when (screen) {
            AppScreen.FAVORITES -> favoriteFiles
            AppScreen.FILES -> if (query.isNotBlank() || category != FileCategory.ALL) allFiles else currentFolderFiles
            else -> emptyList()
        }
        val filtered = if (screen == AppScreen.FILES) source.filter { file ->
            val nameOk = query.isBlank() || file.name.orEmpty().contains(query, ignoreCase = true)
            val hiddenOk = showHidden || !file.name.orEmpty().startsWith(".")
            nameOk && hiddenOk && matchesCategory(file, category)
        } else source
        when (sortMode) {
            SortMode.NAME -> filtered.sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name.orEmpty().lowercase(Locale.ROOT) }))
            SortMode.SIZE -> filtered.sortedByDescending { cachedLength(it) }
            SortMode.DATE -> filtered.sortedByDescending { cachedLastModified(it) }
            SortMode.TYPE -> filtered.sortedWith(compareBy<DocumentFile>(
                { !it.isDirectory },
                { it.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) },
                { it.name.orEmpty().lowercase(Locale.ROOT) }
            ))
        }
    }
    val selectedFiles = remember(visibleFiles, selectedKeys) {
        visibleFiles.filter { selectedKeys.contains(it.uri.toString()) }
    }

    fun persistFavorites() { prefs.edit().putStringSet("favorites", favoriteUris.toSet()).apply() }
    fun toggleFavorite(file: DocumentFile) {
        val key = file.uri.toString()
        if (favoriteUris.contains(key)) favoriteUris.remove(key) else favoriteUris.add(key)
        persistFavorites()
    }
    fun recordRecent(file: DocumentFile) {
        val key = file.uri.toString()
        recentUris.remove(key)
        recentUris.add(0, key)
        while (recentUris.size > 30) recentUris.removeAt(recentUris.lastIndex)
        prefs.edit().putString("recents", recentUris.joinToString("\n")).apply()
    }
    fun navigateToFolder(folder: DocumentFile) {
        pathStack.add(folder)
        screen = AppScreen.FILES
        category = FileCategory.ALL
        query = ""
        selectedUris.clear()
    }
    fun selectCategory(item: FileCategory) {
        category = item
        screen = AppScreen.FILES
        query = ""
        selectedUris.clear()
    }
    fun navigateToBreadcrumb(index: Int) {
        if (index in pathStack.indices) {
            while (pathStack.lastIndex > index) pathStack.removeAt(pathStack.lastIndex)
            selectedUris.clear()
            query = ""
            category = FileCategory.ALL
        }
    }
    fun openDocument(file: DocumentFile) {
        if (file.isDirectory) {
            navigateToFolder(file)
        } else {
            recordRecent(file)
            if (isEditableTextFile(file)) {
                scope.launch {
                    val fileSize = withContext(Dispatchers.IO) {
                        runCatching { file.length() }.getOrDefault(0L)
                    }
                    if (fileSize > MAX_TEXT_EDITOR_BYTES) {
                        Toast.makeText(context, "این فایل برای ویرایش داخلی بیش از یک مگابایت است", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    editorFile = file
                    editorText = ""
                    editorLoading = true
                    showEditorDialog = true
                    val loaded = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(file.uri)
                                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        }
                    }
                    editorLoading = false
                    val content = loaded.getOrNull()
                    if (loaded.isSuccess && content != null) {
                        editorText = content
                    } else {
                        showEditorDialog = false
                        editorFile = null
                        Toast.makeText(context, "خواندن فایل انجام نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(file.uri, file.type ?: mimeFromName(file.name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "باز کردن فایل با"))
                } catch (_: Exception) {
                    Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun saveEditedFile() {
        val target = editorFile ?: return
        val content = editorText
        scope.launch {
            editorSaving = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(target.uri, "wt")
                        ?: error("خروجی فایل در دسترس نیست")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
            }
            editorSaving = false
            if (result.isSuccess) {
                showEditorDialog = false
                editorFile = null
                Toast.makeText(context, "تغییرات ذخیره شد", Toast.LENGTH_SHORT).show()
                refreshKey++
            } else {
                Toast.makeText(context, "ذخیره‌سازی انجام نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun shareFiles(files: List<DocumentFile>) {
        if (files.isEmpty()) return
        try {
            val uris = ArrayList<Uri>().apply { addAll(files.map { it.uri }) }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = files.first().type ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    clipData = ClipData.newUri(context.contentResolver, "فایل", uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    clipData = ClipData.newUri(context.contentResolver, "فایل‌ها", uris.first()).also { clip ->
                        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
        } catch (_: Exception) { Toast.makeText(context, "اشتراک‌گذاری انجام نشد", Toast.LENGTH_SHORT).show() }
    }
    fun deleteSelected() {
        val targets = selectedFiles.toList()
        scope.launch {
            busy = true
            val removed = withContext(Dispatchers.IO) { targets.count { runCatching { it.delete() }.getOrDefault(false) } }
            busy = false
            selectedUris.clear()
            refreshKey++
            Toast.makeText(context, "$removed مورد حذف شد", Toast.LENGTH_SHORT).show()
        }
    }
    fun copyOrCutSelection(cut: Boolean) {
        clipboardFiles = selectedFiles.toList()
        clipboardIsCut = cut
        selectedUris.clear()
        Toast.makeText(context, if (cut) "برای انتقال، وارد مقصد شو و جای‌گذاری را بزن" else "برای کپی، وارد مقصد شو و جای‌گذاری را بزن", Toast.LENGTH_SHORT).show()
    }
    fun pasteClipboard() {
        val destination = currentFolder ?: return
        val pending = clipboardFiles
        val moving = clipboardIsCut
        if (pending.isEmpty()) return
        scope.launch {
            busy = true
            val copied = withContext(Dispatchers.IO) {
                var count = 0
                pending.forEach { source ->
                    val ok = runCatching { copyDocument(context, source, destination) }.getOrDefault(false)
                    if (ok) {
                        if (moving) runCatching { source.delete() }
                        count++
                    }
                }
                count
            }
            busy = false
            if (copied == pending.size) {
                clipboardFiles = emptyList()
                clipboardIsCut = false
            }
            refreshKey++
            Toast.makeText(context, "$copied مورد کپی یا منتقل شد", Toast.LENGTH_SHORT).show()
        }
    }

    fun compressSelectedFiles() {
        val sources = selectedFiles.toList()
        val destination = currentFolder
        if (sources.isEmpty() || destination == null) return
        scope.launch {
            busy = true
            val archive = withContext(Dispatchers.IO) {
                createZipArchive(context, sources, destination, "TIGER_${System.currentTimeMillis()}.zip")
            }
            busy = false
            if (archive != null) {
                selectedUris.clear()
                refreshKey++
                Toast.makeText(context, "فایل فشرده ساخته شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "فشرده‌سازی انجام نشد؛ فضای خالی و دسترسی پوشه را بررسی کن", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun extractSelectedZip() {
        val archive = selectedFiles.singleOrNull()
        val destination = currentFolder
        if (archive == null || destination == null) return
        if (!hasExtension(archive, setOf("zip"))) {
            Toast.makeText(context, "فعلاً فقط فایل‌های ZIP قابل استخراج هستند", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            busy = true
            val extracted = withContext(Dispatchers.IO) {
                extractZipArchive(context, archive, destination)
            }
            busy = false
            if (extracted != null) {
                selectedUris.clear()
                refreshKey++
                Toast.makeText(context, "${extracted.second} فایل استخراج شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "استخراج انجام نشد؛ فایل ZIP ممکن است خراب یا بیش از حد بزرگ باشد", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = screen == AppScreen.FILES && pathStack.size > 1) {
        pathStack.removeAt(pathStack.lastIndex)
        selectedUris.clear()
        query = ""
        category = FileCategory.ALL
    }

    Box(Modifier.fillMaxSize()) {
        TigerBackdrop(Modifier.matchParentSize())
        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIGER Files", fontWeight = FontWeight.ExtraBold)
                        Text(screen.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (screen == AppScreen.FILES && pathStack.size > 1) {
                        IconButton(onClick = { pathStack.removeAt(pathStack.lastIndex); selectedUris.clear(); query = ""; category = FileCategory.ALL }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                        }
                    } else {
                        Surface(Modifier.padding(start = 12.dp).size(42.dp), shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), shadowElevation = 0.dp) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FolderSpecial, contentDescription = "TIGER Files", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp)) }
                        }
                    }
                },
                actions = {
                    if (screen == AppScreen.FILES) {
                        IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.Default.List else Icons.Default.GridView, contentDescription = "تغییر نمایش")
                        }
                        IconButton(onClick = { sortMenuExpanded = true }) { Icon(Icons.Default.Sort, contentDescription = "مرتب‌سازی") }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            SortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text("مرتب‌سازی بر اساس " + mode.label) },
                                    onClick = { sortMode = mode; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                shadowElevation = 0.dp
            ) {
                NavigationBar(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
                    AppScreen.values().forEach { item ->
                        val icon = when (item) {
                            AppScreen.HOME -> Icons.Default.Home
                            AppScreen.FILES -> Icons.Default.Folder
                            AppScreen.FAVORITES -> Icons.Default.Favorite
                            AppScreen.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item; selectedUris.clear(); query = "" },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (screen == AppScreen.FILES && currentFolder != null) {
                FloatingActionButton(onClick = {
                    createAsFile = false
                    nameInput = ""
                    showCreateDialog = true
                }) { Icon(Icons.Default.CreateNewFolder, contentDescription = "ایجاد فایل یا پوشه") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (screen) {
            AppScreen.HOME -> HomeDashboard(
                Modifier.padding(padding), rootFolder, remember { readStorageInfo() }, recentFiles,
                dashboardLargestFiles,
                dashboardFileCount, dashboardFolderCount, dashboardCounts, scanning,
                onChooseFolder = { folderPicker.launch(null) },
                onOpenFiles = { screen = AppScreen.FILES; category = FileCategory.ALL },
                onCategory = { selectCategory(it) },
                onOpenFile = { openDocument(it) }
            )
            AppScreen.FILES -> BrowserScreen(
                Modifier.padding(padding), rootFolder, currentFolder, pathStack.toList(), visibleFiles,
                query, { query = it; selectedUris.clear() }, category, { category = it; selectedUris.clear() }, gridMode,
                selectedUris.toSet(),
                visibleFiles.isNotEmpty() && visibleFiles.all { selectedUris.contains(it.uri.toString()) },
                selectedUris.size, clipboardFiles.size, clipboardIsCut, busy || scanning,
                onChooseFolder = { folderPicker.launch(null) },
                onOpen = { openDocument(it) },
                onLongSelect = { if (!selectedUris.contains(it.uri.toString())) selectedUris.add(it.uri.toString()) },
                onToggleSelect = { val key = it.uri.toString(); if (selectedUris.contains(key)) selectedUris.remove(key) else selectedUris.add(key) },
                onCopy = { copyOrCutSelection(false) },
                onCut = { copyOrCutSelection(true) },
                onFavorite = { selectedFiles.forEach { file -> toggleFavorite(file) }; selectedUris.clear() },
                onShare = { shareFiles(selectedFiles) },
                onDelete = { showDeleteDialog = true },
                onRename = { nameInput = selectedFiles.singleOrNull()?.name.orEmpty(); showRenameDialog = true },
                onPaste = { pasteClipboard() },
                onRefresh = { refreshKey++ },
                onClearSelection = { selectedUris.clear() },
                onZip = { compressSelectedFiles() },
                onExtract = { extractSelectedZip() },
                onDetails = { detailsTarget = it; showDetailsDialog = true },
                isFavorite = { favoriteUris.contains(it.uri.toString()) },
                onToggleFavorite = { toggleFavorite(it) },
                onToggleSelectAll = {
                    val keys = visibleFiles.map { it.uri.toString() }
                    if (keys.isNotEmpty() && keys.all { selectedUris.contains(it) }) selectedUris.clear()
                    else {
                        selectedUris.clear()
                        selectedUris.addAll(keys)
                    }
                },
                onNavigateTo = { navigateToBreadcrumb(it) }
            )
            AppScreen.FAVORITES -> FavoritesScreen(
                Modifier.padding(padding), favoriteFiles, { openDocument(it) }, { toggleFavorite(it) },
                { folderPicker.launch(null) }
            )
            AppScreen.SETTINGS -> SettingsScreen(
                Modifier.padding(padding), rootFolder, showHidden,
                onShowHidden = { showHidden = it; prefs.edit().putBoolean("show_hidden", it).apply() },
                onChooseFolder = { folderPicker.launch(null) },
                onRescan = { refreshKey++ },
                onClearRecents = { recentUris.clear(); prefs.edit().remove("recents").apply() }
            )
        }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; nameInput = ""; createAsFile = false },
            title = { Text(if (createAsFile) "ساخت فایل جدید" else "ساخت پوشه جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !createAsFile,
                            onClick = { createAsFile = false },
                            label = { Text("پوشه") }
                        )
                        FilterChip(
                            selected = createAsFile,
                            onClick = { createAsFile = true },
                            label = { Text("فایل") }
                        )
                    }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        label = { Text(if (createAsFile) "نام فایل" else "نام پوشه") },
                        placeholder = { Text(if (createAsFile) "مثلاً notes.txt" else "نام پوشه") }
                    )
                    if (createAsFile) {
                        Text(
                            "اگر پسوند وارد نکنی، فایل با پسوند ‎.txt ساخته می‌شود.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rawName = nameInput.trim()
                    val name = if (createAsFile && rawName.isNotEmpty() && !rawName.substringAfterLast('.', "").isNotEmpty()) "$rawName.txt" else rawName
                    val validName = rawName.isNotEmpty() && rawName != "." && rawName != ".." &&
                        rawName.none { it == '/' || it == 92.toChar() }
                    val destination = currentFolder
                    if (!validName || destination == null) {
                        Toast.makeText(context, "نام واردشده معتبر نیست", Toast.LENGTH_SHORT).show()
                    } else if (destination.findFile(name) != null) {
                        Toast.makeText(context, "این نام از قبل وجود دارد", Toast.LENGTH_SHORT).show()
                    } else {
                        val created = runCatching {
                            if (createAsFile) destination.createFile(mimeFromName(name), name)
                            else destination.createDirectory(name)
                        }.getOrNull()
                        if (created == null) {
                            Toast.makeText(context, "ایجاد مورد جدید انجام نشد", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, if (createAsFile) "فایل ساخته شد" else "پوشه ساخته شد", Toast.LENGTH_SHORT).show()
                            refreshKey++
                        }
                    }
                    showCreateDialog = false
                    nameInput = ""
                    createAsFile = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; nameInput = ""; createAsFile = false }) { Text("انصراف") }
            }
        )
    }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("تغییر نام") },
            text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("نام جدید") }) },
            confirmButton = {
                TextButton(onClick = {
                    val target = selectedFiles.singleOrNull()
                    val name = nameInput.trim()
                    if (target != null && name.isNotEmpty()) {
                        val ok = runCatching { target.renameTo(name) }.getOrDefault(false)
                        Toast.makeText(context, if (ok) "نام فایل تغییر کرد" else "تغییر نام انجام نشد", Toast.LENGTH_SHORT).show()
                        selectedUris.clear()
                        refreshKey++
                    }
                    showRenameDialog = false
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("انصراف") } }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("حذف موارد انتخاب‌شده؟") },
            text = { Text("تعداد " + selectedFiles.size + " مورد برای حذف انتخاب شده است. این کار قابل بازگشت نیست.") },
            confirmButton = { TextButton(onClick = { showDeleteDialog = false; deleteSelected() }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("انصراف") } }
        )
    }
    if (showDetailsDialog && detailsTarget != null) {
        val file = detailsTarget!!
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false; detailsTarget = null },
            title = { Text("جزئیات فایل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine("نام", file.name ?: "بدون نام")
                    DetailLine("نوع", friendlyFileType(file))
                    DetailLine("حجم", if (file.isDirectory) "—" else formatBytes(cachedLength(file)))
                    DetailLine("آخرین تغییر", if (cachedLastModified(file) > 0L) DateFormat.getDateTimeInstance().format(Date(cachedLastModified(file))) else "نامشخص")
                    DetailLine("شناسه", file.uri.lastPathSegment ?: "—")
                }
            },
            confirmButton = { TextButton(onClick = { showDetailsDialog = false; detailsTarget = null }) { Text("بستن") } }
        )
    }
    if (showEditorDialog && editorFile != null) {
        FileEditorDialog(
            fileName = editorFile?.name ?: "ویرایش فایل",
            text = editorText,
            loading = editorLoading,
            saving = editorSaving,
            onTextChange = { if (it.length <= MAX_TEXT_EDITOR_CHARS) editorText = it },
            onDismiss = {
                showEditorDialog = false
                editorFile = null
            },
            onSave = { saveEditedFile() }
        )
    }
}

@Composable
private fun TigerBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

@Composable
private fun HomeDashboard(
    modifier: Modifier,
    rootFolder: DocumentFile?,
    storageInfo: StorageInfo,
    recentFiles: List<DocumentFile>,
    largestFiles: List<DocumentFile>,
    fileCount: Int,
    folderCount: Int,
    categoryCounts: Map<FileCategory, Int>,
    scanning: Boolean,
    onChooseFolder: () -> Unit,
    onOpenFiles: () -> Unit,
    onCategory: (FileCategory) -> Unit,
    onOpenFile: (DocumentFile) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            DashboardHero(
                rootFolder = rootFolder,
                fileCount = fileCount,
                folderCount = folderCount,
                scanning = scanning,
                onAction = if (rootFolder == null) onChooseFolder else onOpenFiles
            )
        }
        item { StorageOverview(storageInfo) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionHeading("دسترسی سریع", "دسته‌بندی فایل‌ها") }
                Text("۶ دسته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryTile(FileCategory.IMAGES, categoryCounts[FileCategory.IMAGES] ?: 0, Modifier.weight(1f), onCategory)
                    CategoryTile(FileCategory.VIDEOS, categoryCounts[FileCategory.VIDEOS] ?: 0, Modifier.weight(1f), onCategory)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryTile(FileCategory.AUDIO, categoryCounts[FileCategory.AUDIO] ?: 0, Modifier.weight(1f), onCategory)
                    CategoryTile(FileCategory.DOCUMENTS, categoryCounts[FileCategory.DOCUMENTS] ?: 0, Modifier.weight(1f), onCategory)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryTile(FileCategory.APK, categoryCounts[FileCategory.APK] ?: 0, Modifier.weight(1f), onCategory)
                    CategoryTile(FileCategory.ARCHIVES, categoryCounts[FileCategory.ARCHIVES] ?: 0, Modifier.weight(1f), onCategory)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionHeading("اخیراً بازشده", "فایل‌هایی که آخرین بار استفاده کردی") }
                if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (recentFiles.isEmpty()) {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text("هنوز فایلی باز نکردی", fontWeight = FontWeight.SemiBold)
                            Text("فایل‌های اخیرت بعد از اولین استفاده اینجا می‌آیند", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            items(recentFiles, key = { "recent_" + it.uri.toString() }) { file ->
                FileRowCard(file, false, false, false, { onOpenFile(file) }, {}, {})
            }
        }
        if (largestFiles.isNotEmpty()) {
            item {
                SectionHeading("فایل‌های حجیم", "پنج فایل با بیشترین حجم در پوشه انتخابی")
            }
            items(largestFiles, key = { "largest_" + it.uri.toString() }) { file ->
                FileRowCard(file, false, false, false, { onOpenFile(file) }, {}, {})
            }
        }
        item {
            Text(
                "مدیریت فایل‌ها  •  ساده، مرتب و سریع",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardHero(
    rootFolder: DocumentFile?,
    fileCount: Int,
    folderCount: Int,
    scanning: Boolean,
    onAction: () -> Unit
) {
    val gold = Color(0xFFFFB52E)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF1D3045), Color(0xFF55390F))))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مدیریت فایل‌ها", style = MaterialTheme.typography.labelSmall, color = gold, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(9.dp))
                        Text(
                            "همه‌چیز،\nسر جای خودش.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (rootFolder == null) "برای شروع، پوشه‌ای از حافظه انتخاب کن" else "مدیریت فایل‌ها در یک فضای منظم و سریع",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.size(82.dp),
                        shape = RoundedCornerShape(25.dp),
                        color = Color.White.copy(alpha = 0.09f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderCopy, contentDescription = null, tint = gold, modifier = Modifier.size(43.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("فایل‌ها", fileCount.toString(), Modifier.weight(1f))
                    HeroMetric("پوشه‌ها", folderCount.toString(), Modifier.weight(1f))
                    HeroMetric("وضعیت", if (scanning) "در حال اسکن" else if (rootFolder == null) "آماده" else "فعال", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF151B25)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(if (rootFolder == null) Icons.Default.FolderOpen else Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (rootFolder == null) "انتخاب پوشه اصلی" else "رفتن به فایل‌ها", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                if (rootFolder != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "پوشه فعال: " + (rootFolder.name ?: "حافظه انتخاب‌شده"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.64f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.68f))
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StorageOverview(info: StorageInfo) {
    val fraction = info.fraction.coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(25.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("حافظه داخلی", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatBytes(info.used), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("از " + formatBytes(info.total) + " استفاده شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                    CircularProgressIndicator(
                        progress = fraction,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(toPersianDigits(String.format(Locale.US, "%.0f", fraction * 100f)) + "٪", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Text("مصرف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StorageLegend("اشغال‌شده", formatBytes(info.used), MaterialTheme.colorScheme.primary)
                StorageLegend("فضای آزاد", formatBytes(info.free), MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StorageLegend(label: String, value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun categoryTint(category: FileCategory): Color = when (category) {
    FileCategory.IMAGES -> Color(0xFFE66A91)
    FileCategory.VIDEOS -> Color(0xFF9275E8)
    FileCategory.AUDIO -> Color(0xFF4FAF83)
    FileCategory.DOCUMENTS -> Color(0xFF4F94D5)
    FileCategory.APK -> Color(0xFFE4A33A)
    FileCategory.ARCHIVES -> Color(0xFF6E8EAB)
    else -> Color(0xFFFFB52E)
}

@Composable
private fun CategoryTile(category: FileCategory, count: Int, modifier: Modifier, onClick: (FileCategory) -> Unit) {
    val tint = categoryTint(category)
    Card(
        modifier = modifier,
        onClick = { onClick(category) },
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = tint.copy(alpha = 0.16f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp)) }
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(11.dp))
            Text(category.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("$count مورد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(
    modifier: Modifier,
    rootFolder: DocumentFile?,
    currentFolder: DocumentFile?,
    pathStack: List<DocumentFile>,
    files: List<DocumentFile>,
    query: String,
    onQuery: (String) -> Unit,
    category: FileCategory,
    onCategory: (FileCategory) -> Unit,
    gridMode: Boolean,
    selectedUris: Set<String>,
    allSelected: Boolean,
    selectedCount: Int,
    clipboardCount: Int,
    clipboardIsCut: Boolean,
    busy: Boolean,
    onChooseFolder: () -> Unit,
    onOpen: (DocumentFile) -> Unit,
    onLongSelect: (DocumentFile) -> Unit,
    onToggleSelect: (DocumentFile) -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onPaste: () -> Unit,
    onRefresh: () -> Unit,
    onClearSelection: () -> Unit,
    onZip: () -> Unit,
    onExtract: () -> Unit,
    onDetails: (DocumentFile) -> Unit,
    isFavorite: (DocumentFile) -> Boolean,
    onToggleFavorite: (DocumentFile) -> Unit,
    onToggleSelectAll: () -> Unit,
    onNavigateTo: (Int) -> Unit
) {
    if (rootFolder == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("هنوز پوشه‌ای انتخاب نشده", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onChooseFolder) { Text("انتخاب پوشه") }
            }
        }
        return
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        if (currentFolder != null) {
            Text(
                currentFolder.name ?: "حافظه",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
            singleLine = true,
            placeholder = { Text("جستجو در پوشه‌های انتخاب‌شده") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = "پاک کردن") } },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f), unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FileCategory.values().forEach { item ->
                FilterChip(selected = category == item, onClick = { onCategory(item) }, label = { Text(item.label) })
            }
        }
        if (category == FileCategory.ALL && query.isBlank()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pathStack.forEachIndexed { index, folder ->
                    TextButton(
                        onClick = { onNavigateTo(index) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (index == pathStack.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            folder.name ?: "حافظه",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (index == pathStack.lastIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (index != pathStack.lastIndex) Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selectedCount > 0) {
            SelectionActions(selectedCount, onCopy, onCut, onFavorite, onShare, onDelete, onRename, onClearSelection, onZip, onExtract)
        } else if (clipboardCount > 0 && category == FileCategory.ALL && query.isBlank()) {
            FilledTonalButton(onClick = onPaste, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentPaste, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (clipboardIsCut) "انتقال $clipboardCount مورد به این پوشه" else "جای‌گذاری $clipboardCount مورد")
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(files.size.toString() + " مورد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (busy) Text("در حال پردازش…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (files.isNotEmpty()) {
                TextButton(onClick = onToggleSelectAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(if (allSelected) "لغو انتخاب همه" else "انتخاب همه")
                }
            }
            TextButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("به‌روزرسانی") }
        }
        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(if (query.isNotBlank()) "نتیجه‌ای پیدا نشد" else "در این بخش فایلی وجود ندارد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                gridItems(files, key = { it.uri.toString() }) { file ->
                    FileGridCard(file, selectedUris.contains(file.uri.toString()), isFavorite(file),
                        onClick = { if (selectedCount > 0) onToggleSelect(file) else onOpen(file) },
                        onLongClick = { onLongSelect(file) },
                        onToggleFavorite = { onToggleFavorite(file) },
                        onDetails = { onDetails(file) })
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.uri.toString() }) { file ->
                    FileRowCard(file, selectedUris.contains(file.uri.toString()), isFavorite(file), false,
                        onClick = { if (selectedCount > 0) onToggleSelect(file) else onOpen(file) },
                        onLongClick = { onLongSelect(file) },
                        onToggleFavorite = { onToggleFavorite(file) },
                        onDetails = { onDetails(file) })
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    count: Int,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onZip: () -> Unit,
    onExtract: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$count انتخاب شده", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "لغو انتخاب") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionChip("کپی", Icons.Default.ContentCopy, onCopy)
            ActionChip("برش", Icons.Default.ContentCut, onCut)
            ActionChip("علاقه‌مندی", Icons.Default.FavoriteBorder, onFavorite)
            ActionChip("اشتراک", Icons.Default.Share, onShare)
            if (count == 1) ActionChip("تغییر نام", Icons.Default.Edit, onRename)
            ActionChip("فشرده‌سازی ZIP", Icons.Default.Archive, onZip)
            if (count == 1) ActionChip("استخراج ZIP", Icons.Default.Archive, onExtract)
            ActionChip("حذف", Icons.Default.Delete, onDelete)
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRowCard(
    file: DocumentFile,
    selected: Boolean,
    favorite: Boolean,
    grid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit = {}
) {
    val folder = file.isDirectory
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.58f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            FileIconBox(fileIcon(file), folder)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name ?: "بدون نام", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (folder) "پوشه" else formatBytes(cachedLength(file)) + "  •  " + friendlyFileType(file), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = "انتخاب شده", tint = MaterialTheme.colorScheme.primary)
            else {
                IconButton(onClick = onToggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "علاقه‌مندی", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDetails) { Icon(Icons.Default.Info, contentDescription = "جزئیات") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    file: DocumentFile,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit
) {
    val folder = file.isDirectory
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(19.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.17f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIconBox(fileIcon(file), folder, 48.dp)
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, contentDescription = "انتخاب شده", tint = MaterialTheme.colorScheme.primary)
                else {
                    IconButton(onClick = onToggleFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "علاقه‌مندی", tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onDetails) { Icon(Icons.Default.MoreVert, contentDescription = "جزئیات") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(file.name ?: "بدون نام", fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (folder) "پوشه" else formatBytes(cachedLength(file)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileIconBox(icon: ImageVector, folder: Boolean, size: Dp = 44.dp) {
    Surface(Modifier.size(size), shape = RoundedCornerShape(15.dp), color = if (folder) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = if (folder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.52f)) }
    }
}

@Composable
private fun FavoritesScreen(
    modifier: Modifier,
    files: List<DocumentFile>,
    onOpen: (DocumentFile) -> Unit,
    onRemove: (DocumentFile) -> Unit,
    onChooseFolder: () -> Unit
) {
    if (files.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("هنوز فایلی به علاقه‌مندی‌ها اضافه نشده", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("در بخش فایل‌ها روی علامت قلب بزن", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onChooseFolder) { Text("انتخاب پوشه") }
            }
        }
    } else {
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionHeading("فایل‌های منتخب", files.size.toString() + " مورد ذخیره‌شده") }
            items(files, key = { it.uri.toString() }) { file ->
                FileRowCard(file, false, true, false, { onOpen(file) }, {}, { onRemove(file) })
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    rootFolder: DocumentFile?,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit,
    onClearRecents: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("تنظیمات TIGER Files", "مدیریت دسترسی‌ها و رفتار برنامه")
        Card(shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))) {
            Column(Modifier.padding(16.dp)) {
                Text("پوشه اصلی", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(rootFolder?.name ?: "هیچ پوشه‌ای انتخاب نشده", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text("تغییر پوشه اصلی") }
            }
        }
        Card(shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("نمایش فایل‌های مخفی", fontWeight = FontWeight.SemiBold)
                    Text("فایل‌هایی که نامشان با نقطه شروع می‌شود", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showHidden, onCheckedChange = onShowHidden)
            }
        }
        Card(shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ابزارها", fontWeight = FontWeight.Bold)
                TextButton(onClick = onRescan) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("اسکن دوباره فایل‌ها") }
                TextButton(onClick = onClearRecents) { Icon(Icons.Default.Delete, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("پاک کردن تاریخچه اخیر") }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("نسخه ۰٫۶٫۱", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("ساخته شده توسط mobin", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

private fun readStorageInfo(): StorageInfo = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    StorageInfo(stat.totalBytes, stat.availableBytes.coerceAtMost(stat.totalBytes))
}.getOrDefault(StorageInfo(0L, 0L))

private data class FileScanSummary(
    val files: List<DocumentFile>,
    val categoryCounts: Map<FileCategory, Int>,
    val fileCount: Int,
    val folderCount: Int,
    val largestFiles: List<DocumentFile>
)

private fun scanFolderSummary(root: DocumentFile?): FileScanSummary {
    val files = root?.let { scanFolderTree(it, maxDepth = 6, maxItems = 1500) }.orEmpty()
    files.forEach(::cacheDocumentMetadata)
    val counts = FileCategory.values().associateWith { 0 }.toMutableMap()
    var fileCount = 0
    var folderCount = 0
    counts[FileCategory.ALL] = files.size
    files.forEach { file ->
        if (file.isDirectory) {
            folderCount++
            counts[FileCategory.FOLDERS] = (counts[FileCategory.FOLDERS] ?: 0) + 1
        } else {
            fileCount++
            val category = detectFileCategory(file.name.orEmpty(), file.type.orEmpty())
            if (category != null) counts[category] = (counts[category] ?: 0) + 1
        }
    }
    val largestFiles = files.asSequence()
        .filterNot { it.isDirectory }
        .sortedByDescending { file -> cachedLength(file) }
        .take(5)
        .toList()
    return FileScanSummary(files, counts, fileCount, folderCount, largestFiles)
}

private fun scanFolderTree(root: DocumentFile, maxDepth: Int, maxItems: Int): List<DocumentFile> {
    val result = ArrayList<DocumentFile>()
    fun walk(folder: DocumentFile, depth: Int) {
        if (depth > maxDepth || result.size >= maxItems) return
        val children = runCatching { folder.listFiles().toList() }.getOrDefault(emptyList())
        children.forEach { child ->
            if (result.size >= maxItems) return
            result.add(child)
            if (child.isDirectory) walk(child, depth + 1)
        }
    }
    walk(root, 0)
    return result
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a")
private val DOCUMENT_EXTENSIONS = setOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "csv")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2")

private fun matchesCategory(file: DocumentFile, category: FileCategory): Boolean = when (category) {
    FileCategory.ALL -> true
    FileCategory.FOLDERS -> file.isDirectory
    else -> !file.isDirectory && detectFileCategory(file.name.orEmpty(), file.type.orEmpty()) == category
}

private fun detectFileCategory(name: String, mimeType: String): FileCategory? {
    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS -> FileCategory.IMAGES
        mimeType.startsWith("video/") || extension in VIDEO_EXTENSIONS -> FileCategory.VIDEOS
        mimeType.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> FileCategory.AUDIO
        mimeType.startsWith("text/") || mimeType == "application/pdf" || mimeType.contains("word") || mimeType.contains("excel") ||
            extension in DOCUMENT_EXTENSIONS -> FileCategory.DOCUMENTS
        mimeType == "application/vnd.android.package-archive" || extension in setOf("apk", "xapk", "apks") -> FileCategory.APK
        extension in ARCHIVE_EXTENSIONS -> FileCategory.ARCHIVES
        else -> null
    }
}

private fun friendlyFileType(file: DocumentFile): String {
    if (file.isDirectory) return "پوشه"
    return when (detectFileCategory(file.name.orEmpty(), file.type.orEmpty())) {
        FileCategory.IMAGES -> "تصویر"
        FileCategory.VIDEOS -> "ویدئو"
        FileCategory.AUDIO -> "فایل صوتی"
        FileCategory.DOCUMENTS -> "سند"
        FileCategory.APK -> "برنامه"
        FileCategory.ARCHIVES -> "فایل فشرده"
        else -> when (file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html", "htm" -> "صفحه وب"
            "css" -> "شیوه‌نامه"
            "js" -> "فایل جاوااسکریپت"
            "json" -> "داده جیسون"
            "xml" -> "فایل ایکس‌ام‌ال"
            else -> "فایل"
        }
    }
}

private fun hasExtension(file: DocumentFile, extensions: Set<String>): Boolean =
    file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

private fun fileIcon(file: DocumentFile): ImageVector = when {
    file.isDirectory -> Icons.Default.Folder
    matchesCategory(file, FileCategory.IMAGES) -> Icons.Default.Image
    matchesCategory(file, FileCategory.VIDEOS) -> Icons.Default.Movie
    matchesCategory(file, FileCategory.AUDIO) -> Icons.Default.MusicNote
    matchesCategory(file, FileCategory.DOCUMENTS) -> Icons.Default.Description
    matchesCategory(file, FileCategory.APK) -> Icons.Default.Android
    matchesCategory(file, FileCategory.ARCHIVES) -> Icons.Default.Archive
    else -> Icons.Default.InsertDriveFile
}

private fun mimeFromName(name: String?): String = when (name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "text/javascript"
    "json" -> "application/json"
    "md" -> "text/markdown"
    "xml" -> "application/xml"
    "csv" -> "text/csv"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "apk" -> "application/vnd.android.package-archive"
    "zip" -> "application/zip"
    else -> "*/*"
}

private fun uniqueName(folder: DocumentFile, desired: String): String {
    if (folder.findFile(desired) == null) return desired
    val dot = desired.lastIndexOf('.')
    val base = if (dot > 0) desired.substring(0, dot) else desired
    val ext = if (dot > 0) desired.substring(dot) else ""
    var index = 1
    while (folder.findFile("$base (کپی $index)$ext") != null) index++
    return "$base (کپی $index)$ext"
}

private fun isSameOrInside(candidate: DocumentFile, ancestor: DocumentFile): Boolean {
    if (candidate.uri == ancestor.uri) return true

    val candidateId = runCatching { DocumentsContract.getDocumentId(candidate.uri) }.getOrNull()
    val ancestorId = runCatching { DocumentsContract.getDocumentId(ancestor.uri) }.getOrNull()
    if (candidateId != null && ancestorId != null &&
        (candidateId == ancestorId || candidateId.startsWith("$ancestorId/"))
    ) return true

    var parent = runCatching { candidate.parentFile }.getOrNull()
    var depth = 0
    while (depth++ < 64) {
        val current = parent ?: break
        if (current.uri == ancestor.uri) return true
        parent = runCatching { current.parentFile }.getOrNull()
    }
    return false
}

private fun copyDocument(context: Context, source: DocumentFile, destination: DocumentFile): Boolean {
    if (source.isDirectory && isSameOrInside(destination, source)) return false
    if (source.uri == destination.uri) return false
    val originalName = source.name ?: if (source.isDirectory) "پوشه" else "فایل"
    val targetName = uniqueName(destination, originalName)
    if (source.isDirectory) {
        val targetFolder = destination.createDirectory(targetName) ?: return false
        val children = runCatching { source.listFiles().toList() }.getOrElse {
            targetFolder.delete()
            return false
        }
        var success = true
        children.forEach { child -> if (!copyDocument(context, child, targetFolder)) success = false }
        if (!success) targetFolder.delete()
        return success
    }
    val target = destination.createFile(source.type ?: mimeFromName(source.name), targetName) ?: return false
    return try {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output -> input.copyTo(output); true } ?: false
        } ?: false
    } catch (_: Exception) {
        target.delete()
        false
    }
}

private fun toPersianDigits(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(if (character in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[character - '0'] else character)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "۰ بایت"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    return when {
        bytes >= tb -> toPersianDigits(String.format(Locale.US, "%.1f", bytes / tb)) + " ترابایت"
        bytes >= gb -> toPersianDigits(String.format(Locale.US, "%.1f", bytes / gb)) + " گیگابایت"
        bytes >= mb -> toPersianDigits(String.format(Locale.US, "%.1f", bytes / mb)) + " مگابایت"
        bytes >= kb -> toPersianDigits(String.format(Locale.US, "%.0f", bytes / kb)) + " کیلوبایت"
        else -> toPersianDigits(bytes.toString()) + " بایت"
    }
}

private data class CachedDocumentMetadata(val size: Long, val lastModified: Long)

private val documentMetadataCache = ConcurrentHashMap<String, CachedDocumentMetadata>()

private fun cacheDocumentMetadata(file: DocumentFile) {
    val key = file.uri.toString()
    documentMetadataCache[key] = CachedDocumentMetadata(
        size = if (file.isDirectory) 0L else runCatching { file.length() }.getOrDefault(0L),
        lastModified = runCatching { file.lastModified() }.getOrDefault(0L)
    )
}

private fun cachedLength(file: DocumentFile): Long =
    documentMetadataCache[file.uri.toString()]?.size ?: 0L

private fun cachedLastModified(file: DocumentFile): Long =
    documentMetadataCache[file.uri.toString()]?.lastModified ?: 0L
