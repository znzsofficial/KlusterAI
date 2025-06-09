package com.nekolaska.klusterai

import android.content.Context
import android.net.Uri
import com.nekolaska.klusterai.ChatSessionRepository.generateNewSessionId
import com.nekolaska.klusterai.ChatSessionRepository.getChatHistoryDir
import com.nekolaska.klusterai.ChatSessionRepository.loadSessionMetas
import com.nekolaska.klusterai.ChatSessionRepository.saveSessionMetas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer // 用于序列化列表
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.use

object BackupRestoreManager {
    private const val METADATA_FILE_NAME = "metadata.json"
    private const val CHATS_DIR_IN_ZIP = "chats/"

    // --- 导出逻辑 ---
    suspend fun exportChatHistory(context: Context, outputUri: Uri): Boolean = withContext(
        Dispatchers.IO
    ) {
        try {
            val sessionMetas = loadSessionMetas(context)
            if (sessionMetas.isEmpty()) {
                // Toast.makeText(context, "没有会话可导出", Toast.LENGTH_SHORT).show() // 不能在IO线程show Toast
                // Log.i("Export", "没有会话可导出")
                return@withContext false // 或者抛出异常
            }

            context.contentResolver.openFileDescriptor(outputUri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        // 1. 添加 metadata.json
                        val metadataJsonString =
                            jsonParser.encodeToString(sessionMetas) // 使用已有的jsonParser
                        zos.putNextEntry(ZipEntry(METADATA_FILE_NAME))
                        zos.write(metadataJsonString.toByteArray())
                        zos.closeEntry()

                        // 2. 添加每个会话的消息文件
                        val chatHistoryDir = getChatHistoryDir(context) // 你之前定义的获取消息文件目录的函数
                        for (meta in sessionMetas) {
                            val sessionFile = File(chatHistoryDir, "session_${meta.id}.json")
                            if (sessionFile.exists()) {
                                zos.putNextEntry(ZipEntry("$CHATS_DIR_IN_ZIP${sessionFile.name}"))
                                FileInputStream(sessionFile).use { fis ->
                                    fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                        //Log.i("Export", "聊天记录导出成功到: $outputUri")
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            //Log.e("Export", "导出聊天记录失败: ${e.message}")
            false
        }
    }

    // --- 导入逻辑 ---
    suspend fun importChatHistory(
        context: Context,
        inputUri: Uri,
        conflictStrategy: ImportConflictStrategy = ImportConflictStrategy.CREATE_COPY
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(inputUri)?.use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    var importedMetas: List<SessionMeta>? = null
                    val importedMessagesMap =
                        mutableMapOf<String, String>() // sessionId to jsonString

                    while (entry != null) {
                        when {
                            entry.name == METADATA_FILE_NAME -> {
                                val reader = BufferedReader(InputStreamReader(zis))
                                val metadataJsonString = reader.readText()
                                importedMetas = jsonParser.decodeFromString(
                                    ListSerializer(SessionMeta.serializer()),
                                    metadataJsonString
                                )
                                //Log.d("Import", "成功解析 metadata.json")
                            }

                            entry.name.startsWith(CHATS_DIR_IN_ZIP) && entry.name.endsWith(".json") -> {
                                val sessionId = entry.name.substringAfterLast("session_")
                                    .substringBefore(".json")
                                val reader = BufferedReader(InputStreamReader(zis))
                                importedMessagesMap[sessionId] = reader.readText()
                                //Log.d("Import", "成功读取消息文件: ${entry.name}")
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }

                    if (importedMetas == null) {
                        //Log.e("Import", "导入失败: metadata.json 未找到或解析失败")
                        return@withContext false
                    }

                    // 开始合并/保存数据
                    val currentLocalMetas =
                        loadSessionMetas(context).associateBy { it.id }.toMutableMap()
                    var newSessionsAdded = false

                    for (importedMeta in importedMetas) {
                        val existingLocalMeta = currentLocalMetas[importedMeta.id]
                        val messagesJsonString = importedMessagesMap[importedMeta.id]

                        if (messagesJsonString == null) {
                            //Log.w("Import", "会话 ${importedMeta.title} (${importedMeta.id}) 的消息文件未在ZIP中找到，跳过。")
                            continue
                        }

                        when {
                            existingLocalMeta == null -> { // 本地不存在，直接添加
                                currentLocalMetas[importedMeta.id] = importedMeta
                                saveMessagesJsonStringDirectly(
                                    context,
                                    importedMeta.id,
                                    messagesJsonString
                                )
                                newSessionsAdded = true
                                //Log.i("Import", "导入新会话: ${importedMeta.title}")
                            }

                            conflictStrategy == ImportConflictStrategy.REPLACE -> {
                                currentLocalMetas[importedMeta.id] = importedMeta // 替换元数据
                                saveMessagesJsonStringDirectly(
                                    context,
                                    importedMeta.id,
                                    messagesJsonString
                                ) // 替换消息
                                newSessionsAdded = true // 标记有变化
                                //Log.i("Import", "替换会话: ${importedMeta.title}")
                            }

                            conflictStrategy == ImportConflictStrategy.CREATE_COPY -> {
                                val newId = generateNewSessionId() // 你需要这个函数
                                val copyMeta = importedMeta.copy(
                                    id = newId,
                                    title = "${importedMeta.title} (导入副本)"
                                )
                                currentLocalMetas[newId] = copyMeta
                                saveMessagesJsonStringDirectly(context, newId, messagesJsonString)
                                newSessionsAdded = true
                                //Log.i("Import", "创建副本: ${copyMeta.title}")
                            }

                            conflictStrategy == ImportConflictStrategy.SKIP -> {
                                // Log.i("Import", "跳过已存在会话: ${importedMeta.title}")
                                // 什么都不做
                            }
                        }
                    }
                    saveSessionMetas(
                        context,
                        currentLocalMetas.values.toList()
                            .sortedByDescending { it.lastModifiedTimestamp })
                    //Log.i("Import", "聊天记录导入完成。")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            //Log.e("Import", "导入聊天记录失败: ${e.message}")
            false
        }
    }

    // 辅助函数，直接保存从ZIP中读取的JSON字符串，避免再次序列化
    private fun saveMessagesJsonStringDirectly(
        context: Context,
        sessionId: String,
        messagesJsonString: String
    ) {
        val historyDir = getChatHistoryDir(context)
        val sessionFile = File(historyDir, "session_$sessionId.json")
        try {
            sessionFile.writeText(messagesJsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// 定义导入冲突策略
enum class ImportConflictStrategy {
    SKIP, REPLACE, CREATE_COPY
}