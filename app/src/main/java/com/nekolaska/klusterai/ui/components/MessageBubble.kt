package com.nekolaska.klusterai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.nekolaska.klusterai.MessageData
import dev.jeziellago.compose.markdowntext.MarkdownText


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageData, onLongClick: (MessageData) -> Unit,
    isContentSelectable: Boolean
) {
    val alignment = if (message.role == "user") Alignment.End else Alignment.Start
    val containerColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.primaryContainer
        "assistant" -> MaterialTheme.colorScheme.secondaryContainer
        "system" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.onPrimaryContainer
        "assistant" -> MaterialTheme.colorScheme.onSecondaryContainer
        "system" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var isThinkExpanded by remember { mutableStateOf(false) } // 控制思考内容展开/折叠

    if (message.role == "system" && message.content.isBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current

        // containerSize 返回的是像素值，需要转换为 Dp
        val containerWidthPx = windowInfo.containerSize.width
        val containerWidthDp = remember(containerWidthPx, density) {
            with(density) { containerWidthPx.toDp() }
        }

        // 使用 containerWidthDp 进行布局计算
        val maxWidth = remember(containerWidthDp) { containerWidthDp * 0.8f }

        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = { /* 短按无操作，或未来可用于编辑等 */ },
                    onLongClick = { onLongClick(message) }
                ),
            color = containerColor,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text( // 角色名称
                    text = when (message.role) {
                        "user" -> "你"
                        "assistant" -> "助手"
                        "system" -> "系统提示"
                        else -> message.role.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp)) // 角色和内容之间的间距

                // 主要内容
                MarkdownText(
                    isTextSelectable = isContentSelectable,
                    markdown = message.content,
                    linkColor = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = contentColor,
                    ),
                    //style = MaterialTheme.typography.bodyLarge,
                    //color = contentColor
                )

                // 思考内容部分
                if (message.role != "system" && message.thinkContent != null && message.thinkContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton( // 展开/折叠思考内容的按钮
                        onClick = { isThinkExpanded = !isThinkExpanded },
                        modifier = Modifier.padding(vertical = 0.dp), // 减小按钮的垂直内边距
                        contentPadding = PaddingValues(
                            horizontal = 4.dp,
                            vertical = 2.dp
                        ) // 调整按钮内容内边距
                    ) {
                        Text(
                            if (isThinkExpanded) "隐藏思考" else "显示思考",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = if (isThinkExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isThinkExpanded) "隐藏思考" else "显示思考",
                            modifier = Modifier.size(16.dp),
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                    }

                    AnimatedVisibility(visible = isThinkExpanded) { // 可动画的可见性
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp) // 与按钮的间距
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // 更淡的背景
                                    RoundedCornerShape(4.dp) // 圆角
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp) // 内边距
                        ) {
                            SelectionContainer { // 允许复制思考内容
                                Text(
                                    text = message.thinkContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.85f) // 思考内容颜色可以略深一点
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}