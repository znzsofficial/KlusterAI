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
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
fun StreamingResponseDialog(
    content: String,
    onDismissRequest: () -> Unit,
    onInterruptStream: () -> Unit // 新增回调：中断流
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(content) {
        // 只有当滚动条不满（即内容未填满可视区域的底部）或者已滚动到底部时，才在内容增加时继续滚动到底部
        // 这样可以避免用户向上滚动查看时，新内容又把视图拉下去
        if (scrollState.value == scrollState.maxValue || scrollState.maxValue == 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest, // 点击外部或返回键时调用
        title = { Text("实时回复中...") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 100.dp,
                        max = (LocalWindowInfo.current.containerSize.height * 0.4).dp
                    ) // 最大高度为屏幕40%
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