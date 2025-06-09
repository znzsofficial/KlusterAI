package com.nekolaska.klusterai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nekolaska.klusterai.SessionMeta
import com.nekolaska.klusterai.formatTimestamp
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListDialog(
    sessions: List<SessionMeta>,
    currentSessionId: String?,
    onSessionSelected: (sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sessionToRename by remember { mutableStateOf<SessionMeta?>(null) }
    val lazyListState = rememberLazyListState() // 获取 LazyListState
    val coroutineScope = rememberCoroutineScope() // 用于启动协程滚动
    // 使用 LaunchedEffect 滚动到当前项
    LaunchedEffect(sessions, currentSessionId) { // 当列表或当前ID变化时触发
        if (currentSessionId != null && sessions.isNotEmpty()) {
            val currentIndex = sessions.indexOfFirst { it.id == currentSessionId } // 找到索引
            if (currentIndex != -1) {
                // 确保列表足够长，避免滚动到不存在的项 (虽然 indexOfFirst 会处理)
                // 使用 animateScrollToItem 以获得平滑滚动效果
                coroutineScope.launch { // 在协程中调用滚动方法
                    lazyListState.animateScrollToItem(index = currentIndex)
                }
                // 如果不需要动画，可以直接用：
                // lazyListState.scrollToItem(index = currentIndex)
            }
        }
    }

    Dialog(onDismissRequest = {
        sessionToRename = null // 关闭重命名输入时重置
        onDismiss()
    }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "会话列表",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (sessions.isEmpty()) {
                    Text(
                        "还没有保存的会话。",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        state = lazyListState, // 将 state 传递给 LazyColumn
                    ) { // 限制高度，使其可滚动
                        items(sessions, key = { it.id }) { session ->
                            SessionListItem(
                                session = session,
                                isCurrent = session.id == currentSessionId,
                                onClick = { onSessionSelected(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                                onRename = {
                                    sessionToRename = session
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("关闭")
                }
            }
        }
    }

    // 重命名对话框 (嵌套或作为另一个AlertDialog)
    if (sessionToRename != null) {
        var renameTitleField by remember(sessionToRename) {
            mutableStateOf(
                TextFieldValue(
                    sessionToRename!!.title,
                    selection = TextRange(sessionToRename!!.title.length)
                )
            )
        }
        val renameFocusRequester = remember { FocusRequester() }

        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameTitleField,
                    onValueChange = { renameTitleField = it },
                    label = { Text("新标题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(renameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (renameTitleField.text.isNotBlank()) {
                            onRenameSession(sessionToRename!!.id, renameTitleField.text)
                            sessionToRename = null
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameTitleField.text.isNotBlank()) {
                            onRenameSession(sessionToRename!!.id, renameTitleField.text)
                            sessionToRename = null
                        }
                    },
                    enabled = renameTitleField.text.isNotBlank()
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { sessionToRename = null }) { Text("取消") } }
        )
        LaunchedEffect(sessionToRename) {
            if (sessionToRename != null) renameFocusRequester.requestFocus()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSessionDialog(
    initialTitle: String,
    onSave: (title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember {
        mutableStateOf(
            TextFieldValue(
                initialTitle,
                TextRange(initialTitle.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存会话") },
        text = {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("会话标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (titleInput.text.isNotBlank()) {
                        onSave(titleInput.text)
                    }
                    keyboardController?.hide()
                })
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titleInput.text.isNotBlank()) {
                        onSave(titleInput.text)
                    }
                },
                enabled = titleInput.text.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun SessionListItem(
    session: SessionMeta,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    //val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                "最后修改: ${formatTimestamp(session.lastModifiedTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = {
                    onRename()
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("删除") }, onClick = {
                    onDelete() // onDelete 应该触发确认对话框
                    showMenu = false
                })
            }
        }
    }
}