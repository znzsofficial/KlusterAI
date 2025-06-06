package com.nekolaska.klusterai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorMessageDisplay(
    errorMessage: String?, // 可空的错误信息字符串
    onDismiss: () -> Unit, // 当用户点击关闭按钮时调用
    modifier: Modifier = Modifier // 允许外部传入 Modifier
) {
    // 只有当 errorMessage 不为 null 且不为空白时才显示
    AnimatedVisibility(
        visible = !errorMessage.isNullOrBlank(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier // 将外部 modifier 应用到 AnimatedVisibility
    ) {
        // 使用 Surface 包裹以应用背景和形状
        Surface(
            modifier = Modifier // Surface 内部自己处理 fillMaxWidth 和 padding
                .fillMaxWidth()
                .padding(vertical = 8.dp), // 整个错误组件的垂直外边距
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 2.dp // 可选的阴影效果
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp), // 内容的内边距
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // 让关闭按钮在最右边
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning, // 或者你选择的其他错误/警告图标
                    contentDescription = "错误提示图标",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp)
                )
                Text(
                    text = errorMessage ?: "", // 安全地使用 errorMessage
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f) // 让文本占据可用空间
                )
                IconButton(
                    onClick = onDismiss, // 调用传入的 onDismiss 回调
                    modifier = Modifier.size(32.dp) // 调整关闭按钮大小和点击区域
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭错误提示",
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}