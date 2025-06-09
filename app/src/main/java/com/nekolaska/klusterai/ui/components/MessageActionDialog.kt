package com.nekolaska.klusterai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nekolaska.klusterai.R
import com.nekolaska.klusterai.data.MessageData


@Composable
fun MessageActionDialog(
    message: MessageData,
    onDismiss: () -> Unit,
    onCopy: (MessageData) -> Unit,
    onDelete: (MessageData) -> Unit,
    onRegenerate: (MessageData) -> Unit, // 重新生成回调
    onEdit: (MessageData) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "操作消息",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "角色: ${message.role.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "内容: \"${message.content.take(80)}${if (message.content.length > 80) "..." else ""}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                message.thinkContent?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "思考: \"${it.take(50)}${if (it.length > 50) "..." else ""}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                // 复制按钮
                TextButton(
                    onClick = { onCopy(message) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = "复制",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("复制消息")
                }
                Spacer(Modifier.height(4.dp))

                // 编辑按钮 (可以根据角色决定是否显示，例如不允许编辑系统消息)
                if (message.role == "user" || message.role == "assistant") {
                    TextButton(
                        onClick = { onEdit(message); /* onDismiss() is handled in ChatScreen */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("编辑消息")
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // 重新回复按钮 (仅对 user 和 assistant 消息有意义，但为简化，都显示)
                // 你可以根据 message.role 决定是否显示此按钮
                // if (message.role == "user" || message.role == "assistant")
                TextButton(
                    onClick = { onRegenerate(message) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "重新回复",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("重新回复")
                }
                Spacer(Modifier.height(4.dp))


                // 删除按钮
                TextButton(
                    onClick = { onDelete(message) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("删除消息")
                }
            }
        },
        confirmButton = { // 只保留一个取消按钮，因为操作按钮都在列表里了
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("取消")
                }
            }
        },
        dismissButton = null, // 移除单独的 DismissButton
        icon = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}