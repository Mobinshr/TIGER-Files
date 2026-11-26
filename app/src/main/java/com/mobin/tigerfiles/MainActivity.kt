package com.mobin.tigerfiles

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mobin.tigerfiles.ui.theme.TigerFilesTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TigerFilesTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TigerFilesHome()
                }
            }
        }
    }
}

private enum class FileCategory(val label: String) {
    ALL("همه"),
    FOLDERS("پوشه‌ها"),
    IMAGES("تصاویر"),
    VIDEOS("ویدئوها"),
    AUDIO("صوتی"),
    DOCUMENTS("اسناد")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TigerFilesHome() {
    val context = LocalContext.current
    val folderStack = remember { mutableStateListOf<DocumentFile>() }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FileCategory.ALL) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val pickedFolder = DocumentFile.fromTreeUri(context, uri)
                if (pickedFolder != null) {
                    folderStack.clear()
                    folderStack.add(pickedFolder)
                    query = ""
                    category = FileCategory.ALL
                    refreshKey++
                } else {
                    Toast.makeText(context, "خواندن پوشه انتخاب‌شده ممکن نبود", Toast.LENGTH_SHORT).show()
                }
            } catch (error: SecurityException) {
                Toast.makeText(context, "دسترسی به پوشه داده نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentFolder = folderStack.lastOrNull()
    val folderFiles = remember(currentFolder, refreshKey) {
        currentFolder?.listFiles()
            ?.toList()
            ?.sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase(Locale.ROOT).orEmpty() }))
            .orEmpty()
    }

    val visibleFiles = remember(folderFiles, query, category) {
        folderFiles.filter { file ->
            val nameMatches = file.name.orEmpty().contains(query, ignoreCase = true)
            val categoryMatches = when (category) {
                FileCategory.ALL -> true
                FileCategory.FOLDERS -> file.isDirectory
                FileCategory.IMAGES -> isImage(file)
                FileCategory.VIDEOS -> isVideo(file)
                FileCategory.AUDIO -> isAudio(file)
                FileCategory.DOCUMENTS -> isDocument(file)
            }
            nameMatches && categoryMatches
        }
    }

    BackHandler(enabled = folderStack.size > 1) {
        folderStack.removeAt(folderStack.lastIndex)
        query = ""
        category = FileCategory.ALL
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIGER Files", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "مدیریت ساده و سریع فایل‌ها",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 12.dp).size(40.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("T", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Black)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "انتخاب پوشه")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            StorageSummaryCard()
            Spacer(Modifier.height(16.dp))

            if (currentFolder == null) {
                WelcomeCard(onChooseFolder = { folderPicker.launch(null) })
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentFolder.name ?: "پوشه انتخاب‌شده",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (folderStack.size > 1) "زیرپوشه" else "پوشه اصلی انتخاب‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (folderStack.size > 1) {
                        IconButton(onClick = {
                            folderStack.removeAt(folderStack.lastIndex)
                            query = ""
                            category = FileCategory.ALL
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                        }
                    }
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("جستجو در این پوشه") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileCategory.values().forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    visibleFiles.size.toString() + " مورد",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (visibleFiles.isEmpty()) {
                    EmptyFolderState(
                        modifier = Modifier.weight(1f),
                        message = if (query.isNotBlank() || category != FileCategory.ALL) {
                            "موردی با این فیلتر پیدا نشد"
                        } else {
                            "این پوشه خالی است"
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleFiles, key = { it.uri.toString() }) { file ->
                            FileRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        folderStack.add(file)
                                        query = ""
                                        category = FileCategory.ALL
                                    } else {
                                        openFile(context, file)
                                    }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard() {
    val storage = remember {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.totalBytes to stat.availableBytes
        }.getOrDefault(0L to 0L)
    }
    val total = storage.first
    val free = storage.second.coerceAtMost(total)
    val used = (total - free).coerceAtLeast(0L)
    val progress = if (total > 0L) used.toFloat() / total.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("حافظه دستگاه", fontWeight = FontWeight.Bold)
                    Text("نمای کلی فضای ذخیره‌سازی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatBytes(used), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("استفاده‌شده: " + formatBytes(used), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("آزاد: " + formatBytes(free), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomeCard(onChooseFolder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("فایل‌هایت را یک‌جا مدیریت کن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "برای شروع، یک پوشه از حافظه دستگاه انتخاب کن. دسترسی فقط برای همان پوشه ذخیره می‌شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("انتخاب پوشه")
            }
        }
    }
}

@Composable
private fun EmptyFolderState(modifier: Modifier = Modifier, message: String) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(file: DocumentFile, onClick: () -> Unit) {
    val isFolder = file.isDirectory
    val icon = when {
        isFolder -> Icons.Default.Folder
        isImage(file) -> Icons.Default.Image
        isVideo(file) -> Icons.Default.Movie
        isAudio(file) -> Icons.Default.MusicNote
        isDocument(file) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
    val subtitle = if (isFolder) "پوشه" else formatBytes(file.length())

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(13.dp),
                color = if (isFolder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isFolder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name ?: "بدون نام",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isFolder) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun openFile(context: android.content.Context, file: DocumentFile) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, file.type ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "باز کردن فایل"))
    } catch (error: Exception) {
        Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
    }
}

private fun isImage(file: DocumentFile): Boolean =
    file.type?.startsWith("image/") == true || file.name.orEmpty().hasImageExtension()

private fun isVideo(file: DocumentFile): Boolean =
    file.type?.startsWith("video/") == true || file.name.orEmpty().hasVideoExtension()

private fun isAudio(file: DocumentFile): Boolean =
    file.type?.startsWith("audio/") == true || file.name.orEmpty().hasAudioExtension()

private fun isDocument(file: DocumentFile): Boolean {
    val mime = file.type.orEmpty()
    return mime.startsWith("text/") ||
        mime == "application/pdf" ||
        mime.contains("word") ||
        mime.contains("excel") ||
        mime.contains("powerpoint") ||
        file.name.orEmpty().hasDocumentExtension()
}

private fun String.hasImageExtension(): Boolean =
    lowercase(Locale.ROOT).substringAfterLast('.', "") in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")

private fun String.hasVideoExtension(): Boolean =
    lowercase(Locale.ROOT).substringAfterLast('.', "") in setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

private fun String.hasAudioExtension(): Boolean =
    lowercase(Locale.ROOT).substringAfterLast('.', "") in setOf("mp3", "wav", "aac", "flac", "ogg", "m4a")

private fun String.hasDocumentExtension(): Boolean =
    lowercase(Locale.ROOT).substringAfterLast('.', "") in setOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "csv")

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "۰ بایت"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
        else -> bytes.toString() + " B"
    }
}
