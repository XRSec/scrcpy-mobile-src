package com.mobile.scrcpy.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LanguageManager
import com.mobile.scrcpy.android.core.data.model.DefaultGroups
import com.mobile.scrcpy.android.core.data.model.DeviceGroup
import com.mobile.scrcpy.android.core.data.model.GroupTreeNode
import com.mobile.scrcpy.android.core.data.model.GroupType
import kotlin.collections.isNotEmpty

/**
 * 分组管理对话框（树形展示）
 */
@Composable
fun GroupManagementDialog(
    groups: List<DeviceGroup>,
    sessionCounts: Map<String, Int>,
    onDismiss: () -> Unit,
    onAddGroup: (name: String, parentPath: String, description: String, type: GroupType) -> Unit,
    onUpdateGroup: (DeviceGroup) -> Unit,
    onDeleteGroup: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<DeviceGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<DeviceGroup?>(null) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    var selectedType by remember { mutableStateOf(GroupType.SESSION) }

    // 按类型过滤分组
    val filteredGroups = remember(groups, selectedType) {
        groups.filter { it.type == selectedType }
    }

    // 构建树形结构
    val treeNodes = remember(filteredGroups) { 
        buildGroupTree(filteredGroups)
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
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = BilingualTexts.GROUP_MANAGE.get(),
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = BilingualTexts.BUTTON_CLOSE.get(),
                    rightButtonText = BilingualTexts.GROUP_ADD.get(),
                    onRightButtonClick = {
                        editingGroup = null
                        showAddDialog = true
                    }
                )

                // 类型选择器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = selectedType == GroupType.SESSION,
                        onClick = {
                            selectedType =
                                GroupType.SESSION
                        },
                        label = { Text(BilingualTexts.MAIN_TAB_SESSIONS.get()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == GroupType.AUTOMATION,
                        onClick = {
                            selectedType =
                                GroupType.AUTOMATION
                        },
                        label = { Text(BilingualTexts.MAIN_TAB_ACTIONS.get()) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 分组列表（整个列表用一个 Card 包裹）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    LazyColumn {
                        // 根目录（Home）- 可展开/折叠
                        item {
                            TreeRootItemForManagement(
                                hasChildren = treeNodes.isNotEmpty(),
                                isExpanded = expandedPaths.contains("/"),
                                onToggleExpand = {
                                    expandedPaths = if (expandedPaths.contains("/")) {
                                        expandedPaths - "/"
                                    } else {
                                        expandedPaths + "/"
                                    }
                                }
                            )
                        }

                        // 树形节点（只在根目录展开时显示）
                        if (expandedPaths.contains("/")) {
                            items(treeNodes.size) { index ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )

                                TreeNodeItemForManagement(
                                    node = treeNodes[index],
                                    expandedPaths = expandedPaths,
                                    onToggleExpand = { path ->
                                        expandedPaths = if (expandedPaths.contains(path)) {
                                            expandedPaths - path
                                        } else {
                                            expandedPaths + path
                                        }
                                    },
                                    onEdit = {
                                        editingGroup = it
                                        showAddDialog = true
                                    },
                                    onDelete = {
                                        groupToDelete = it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加/编辑分组对话框
    if (showAddDialog) {
        AddGroupDialog(
            groups = filteredGroups,
            initialName = editingGroup?.name ?: "",
            initialParentPath = editingGroup?.parentPath ?: "/",
            initialDescription = editingGroup?.description ?: "",
            initialType = editingGroup?.type ?: selectedType,
            isEditMode = editingGroup != null,
            onConfirm = { name, parentPath, description, type ->
                if (editingGroup != null) {
                    // 编辑模式：更新分组
                    val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
                    onUpdateGroup(
                        editingGroup!!.copy(
                            name = name,
                            type = type,
                            path = path,
                            parentPath = parentPath,
                            description = description
                        )
                    )
                } else {
                    // 添加模式
                    onAddGroup(name, parentPath, description, type)
                }
                showAddDialog = false
                editingGroup = null
            },
            onDismiss = {
                showAddDialog = false
                editingGroup = null
            }
        )
    }

    // 删除确认对话框
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(BilingualTexts.GROUP_CONFIRM_DELETE.get()) },
            text = {
                Text(
                    String.format(
                        BilingualTexts.GROUP_CONFIRM_DELETE_MESSAGE.get(),
                        group.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(group.id)
                    groupToDelete = null
                }) {
                    Text(BilingualTexts.GROUP_DELETE.get())
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text(BilingualTexts.BUTTON_CANCEL.get())
                }
            }
        )
    }
}


/**
 * 树形根目录项（管理模式）
 * 用于分组管理，支持展开/折叠
 */
@Composable
private fun TreeRootItemForManagement(
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = BilingualTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 展开/折叠图标（移到右边）
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .size(30.dp)
                    .padding(0.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 树形节点项（管理模式，递归渲染）
 * 用于分组管理，支持编辑和删除操作
 */
@Composable
private fun TreeNodeItemForManagement(
    node: GroupTreeNode,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onEdit: (DeviceGroup) -> Unit,
    onDelete: (DeviceGroup) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .padding(start = (16 + node.level * 20).dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 展开/折叠图标
                if (hasChildren) {
                    IconButton(
                        onClick = { onToggleExpand(node.group.path) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant

                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 文件夹图标
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 分组名称和描述
                Column {
                    Text(
                        text = node.group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (node.group.description.isNotBlank()) {
                        Text(
                            text = node.group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 右侧按钮组（编辑+删除）
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 编辑按钮
                IconButton(
                    onClick = { onEdit(node.group) },
                    modifier = Modifier
                        .size(30.dp)
                        .padding(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = BilingualTexts.GROUP_EDIT.get(),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 删除按钮（最右边）
                IconButton(
                    onClick = { onDelete(node.group) },
                    modifier = Modifier
                        .size(30.dp)
                        .padding(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = BilingualTexts.GROUP_DELETE.get(),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 子节点（带动画）
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.shrinkVertically()
        ) {
            Column {
                node.children.forEach { childNode ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    TreeNodeItemForManagement(
                        node = childNode,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}


/**
 * 构建分组树形结构
 */
private fun buildGroupTree(groups: List<DeviceGroup>): List<GroupTreeNode> {
    val groupMap = groups.associateBy { it.path }
    val rootNodes = mutableListOf<GroupTreeNode>()

    fun buildNode(
        path: String,
        level: Int
    ): GroupTreeNode? {
        val group = groupMap[path] ?: return null
        val children = groups
            .filter { it.parentPath == path }
            .sortedBy { it.name }
            .mapNotNull { buildNode(it.path, level + 1) }
        return GroupTreeNode(
            group,
            children,
            false,
            level
        )
    }

    // 构建根节点的子节点
    groups.filter { it.parentPath == "/" }
        .sortedBy { it.name }
        .forEach { group ->
            buildNode(group.path, 0)?.let { rootNodes.add(it) }
        }

    return rootNodes
}


/**
 * 分组选择器组件（支持多选，树形结构）
 * 新交互：
 * 1. 上方：分组树选择器（单选，可展开/折叠，Home 默认折叠）
 * 2. 中间：添加按钮（在完成按钮旁边）
 * 3. 下方：已选择的分组列表（每项带删除按钮）
 *
 * @param selectedGroupIds 已选中的分组 ID 列表
 * @param availableGroups 可用的分组列表
 * @param onGroupsSelected 选择完成回调
 * @param onDismiss 关闭回调
 */
@Composable
fun GroupSelectorDialog(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // 已选择的分组列表（这是最终会保存的）
    var tempSelectedIds by remember { mutableStateOf(selectedGroupIds) }
    // 当前在树中选中的分组（用于添加）
    var currentSelectedGroupId by remember { mutableStateOf<String?>(null) }
    // 是否处于编辑模式（从已选列表点击编辑进入）
    var isEditingMode by remember { mutableStateOf(false) }
    // 展开的路径集合（包括根目录 "/"）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // 构建树形结构
    val treeNodes = remember(availableGroups) {
        buildGroupTree(availableGroups)
    }
    // 检查根目录是否有子节点
    val hasRootChildren = treeNodes.isNotEmpty()

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
                .fillMaxWidth(0.95f),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                // 顶部标题栏（添加"添加"按钮）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = BilingualTexts.BUTTON_CANCEL.get(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = BilingualTexts.GROUP_SELECT.get(),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 保存按钮（放左边）
                        TextButton(
                            onClick = {
                                // 只保存已添加到列表的分组
                                onGroupsSelected(tempSelectedIds)
                            }
                        ) {
                            Text(
                                text = BilingualTexts.BUTTON_SAVE.get(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 添加/完成按钮（编辑模式时显示为完成图标，放右边）
                        val hasSelection = currentSelectedGroupId != null && !tempSelectedIds.contains(currentSelectedGroupId!!)
                        TextButton(
                            onClick = {
                                currentSelectedGroupId?.let { groupId ->
                                    if (!tempSelectedIds.contains(groupId)) {
                                        tempSelectedIds = tempSelectedIds + groupId
                                    }
                                    // 清空树的选择状态和编辑模式
                                    currentSelectedGroupId = null
                                    isEditingMode = false
                                }
                            },
                            enabled = hasSelection
                        ) {
                            Icon(
                                imageVector = if (isEditingMode) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (isEditingMode) {
                                    if (LanguageManager.isChinese()) "完成" else "Done"
                                } else {
                                    BilingualTexts.BUTTON_ADD.get()
                                },
                                tint = if (hasSelection) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    // 上方：分组树选择器
                    Text(
                        text = if (LanguageManager.isChinese()) "选择分组" else "Select Group",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        LazyColumn {
                            // 根目录（Home）- 可展开/折叠
                            item {
                                TreeRootItemForSelector(
                                    hasChildren = hasRootChildren,
                                    isExpanded = expandedPaths.contains("/"),
                                    onToggleExpand = {
                                        expandedPaths = if (expandedPaths.contains("/")) {
                                            expandedPaths - "/"
                                        } else {
                                            expandedPaths + "/"
                                        }
                                    }
                                )
                            }

                            // 树形节点（只在根目录展开时显示）
                            if (expandedPaths.contains("/")) {
                                items(treeNodes.size) { index ->
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )

                                    TreeNodeItemForSelector(
                                        node = treeNodes[index],
                                        currentSelectedId = currentSelectedGroupId,
                                        alreadyAddedIds = tempSelectedIds,
                                        expandedPaths = expandedPaths,
                                        onToggleExpand = { path ->
                                            expandedPaths = if (expandedPaths.contains(path)) {
                                                expandedPaths - path
                                            } else {
                                                expandedPaths + path
                                            }
                                        },
                                        onSelect = { groupId ->
                                            currentSelectedGroupId = if (currentSelectedGroupId == groupId) null else groupId
                                            // 如果取消选中，也要退出编辑模式
                                            if (currentSelectedGroupId == null) {
                                                isEditingMode = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 下方：已选择的分组列表
                    if (tempSelectedIds.isNotEmpty()) {
                        Text(
                            text = if (LanguageManager.isChinese())
                                "已选择 (${tempSelectedIds.size})"
                            else
                                "Selected (${tempSelectedIds.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                        ) {
                            Column {
                                tempSelectedIds.forEachIndexed { index, groupId ->
                                    val group = availableGroups.find { it.id == groupId }

                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }

                                    if (group != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(AppDimens.listItemHeight)
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = group.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 编辑按钮
                                                IconButton(
                                                    onClick = {
                                                        // 1. 从已选列表中移除
                                                        tempSelectedIds = tempSelectedIds.filter { it != groupId }
                                                        // 2. 在树中选中该分组
                                                        currentSelectedGroupId = groupId
                                                        // 3. 设置为编辑模式
                                                        isEditingMode = true
                                                        // 4. 展开该分组的父路径
                                                        val pathsToExpand = mutableSetOf<String>()
                                                        pathsToExpand.add("/") // 展开根目录
                                                        var currentPath = group.parentPath
                                                        while (currentPath != "/" && currentPath.isNotEmpty()) {
                                                            pathsToExpand.add(currentPath)
                                                            // 获取父路径
                                                            val lastSlash = currentPath.lastIndexOf('/')
                                                            currentPath = if (lastSlash > 0) {
                                                                currentPath.substring(0, lastSlash)
                                                            } else {
                                                                "/"
                                                            }
                                                        }
                                                        expandedPaths = expandedPaths + pathsToExpand
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = BilingualTexts.GROUP_EDIT.get(),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                // 删除按钮
                                                IconButton(
                                                    onClick = {
                                                        tempSelectedIds = tempSelectedIds.filter { it != groupId }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = if (LanguageManager.isChinese()) "删除" else "Remove",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 树形根目录项（选择模式）
 * 用于分组选择器，支持展开/折叠
 */
@Composable
private fun TreeRootItemForSelector(
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = BilingualTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 展开/折叠图标（移到右边）
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 树形节点项（选择模式，递归渲染）
 * 用于分组选择器，支持多选和已添加状态显示
 */
@Composable
private fun TreeNodeItemForSelector(
    node: GroupTreeNode,
    currentSelectedId: String?,
    alreadyAddedIds: List<String>,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()
    val isCurrentSelected = currentSelectedId == node.group.id
    val isAlreadyAdded = alreadyAddedIds.contains(node.group.id)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .background(
                    if (isCurrentSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable { onSelect(node.group.id) }
                .padding(start = (16 + node.level * 20).dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 展开/折叠图标
                if (hasChildren) {
                    IconButton(
                        onClick = { onToggleExpand(node.group.path) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 文件夹图标
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = when {
                        isAlreadyAdded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isCurrentSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // 分组名称和描述
                Column {
                    Text(
                        text = node.group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAlreadyAdded) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (node.group.description.isNotBlank()) {
                        Text(
                            text = node.group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isAlreadyAdded) 0.3f else 1f
                            )
                        )
                    }
                }
            }

            // 右侧状态图标
            if (isAlreadyAdded) {
                Text(
                    text = if (LanguageManager.isChinese()) "已添加" else "Added",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else if (isCurrentSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 子节点（带动画）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                node.children.forEach { childNode ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    TreeNodeItemForSelector(
                        node = childNode,
                        currentSelectedId = currentSelectedId,
                        alreadyAddedIds = alreadyAddedIds,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}



/**
 * 分组选择按钮（用于会话对话框）
 * 样式与 LabeledTextField 保持一致，显示路径式分组
 */
@Composable
fun GroupSelectorButton(
    selectedGroupIds: List<String>,
    availableGroups: List<DeviceGroup>,
    onGroupsSelected: (List<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    // 将选中的分组名称用斜杠连接，形成路径式显示
    val displayText = if (selectedGroupIds.isNotEmpty()) {
        availableGroups
            .filter { selectedGroupIds.contains(it.id) }
            .joinToString(" / ") { it.name }
    } else {
        BilingualTexts.GROUP_PLACEHOLDER_DESCRIPTION.get()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = BilingualTexts.GROUP_SELECT.get(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = displayText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selectedGroupIds.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
    }

    if (showDialog) {
        GroupSelectorDialog(
            selectedGroupIds = selectedGroupIds,
            availableGroups = availableGroups,
            onGroupsSelected = onGroupsSelected,
            onDismiss = { showDialog = false }
        )
    }
}


/**
 * 路径选择对话框（树形展示）
 *
 * 用于选择分组路径，支持树形结构展示和展开/折叠。
 * 适用场景：
 * - 创建/编辑分组时选择父路径
 * - 其他需要选择分组路径的场景
 *
 * @param groups 可选的分组列表
 * @param selectedPath 当前选中的路径
 * @param onPathSelected 路径选择回调
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun PathSelectorDialog(
    groups: List<DeviceGroup>,
    selectedPath: String,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedPath) }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }

    // 构建树形结构
    val treeNodes = remember(groups) {
        buildGroupTree(groups)
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
                .wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = BilingualTexts.GROUP_SELECT_PATH.get(),
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = BilingualTexts.BUTTON_CANCEL.get(),
                    rightButtonText = BilingualTexts.BUTTON_CONFIRM.get(),
                    onRightButtonClick = {
                        onPathSelected(currentSelection)
                    }
                )

                // 整个列表用一个 Card 包裹（参考 SettingsCard 样式）
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    LazyColumn {
                        // 根目录
                        item {
                            TreeRootItem(
                                isSelected = currentSelection == "/",
                                hasChildren = treeNodes.isNotEmpty(),
                                isExpanded = expandedPaths.contains("/"),
                                onToggleExpand = {
                                    expandedPaths = if (expandedPaths.contains("/")) {
                                        expandedPaths - "/"
                                    } else {
                                        expandedPaths + "/"
                                    }
                                },
                                onClick = { currentSelection = "/" }
                            )
                        }

                        // 树形节点（只在根目录展开时显示）
                        if (expandedPaths.contains("/")) {
                            items(treeNodes.size) { index ->
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )

                                TreeNodeItem(
                                    node = treeNodes[index],
                                    selectedPath = currentSelection,
                                    expandedPaths = expandedPaths,
                                    onToggleExpand = { path ->
                                        expandedPaths = if (expandedPaths.contains(path)) {
                                            expandedPaths - path
                                        } else {
                                            expandedPaths + path
                                        }
                                    },
                                    onSelect = { currentSelection = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 树形根目录项（统一组件）
 *
 * 用于渲染树形结构的根节点（Home），支持展开/折叠和选中状态。
 *
 * @param isSelected 是否选中
 * @param hasChildren 是否有子节点
 * @param isExpanded 是否展开
 * @param onToggleExpand 展开/折叠回调
 * @param onClick 点击回调
 */
@Composable
private fun TreeRootItem(
    isSelected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = BilingualTexts.GROUP_ROOT.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 右侧：展开/折叠按钮或选中标记
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 树形节点项（统一组件，递归渲染）
 *
 * 用于渲染树形结构中的节点，支持展开/折叠和选中状态。
 *
 * @param node 树形节点数据
 * @param selectedPath 当前选中的路径
 * @param expandedPaths 已展开的路径集合
 * @param onToggleExpand 展开/折叠回调
 * @param onSelect 选中回调
 */
@Composable
private fun TreeNodeItem(
    node: GroupTreeNode,
    selectedPath: String,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.group.path)
    val hasChildren = node.children.isNotEmpty()
    val isSelected = selectedPath == node.group.path

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.listItemHeight)
                .clickable { onSelect(node.group.path) }
                .padding(start = (16 + node.level * 20).dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 展开/折叠图标
                if (hasChildren) {
                    IconButton(
                        onClick = { onToggleExpand(node.group.path) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 文件夹图标
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // 分组名称
                Text(
                    text = node.group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 子节点（带动画）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                node.children.forEach { childNode ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    TreeNodeItem(
                        node = childNode,
                        selectedPath = selectedPath,
                        expandedPaths = expandedPaths,
                        onToggleExpand = onToggleExpand,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}



/**
 * 底部路径面包屑（显示完整路径）
 */
@Composable
fun PathBreadcrumb(
    selectedGroupPath: String
) {
    // 如果是默认状态，不显示
    if (selectedGroupPath == DefaultGroups.ALL_DEVICES || selectedGroupPath == DefaultGroups.UNGROUPED) {
        return
    }

    val pathParts = selectedGroupPath.split("/").filter { it.isNotEmpty() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // All
            Text(
                text = BilingualTexts.GROUP_ALL.get(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 路径的每一层
            pathParts.forEach { part ->
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )

                Text(
                    text = part,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 分组树形结构工具类
 *
 * 统一管理树形结构构建逻辑，避免重复代码。
 * 用于将扁平的分组列表转换为层级树形结构，便于 UI 展示。
 */
object GroupTreeUtils {

    /**
     * 构建分组树形结构
     *
     * 将扁平的分组列表转换为树形结构，根据 parentPath 建立父子关系。
     * 树形结构从根路径 "/" 开始，递归构建所有子节点。
     *
     * @param groups 扁平的分组列表，每个分组包含 path 和 parentPath 信息
     * @return 根节点列表（parentPath 为 "/" 的所有分组），按名称排序
     *
     * @see DeviceGroup 分组数据模型
     * @see GroupTreeNode 树形节点模型
     *
     * 示例：
     * ```
     * 输入: [
     *   DeviceGroup(path="/A", parentPath="/"),
     *   DeviceGroup(path="/A/B", parentPath="/A"),
     *   DeviceGroup(path="/C", parentPath="/")
     * ]
     *
     * 输出: [
     *   GroupTreeNode(group=A, children=[
     *     GroupTreeNode(group=B, children=[], level=1)
     *   ], level=0),
     *   GroupTreeNode(group=C, children=[], level=0)
     * ]
     * ```
     */
    fun buildGroupTree(groups: List<DeviceGroup>): List<GroupTreeNode> {
        val groupMap = groups.associateBy { it.path }
        val rootNodes = mutableListOf<GroupTreeNode>()

        fun buildNode(path: String, level: Int): GroupTreeNode? {
            val group = groupMap[path] ?: return null
            val children = groups
                .filter { it.parentPath == path }
                .sortedBy { it.name }
                .mapNotNull { buildNode(it.path, level + 1) }
            return GroupTreeNode(group, children, false, level)
        }

        // 构建根节点的子节点
        groups.filter { it.parentPath == "/" }
            .sortedBy { it.name }
            .forEach { group ->
                buildNode(group.path, 0)?.let { rootNodes.add(it) }
            }

        return rootNodes
    }
}
