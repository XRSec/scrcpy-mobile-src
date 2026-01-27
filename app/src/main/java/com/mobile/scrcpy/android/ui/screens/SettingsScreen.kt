package com.mobile.scrcpy.android.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.FilePathConstants
import com.mobile.scrcpy.android.common.rememberText
import com.mobile.scrcpy.android.feature.session.MainViewModel
import com.mobile.scrcpy.android.ui.components.AdbKeyManagementDialog
import com.mobile.scrcpy.android.ui.components.DialogHeader
import com.mobile.scrcpy.android.ui.components.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.ui.components.IOSStyledDropdownMenuItem
import com.mobile.scrcpy.android.ui.components.SectionTitle

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToLogManagement: () -> Unit,
    onNavigateToGroupManagement: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var showAdbKeyDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var showFilePathDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f
    
    // 双语文本
    val txtTitle = rememberText(BilingualTexts.SETTINGS_TITLE.chinese, BilingualTexts.SETTINGS_TITLE.english)
    val txtDone = rememberText(BilingualTexts.BUTTON_DONE.chinese, BilingualTexts.BUTTON_DONE.english)
    val txtGeneral = rememberText(BilingualTexts.SETTINGS_GENERAL.chinese, BilingualTexts.SETTINGS_GENERAL.english)
    val txtAppearance = rememberText(BilingualTexts.SETTINGS_APPEARANCE.chinese, BilingualTexts.SETTINGS_APPEARANCE.english)
    val txtKeepAlive = rememberText(BilingualTexts.SETTINGS_KEEP_ALIVE.chinese, BilingualTexts.SETTINGS_KEEP_ALIVE.english)
    val txtShowOnLockScreen = rememberText(BilingualTexts.SETTINGS_SHOW_ON_LOCK_SCREEN.chinese, BilingualTexts.SETTINGS_SHOW_ON_LOCK_SCREEN.english)
    val txtFloatingHaptic = rememberText(BilingualTexts.SETTINGS_FLOATING_HAPTIC.chinese, BilingualTexts.SETTINGS_FLOATING_HAPTIC.english)
    val txtLanguage = rememberText(BilingualTexts.SETTINGS_LANGUAGE.chinese, BilingualTexts.SETTINGS_LANGUAGE.english)
    val txtAbout = rememberText(BilingualTexts.SETTINGS_ABOUT.chinese, BilingualTexts.SETTINGS_ABOUT.english)
    val txtGroupManage = rememberText(BilingualTexts.GROUP_MANAGE.chinese, BilingualTexts.GROUP_MANAGE.english)
    val txtAdbManagement = rememberText(BilingualTexts.SETTINGS_ADB_MANAGEMENT.chinese, BilingualTexts.SETTINGS_ADB_MANAGEMENT.english)
    val txtManageAdbKeys = rememberText(BilingualTexts.SETTINGS_MANAGE_ADB_KEYS.chinese, BilingualTexts.SETTINGS_MANAGE_ADB_KEYS.english)
    val txtAdbPairing = rememberText(BilingualTexts.SETTINGS_ADB_PAIRING.chinese, BilingualTexts.SETTINGS_ADB_PAIRING.english)
    val txtFileTransferPath = rememberText(BilingualTexts.SETTINGS_FILE_TRANSFER_PATH.chinese, BilingualTexts.SETTINGS_FILE_TRANSFER_PATH.english)
    val txtAppLogs = rememberText(BilingualTexts.SETTINGS_APP_LOGS.chinese, BilingualTexts.SETTINGS_APP_LOGS.english)
    val txtEnableLog = rememberText(BilingualTexts.SETTINGS_ENABLE_LOG.chinese, BilingualTexts.SETTINGS_ENABLE_LOG.english)
    val txtLogManagement = rememberText(BilingualTexts.SETTINGS_LOG_MANAGEMENT.chinese, BilingualTexts.SETTINGS_LOG_MANAGEMENT.english)
    val txtClearLogs = rememberText(BilingualTexts.SETTINGS_CLEAR_LOGS.chinese, BilingualTexts.SETTINGS_CLEAR_LOGS.english)
    val txtFeedbackSupport = rememberText(BilingualTexts.SETTINGS_FEEDBACK_SUPPORT.chinese, BilingualTexts.SETTINGS_FEEDBACK_SUPPORT.english)
    val txtSubmitIssue = rememberText(BilingualTexts.SETTINGS_SUBMIT_ISSUE.chinese, BilingualTexts.SETTINGS_SUBMIT_ISSUE.english)
    val txtUserGuide = rememberText(BilingualTexts.SETTINGS_USER_GUIDE.chinese, BilingualTexts.SETTINGS_USER_GUIDE.english)
    
    val txt1Min = rememberText(BilingualTexts.TIME_1_MINUTE.chinese, BilingualTexts.TIME_1_MINUTE.english)
    val txt5Min = rememberText(BilingualTexts.TIME_5_MINUTES.chinese, BilingualTexts.TIME_5_MINUTES.english)
    val txt10Min = rememberText(BilingualTexts.TIME_10_MINUTES.chinese, BilingualTexts.TIME_10_MINUTES.english)
    val txt30Min = rememberText(BilingualTexts.TIME_30_MINUTES.chinese, BilingualTexts.TIME_30_MINUTES.english)
    val txt1Hour = rememberText(BilingualTexts.TIME_1_HOUR.chinese, BilingualTexts.TIME_1_HOUR.english)
    val txtAlways = rememberText(BilingualTexts.TIME_ALWAYS.chinese, BilingualTexts.TIME_ALWAYS.english)

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
                    showBackButton = false,
                    rightButtonText = txtDone,
                    onRightButtonClick = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    // 通用设置卡片
                    SettingsCard(title = txtGeneral) {
                        SettingsItem(
                            title = txtAppearance,
                            onClick = onNavigateToAppearance
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtGroupManage,
                            onClick = onNavigateToGroupManagement
                        )
                        SettingsDivider()
                        SettingsItemWithMenu(
                            title = txtKeepAlive,
                            subtitle = when (settings.keepAliveMinutes) {
                                1 -> txt1Min
                                5 -> txt5Min
                                10 -> txt10Min
                                30 -> txt30Min
                                60 -> txt1Hour
                                -1 -> txtAlways
                                else -> "${settings.keepAliveMinutes} minutes"
                            },
                            expanded = showKeepAliveDialog,
                            onExpandedChange = { showKeepAliveDialog = it },
                            menuContent = {
                                listOf(
                                    1 to txt1Min,
                                    5 to txt5Min,
                                    10 to txt10Min,
                                    30 to txt30Min,
                                    60 to txt1Hour,
                                    -1 to txtAlways
                                ).forEach { (minutes, label) ->
                                    IOSStyledDropdownMenuItem(
                                        text = label,
                                        onClick = {
                                            viewModel.updateSettings(settings.copy(keepAliveMinutes = minutes))
                                            showKeepAliveDialog = false
                                        }
                                    )
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsSwitch(
                            title = txtFloatingHaptic,
                            checked = settings.enableFloatingHapticFeedback,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(enableFloatingHapticFeedback = it))
                                // 同步更新全局触感反馈状态
                                com.mobile.scrcpy.android.common.HapticFeedbackManager.setEnabled(it)
                            }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtLanguage,
                            onClick = onNavigateToLanguage
                        )
                        SettingsDivider()
                        SettingsSwitch(
                            title = txtShowOnLockScreen,
                            checked = settings.showOnLockScreen,
                            enabled = false,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(showOnLockScreen = it))
                            }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtAbout,
                            onClick = onNavigateToAbout
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ADB 管理卡片
                    SettingsCard(title = txtAdbManagement) {
                        SettingsItem(
                            title = txtManageAdbKeys,
                            onClick = { showAdbKeyDialog = true }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtAdbPairing,
                            onClick = { /* TODO */ }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtFileTransferPath,
                            subtitle = settings.fileTransferPath.substringAfterLast('/'),
                            onClick = { showFilePathDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 应用日志卡片
                    SettingsCard(title = txtAppLogs) {
                        SettingsSwitch(
                            title = txtEnableLog,
                            checked = settings.enableActivityLog,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(enableActivityLog = it))
                                com.mobile.scrcpy.android.common.LogManager.setEnabled(it)
                            }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtLogManagement,
                            onClick = onNavigateToLogManagement
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtClearLogs,
                            isDestructive = true,
                            onClick = { showClearLogsDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 反馈与支持卡片
                    SettingsCard(title = txtFeedbackSupport) {
                        SettingsItem(
                            title = txtSubmitIssue,
                            showExternalIcon = true,
                            isLink = true,
                            onClick = { /* TODO 蓝色*/ }
                        )
                        SettingsDivider()
                        SettingsItem(
                            title = txtUserGuide,
                            showExternalIcon = true,
                            isLink = true,
                            onClick = { /* TODO */ }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

    }

    if (showAdbKeyDialog) {
        AdbKeyManagementDialog(
            viewModel = viewModel,
            onDismiss = { showAdbKeyDialog = false }
        )
    }

    if (showClearLogsDialog) {
        val txtClearLogsTitle = rememberText(BilingualTexts.DIALOG_CLEAR_LOGS_TITLE.chinese, BilingualTexts.DIALOG_CLEAR_LOGS_TITLE.english)
        val txtClearLogsMessage = rememberText(BilingualTexts.DIALOG_CLEAR_LOGS_MESSAGE.chinese, BilingualTexts.DIALOG_CLEAR_LOGS_MESSAGE.english)
        val txtClearLogsConfirm = rememberText(BilingualTexts.DIALOG_CLEAR_LOGS_CONFIRM.chinese, BilingualTexts.DIALOG_CLEAR_LOGS_CONFIRM.english)
        val txtCancel = rememberText(BilingualTexts.BUTTON_CANCEL.chinese, BilingualTexts.BUTTON_CANCEL.english)
        
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text(txtClearLogsTitle) },
            text = { Text(txtClearLogsMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.mobile.scrcpy.android.common.LogManager.clearAllLogs()
                        showClearLogsDialog = false
                    }
                ) {
                    Text(txtClearLogsConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text(txtCancel)
                }
            }
        )
    }

    if (showFilePathDialog) {
        FilePathDialog(
            currentPath = settings.fileTransferPath,
            onDismiss = { showFilePathDialog = false },
            onConfirm = { path ->
                viewModel.updateSettings(settings.copy(fileTransferPath = path))
                showFilePathDialog = false
            }
        )
    }
}

@Composable
fun SettingsCard(
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
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    showExternalIcon: Boolean = false,
    isDestructive: Boolean = false,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                isDestructive -> MaterialTheme.colorScheme.error
                isLink -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showExternalIcon) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "外部链接",
                    tint = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsItemWithMenu(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .clickable { onExpandedChange(true) }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IOSStyledDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(min = 80.dp)
            ) {
                menuContent()
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.38f
            )
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.9f)
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun FilePathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var editablePath by remember { mutableStateOf(currentPath) }
    
    val txtTitle = rememberText(BilingualTexts.DIALOG_FILE_PATH_TITLE.chinese, BilingualTexts.DIALOG_FILE_PATH_TITLE.english)
    val txtDefault = rememberText(BilingualTexts.DIALOG_FILE_PATH_DEFAULT.chinese, BilingualTexts.DIALOG_FILE_PATH_DEFAULT.english)
    val txtQuickSelect = rememberText(BilingualTexts.DIALOG_FILE_PATH_QUICK_SELECT.chinese, BilingualTexts.DIALOG_FILE_PATH_QUICK_SELECT.english)
    val txtInfo = rememberText(BilingualTexts.DIALOG_FILE_PATH_INFO.chinese, BilingualTexts.DIALOG_FILE_PATH_INFO.english)
    val txtInfoText = rememberText(BilingualTexts.DIALOG_FILE_PATH_INFO_TEXT.chinese, BilingualTexts.DIALOG_FILE_PATH_INFO_TEXT.english)
    val txtReset = rememberText(BilingualTexts.DIALOG_FILE_PATH_RESET.chinese, BilingualTexts.DIALOG_FILE_PATH_RESET.english)
    val txtSave = rememberText(BilingualTexts.BUTTON_SAVE.chinese, BilingualTexts.BUTTON_SAVE.english)

    val quickPaths = FilePathConstants.QUICK_SELECT_PATHS

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

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
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        txtDefault,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        OutlinedTextField(
                            value = editablePath,
                            onValueChange = { editablePath = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            placeholder = { Text("Download") },
                            singleLine = true
                        )
                    }

                    Text(
                        txtQuickSelect,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickPaths.chunked(3).forEach { rowPaths ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPaths.forEach { path ->
                                        val isSelected = editablePath == path
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { editablePath = path },
                                            label = {
                                                Text(
                                                    path.substringAfterLast("/"),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowPaths.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editablePath = currentPath },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(txtReset)
                        }
                    }

                    Text(
                        txtInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            txtInfoText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onConfirm(editablePath) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editablePath.isNotBlank()
                    ) {
                        Text(txtSave)
                    }
                }
            }
        }
    }
}
