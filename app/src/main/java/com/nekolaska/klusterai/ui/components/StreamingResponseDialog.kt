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

@Composable
fun StreamingResponseDialog(
    content: String,
    onDismissRequest: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(content) { // 当内容变化时，滚动到底部
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("实时回复中...") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp) // 限制最大高度
                    .verticalScroll(scrollState) // 使其可滚动
            ) {
                SelectionContainer { // 允许选择和复制文本
                    Text(
                        text = content.ifBlank { "等待响应..." }, // 内容为空时的提示
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "隐藏",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("隐藏")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, // 使用较低的容器颜色
        shape = MaterialTheme.shapes.large
    )
}