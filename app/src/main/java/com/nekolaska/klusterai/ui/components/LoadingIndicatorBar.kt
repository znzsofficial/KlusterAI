package com.nekolaska.klusterai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 一个提示条，显示“助手正在回复...”并提供一个按钮来显示实时回复对话框。
 *
 * @param onShowStreamingDialog 当点击“显示实时回复”按钮时调用。
 * @param modifier Modifier 应用于此组件的根 Row。
 */
@Composable
fun AssistantLoadingIndicatorBar(
    modifier: Modifier = Modifier,
    onShowStreamingDialog: () -> Unit
) {
    Row(
        modifier = modifier // 应用外部传入的 Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "助手正在回复...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant // 确保文本颜色在 surfaceVariant 上可读
        )
        TextButton(onClick = onShowStreamingDialog) {
            Text("显示实时回复")
        }
    }
}

/**
 * 一个指示器，显示“正在进行可靠性审查...”。
 *
 * @param modifier Modifier 应用于此组件的根 Row。
 */
@Composable
fun VerificationInProgressIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier // 应用外部传入的 Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)) // 使用三级容器颜色作为背景
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer, // 指示器颜色与背景协调
            strokeWidth = 2.dp // 使指示器更细一点
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "正在进行可靠性审查...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer // 文本颜色与背景协调
        )
    }
}
