package com.nekolaska.klusterai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nekolaska.klusterai.R

@Composable
fun InputRow(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    apiKey: String,
    modifier: Modifier = Modifier
) {
    var isInputExpanded by remember { mutableStateOf(false) }

    // 当展开时，TextField 的容器颜色可以略有不同，或者使用边框
    val textFieldContainerColor = if (isInputExpanded) {
        MaterialTheme.colorScheme.surfaceContainer // 比 surfaceBright 略暗或不同，以示区别
    } else {
        MaterialTheme.colorScheme.surfaceBright // 折叠时的颜色
    }

    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLowest, // 整个 InputRow 的背景
        modifier = modifier.animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            // verticalAlignment = if (isInputExpanded) Alignment.Bottom else Alignment.CenterVertically
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { isInputExpanded = !isInputExpanded },
                // modifier = Modifier.align(Alignment.CenterVertically) // 按钮始终垂直居中于行
            ) {
                Icon(
                    painter = painterResource(id = if (isInputExpanded) R.drawable.fold else R.drawable.expand),
                    contentDescription = if (isInputExpanded) "折叠输入框" else "展开输入框",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant // 给图标一个明确的颜色
                )
            }

            Spacer(modifier = Modifier.width(4.dp)) // 减小一点间距

            // 为了在展开时给 TextField 一个明确的区域感，可以用另一个 Surface 或 Card 包裹
            Surface( // 包裹 TextField 以应用不同的背景或边框
                modifier = Modifier.weight(1f),
                shape = if (isInputExpanded) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraLarge, // 展开时方一点，折叠时更圆
                color = textFieldContainerColor, // 使用根据状态变化的容器颜色
                border = if (isInputExpanded) BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                ) else null // 展开时加一个细边框
            ) {
                TextField(
                    value = userInput,
                    onValueChange = onUserInputChange,
                    placeholder = { Text("输入您的消息...") },
                    modifier = Modifier.fillMaxWidth(), // TextField 填满包裹它的 Surface
                    enabled = !isLoading,
                    singleLine = !isInputExpanded,
                    maxLines = if (isInputExpanded) 6 else 1,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (isInputExpanded) ImeAction.Default else ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isInputExpanded && !isLoading && userInput.isNotBlank() && apiKey.isNotBlank()) {
                                onSendClick()
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, // 保持无指示器
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        // TextField 内部的容器颜色也需要根据状态调整，或者设为透明以使用外部 Surface 的颜色
                        focusedContainerColor = Color.Transparent, // textFieldContainerColor,
                        unfocusedContainerColor = Color.Transparent, // textFieldUnfocusedContainerColor,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }


            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onSendClick,
                enabled = !isLoading && userInput.isNotBlank() && apiKey.isNotBlank(),
                // modifier = Modifier.align(Alignment.CenterVertically) // 按钮始终垂直居中于行
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (userInput.isNotBlank() && apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}