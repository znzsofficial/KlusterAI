package  com.nekolaska.klusterai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close // 用于中断按钮
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun StreamingResponseDialog(
    content: String,
    onDismissRequest: () -> Unit,
    onInterruptStream: () -> Unit // 新增回调：中断流
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // 状态：用户是否因为向上滚动而暂时禁用了自动滚动
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // 当用户手动滚动时，决定是否禁用自动滚动
    LaunchedEffect(scrollState) {
        var previousValue = scrollState.value // 记录上一次的滚动位置
        snapshotFlow { scrollState.value } // 创建一个Flow来观察滚动位置的变化
            .distinctUntilChanged() // 只有当滚动位置真正改变时才发出新值
            .collect { currentValue ->
                // 如果用户向上滚动 (currentValue < previousValue)
                // 并且滚动条没有在最底部 (允许一点误差)
                if (currentValue < previousValue && currentValue < scrollState.maxValue - 10) { // 10px 误差
                    if (autoScrollEnabled) {
                        // Log.d("ScrollDebug", "User scrolled up, disabling auto-scroll.")
                        autoScrollEnabled = false
                    }
                }
                // 如果用户滚动到底部了
                else if (currentValue >= scrollState.maxValue - 10) {
                    if (!autoScrollEnabled) {
                        // Log.d("ScrollDebug", "User scrolled to bottom, re-enabling auto-scroll.")
                        autoScrollEnabled = true
                    }
                }
                previousValue = currentValue
            }
    }

    // 当新内容到达，或者可滚动区域变大时，如果允许自动滚动，则滚动到底部
    LaunchedEffect(content, scrollState.maxValue) {
        if (content.isNotBlank() && autoScrollEnabled) {
            coroutineScope.launch {
                // Log.d("ScrollDebug", "Content changed, auto-scrolling to: ${scrollState.maxValue}")
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }


    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("实时回复中...") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 100.dp,
                        max = 400.dp
                    )
                    .verticalScroll(scrollState)
            ) {
                SelectionContainer {
                    Text(
                        text = content.ifBlank { "等待响应..." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = { // "隐藏" 按钮
            TextButton(onClick = onDismissRequest) { // 点击隐藏时只关闭对话框
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "隐藏",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("隐藏")
            }
        },
        dismissButton = { // "中断" 按钮
            TextButton(
                onClick = {
                    onInterruptStream() // 调用中断回调
                    onDismissRequest()  // 同时关闭对话框
                },
                colors = ButtonDefaults.textButtonColors( // 使用 ButtonDefaults 设置颜色 提示破坏性操作
                    contentColor = MaterialTheme.colorScheme.error // 设置内容颜色 (文本和图标)
                )
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "中断",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("中断回复")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large
    )
}