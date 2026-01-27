package com.mobile.scrcpy.android.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import androidx.core.content.FileProvider
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.rememberText
import com.mobile.scrcpy.android.ui.components.AppDivider
import com.mobile.scrcpy.android.ui.components.DialogHeader
import com.mobile.scrcpy.android.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogManagementScreen(
    onDismiss: () -> Unit
) {
    LocalContext.current
    val scope = rememberCoroutineScope()
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var totalSize by remember { mutableLongStateOf(0L) }
    var currentLogSize by remember { mutableLongStateOf(0L) }
    var fileCount by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showLogViewer by remember { mutableStateOf(false) }
    
    // 双语文本
    val txtTitle = rememberText(BilingualTexts.LOG_MANAGEMENT_TITLE.chinese, BilingualTexts.LOG_MANAGEMENT_TITLE.english)

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    fun loadLogFiles() {
        scope.launch {
            withContext(Dispatchers.IO) {
                logFiles = LogManager.getLogFiles()
                totalSize = LogManager.getTotalLogSize()
                fileCount = logFiles.size
                currentLogSize = logFiles.firstOrNull()?.length() ?: 0L
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogFiles()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = txtTitle,
                    onDismiss = onDismiss,
                    trailingContent = {
                        TextButton(onClick = { loadLogFiles() }) {
                            Text(
                                BilingualTexts.LOG_REFRESH_BUTTON.get(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF007AFF)
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 日志文件统计
                    LogSection(title = BilingualTexts.LOG_STATS_TITLE.get()) {
                        LogStatItem(
                            label = BilingualTexts.LOG_FILE_COUNT.get() + "：", 
                            value = fileCount.toString()
                        )
                        AppDivider()
                        LogStatItem(
                            label = BilingualTexts.LOG_TOTAL_SIZE.get() + "：", 
                            value = formatFileSize(totalSize)
                        )
                        AppDivider()
                        LogStatItem(
                            label = BilingualTexts.LOG_CURRENT_SIZE.get() + "：",
                            value = formatFileSize(currentLogSize)
                        )
                    }

                    // 快捷自动化
                    LogSection(title = BilingualTexts.LOG_QUICK_ACTIONS.get()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(AppDimens.listItemHeight)
                                .clickable {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            LogManager.clearAllLogs()
                                        }
                                        loadLogFiles()
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFFFCC00),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = BilingualTexts.LOG_CLEAR_OLD_LOGS.get(),
                                    color = Color(0xFFFFCC00),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                text = BilingualTexts.LOG_KEEP_CURRENT_ONLY.get(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 日志文件列表
                    if (logFiles.isNotEmpty()) {
                        LogSection(title = BilingualTexts.LOG_FILES_SECTION.get()) {
                            logFiles.forEach { file ->
                                LogFileItem(
                                    file = file,
                                    isCurrent = file == logFiles.firstOrNull(),
                                    onView = {
                                        selectedFile = file
                                        showLogViewer = true
                                    },
                                    onDelete = {
                                        selectedFile = file
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(BilingualTexts.LOG_DELETE_CONFIRM_TITLE.get()) },
            text = { 
                Text(
                    BilingualTexts.LOG_DELETE_CONFIRM_MESSAGE.get()
                        .replace("%s", selectedFile?.name ?: "")
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                selectedFile?.let { LogManager.deleteLogFile(it) }
                            }
                            loadLogFiles()
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text(BilingualTexts.LOG_DELETE_BUTTON.get(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(BilingualTexts.BUTTON_CANCEL.get())
                }
            }
        )
    }

    // 日志查看器
    if (showLogViewer && selectedFile != null) {
        LogViewerDialog(
            file = selectedFile!!,
            onDismiss = { showLogViewer = false }
        )
    }
}

@Composable
fun LogSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun LogStatItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LogFileItem(
    file: File,
    isCurrent: Boolean = false,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 主要内容行：左侧列（文件名、时间、View）和 右侧列（文件大小、当前、删除）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 左侧列：文件名、时间、View
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .clickable(onClick = onView)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = BilingualTexts.LOG_VIEW_BUTTON.get(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 右侧列：文件大小、当前按钮、删除按钮
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.width(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatFileSize(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                if (isCurrent) {
                    Button(
                        onClick = onView,
                        modifier = Modifier
                            .width(60.dp)
                            .height(24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(BilingualTexts.LOG_CURRENT_BUTTON.get(), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // 占位空间，保持布局一致
                    Box(modifier = Modifier.height(24.dp))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = BilingualTexts.LOG_DELETE_BUTTON.get(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (file != LogManager.getLogFiles().firstOrNull()) {
            AppDivider()
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerDialog(
    file: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()) }
    
    // 文件大小限制：1MB
    val maxFileSize = 1024 * 1024L
    var showFileTooLargeDialog by remember { mutableStateOf(false) }
    
    var logContent by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var availableTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearchActive by remember { mutableStateOf(false) }

    // 从日志内容中提取 TAG
    fun extractTags(content: String): List<String> {
        val tagRegex = Regex("""[VDIWEF]/([A-Za-z0-9_]+):""")
        return tagRegex.findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()
    }

    // 过滤日志内容
    fun filterLogContent(content: String, query: String, tags: Set<String>): String {
        var lines = content.lines()
        
        // 按 TAG 筛选
        if (tags.isNotEmpty()) {
            val tagPattern = tags.joinToString("|") { Regex.escape(it) }
            val tagRegex = Regex("""[VDIWEF]/($tagPattern):""")
            lines = lines.filter { line -> tagRegex.containsMatchIn(line) }
        }
        
        // 按搜索关键词筛选
        if (query.isNotBlank()) {
            lines = lines.filter { line -> 
                line.contains(query, ignoreCase = true)
            }
        }
        
        return lines.joinToString("\n")
    }

    fun loadLogContent() {
        scope.launch {
            // 检查文件大小
            if (file.length() > maxFileSize) {
                showFileTooLargeDialog = true
                return@launch
            }
            
            val content = withContext(Dispatchers.IO) {
                LogManager.readLogFile(file)
            }
            logContent = content
            availableTags = extractTags(content)
        }
    }
    
    // 分享日志文件
    fun shareLogFile() {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Scrcpy Log - ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, BilingualTexts.LOG_SHARE_BUTTON.get()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(file) {
        loadLogContent()
    }

    // 计算显示的内容
    val displayContent = remember(logContent, searchQuery, selectedTags) {
        if (logContent.isEmpty()) {
            BilingualTexts.STATUS_CONNECTING.get()
        } else {
            val filtered = filterLogContent(logContent, searchQuery, selectedTags)
            if (filtered.isEmpty() && (searchQuery.isNotBlank() || selectedTags.isNotEmpty())) {
                BilingualTexts.LOG_NO_RESULTS.get()
            } else {
                filtered
            }
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    // 文件过大对话框
    if (showFileTooLargeDialog) {
        AlertDialog(
            onDismissRequest = { showFileTooLargeDialog = false },
            title = { Text(BilingualTexts.LOG_FILE_TOO_LARGE_TITLE.get()) },
            text = { Text(BilingualTexts.LOG_FILE_TOO_LARGE_MESSAGE.get()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFileTooLargeDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LogManager.clearAllLogs()
                            }
                            onDismiss()
                        }
                    }
                ) {
                    Text(BilingualTexts.LOG_CLEAR_AND_RETRY.get())
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showFileTooLargeDialog = false
                    onDismiss()
                }) {
                    Text(BilingualTexts.BUTTON_CANCEL.get())
                }
            }
        )
    }

    // TAG 筛选对话框
    if (showFilterDialog) {
        TagFilterDialog(
            availableTags = availableTags,
            selectedTags = selectedTags,
            onTagsSelected = { selectedTags = it },
            onDismiss = { showFilterDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = "Log Detail",
                    onDismiss = onDismiss,
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 搜索按钮
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = BilingualTexts.LOG_SEARCH_PLACEHOLDER.get(),
                                    tint = Color(0xFF007AFF)
                                )
                            }
                            
                            // 筛选按钮（带徽章）
                            BadgedBox(
                                badge = {
                                    if (selectedTags.isNotEmpty()) {
                                        Badge {
                                            Text(selectedTags.size.toString())
                                        }
                                    }
                                }
                            ) {
                                IconButton(onClick = { showFilterDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = BilingualTexts.LOG_FILTER_BY_TAG.get(),
                                        tint = if (selectedTags.isNotEmpty()) MaterialTheme.colorScheme.primary else Color(0xFF007AFF)
                                    )
                                }
                            }
                            
                            // 分享按钮
                            IconButton(onClick = { shareLogFile() }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = BilingualTexts.LOG_SHARE_BUTTON.get(),
                                    tint = Color(0xFF007AFF)
                                )
                            }
                            
                            // 刷新按钮
                            IconButton(onClick = { loadLogContent() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = BilingualTexts.BUTTON_DONE.get(),
                                    tint = Color(0xFF007AFF)
                                )
                            }
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 搜索框
                    if (isSearchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = BilingualTexts.LOG_SEARCH_PLACEHOLDER.get(),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    // 已选标签显示
                    if (selectedTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                FilterChip(
                                    selected = true,
                                    onClick = { selectedTags = selectedTags - tag },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 文件信息
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppDimens.listItemHeight)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = BilingualTexts.LOG_FILE_LABEL.get() + "：",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AppDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppDimens.listItemHeight)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = BilingualTexts.LOG_SIZE_LABEL.get() + "：",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = formatFileSize(file.length()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AppDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppDimens.listItemHeight)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = BilingualTexts.LOG_MODIFIED_LABEL.get() + "：",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = dateFormat.format(Date(file.lastModified())),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 日志内容
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = displayContent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagFilterDialog(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedTags by remember { mutableStateOf(selectedTags) }
    
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxDialogHeight = screenHeight * 0.6f
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(BilingualTexts.LOG_FILTER_BY_TAG.get()) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (availableTags.isEmpty()) {
                    Text(
                        text = BilingualTexts.LOG_NO_RESULTS.get(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 全选/取消全选
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempSelectedTags = if (tempSelectedTags.size == availableTags.size) {
                                    emptySet()
                                } else {
                                    availableTags.toSet()
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempSelectedTags.size == availableTags.size,
                            onCheckedChange = null
                        )
                        Text(
                            text = BilingualTexts.LOG_ALL_TAGS.get(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    AppDivider()
                    
                    // TAG 列表
                    availableTags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelectedTags = if (tag in tempSelectedTags) {
                                        tempSelectedTags - tag
                                    } else {
                                        tempSelectedTags + tag
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = tag in tempSelectedTags,
                                onCheckedChange = null
                            )
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTagsSelected(tempSelectedTags)
                    onDismiss()
                }
            ) {
                Text(BilingualTexts.BUTTON_CONFIRM.get())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(BilingualTexts.BUTTON_CANCEL.get())
            }
        }
    )
}


fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}