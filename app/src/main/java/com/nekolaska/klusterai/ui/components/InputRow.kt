package com.nekolaska.klusterai.ui.components


import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.KeyboardArrowDown // 用于展开
import androidx.compose.material.icons.filled.KeyboardArrowUp   // 用于折叠
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun InputRow(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    apiKey: String,
    modifier: Modifier = Modifier // 允许外部传入 Modifier
) {
    var isInputExpanded by remember { mutableStateOf(false) } // 追踪展开状态

    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.animateContentSize() // 添加动画效果
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = if (isInputExpanded) Alignment.Top else Alignment.CenterVertically // 展开时顶部对齐
        ) {
            // 展开/折叠按钮
            IconButton(
                onClick = { isInputExpanded = !isInputExpanded },
                modifier = Modifier.align(if (isInputExpanded) Alignment.Bottom else Alignment.CenterVertically) // 确保按钮位置合理
            ) {
                Icon(
                    imageVector = if (isInputExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isInputExpanded) "折叠输入框" else "展开输入框"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = userInput,
                onValueChange = {
                    if (it.length <= if (isInputExpanded) 5000 else 500) onUserInputChange(it)
                },
                placeholder = { Text("输入您的消息...") },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                singleLine = !isInputExpanded, // 根据状态切换单行/多行
                maxLines = if (isInputExpanded) 6 else 1, // 展开时最多6行，折叠时1行
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences, // 句子首字母大写
                    imeAction = if (isInputExpanded) ImeAction.Default else ImeAction.Send // 多行时用默认，单行时用发送
                ),
                keyboardActions = KeyboardActions(
                    onSend = { // 仅在单行模式且非加载中时，通过键盘发送
                        if (!isInputExpanded && !isLoading && userInput.isNotBlank() && apiKey.isNotBlank()) {
                            onSendClick()
                        }
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            IconButton(
                onClick = onSendClick,
                enabled = !isLoading && userInput.isNotBlank() && apiKey.isNotBlank(),
                modifier = Modifier.align(if (isInputExpanded) Alignment.Bottom else Alignment.CenterVertically) // 确保按钮位置合理
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}