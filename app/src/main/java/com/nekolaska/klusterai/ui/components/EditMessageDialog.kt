package com.nekolaska.klusterai.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMessageDialog(
    initialContent: String,
    onSave: (newContent: String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(
        TextFieldValue(
            initialContent,
            selection = TextRange(initialContent.length)
        )
    ) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 300.dp) // 允许多行编辑
                    .focusRequester(focusRequester),
                label = { Text("消息内容") },
                maxLines = 10, // 限制最大行数
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done // 使用 Done 而不是 Send
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (textFieldValue.text.isNotBlank()) {
                        onSave(textFieldValue.text)
                    } else {
                        // Optionally show a toast or error if content is blank
                    }
                    keyboardController?.hide() // 隐藏键盘
                })
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (textFieldValue.text.isNotBlank()) {
                        onSave(textFieldValue.text)
                    }
                },
                enabled = textFieldValue.text.isNotBlank() // 只有当有内容时才能保存
            ) {
                Text("保存更改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
    LaunchedEffect(Unit) { // 对话框出现时请求焦点
        focusRequester.requestFocus()
    }
}