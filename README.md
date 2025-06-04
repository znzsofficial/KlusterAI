# Kluster AI Chat - Android Compose 对话应用

这是一个基于 Android Jetpack Compose 构建的、对接 Kluster.ai LLM API 的对话软件。用户可以通过该应用与 Kluster.ai 提供的多种大语言模型进行交互。

## 功能特性

*   **多会话管理**:
    *   支持创建、保存、加载和删除多个独立的聊天会话。
    *   每个会话可以拥有自己的标题，方便用户管理和区分。
    *   会话标题可以根据第一条用户消息自动建议，并允许用户手动修改。
*   **手动保存机制**: 用户可以自主选择何时保存当前聊天记录，避免自动保存带来的干扰。
*   **模型选择**:
    *   支持在多个 Kluster.ai 提供的语言模型之间进行切换。
    *   模型选择和系统提示与每个会话关联并保存。
*   **自定义系统提示 (System Prompt)**:
    *   允许用户为每个会话设置特定的系统提示，以引导模型的行为和回复风格。
*   **流式响应**: 实时显示模型生成的回复内容，提升用户体验。
*   **消息操作**:
    *   支持复制消息内容。
    *   支持删除单条消息。
    *   支持针对用户或助手消息重新生成回复。
*   **思考过程展示**: 对于包含 `<think>` 标签的助手回复，支持折叠和展开思考内容。
*   **设置持久化**: API Key、全局默认模型、全局默认系统提示以及所有会话数据均通过 SharedPreferences 和 JSON 文件进行持久化存储。
*   **简洁的 Material Design 3 界面**: 采用现代 Android 设计风格，提供流畅的用户体验。

## 技术栈

*   **UI**: Jetpack Compose (Android 的现代声明式 UI 工具包)
*   **HTTP Client**: OkHttp
*   **JSON Serialization**: kotlinx.serialization
*   **MarkDown**: [compose-markdown](https://github.com/jeziellago/compose-markdown)
*   **语言**: Kotlin
*   **构建工具**: Gradle

## 鸣谢与说明

本项目的大部分UI布局代码、Composable 函数结构以及部分交互逻辑的初步实现，是在 **Gemini 2.5 Pro Preview 05-06 (AiStudio)** 的协助下完成的。

本人仅编写了基础的API对接对话逻辑，以及对 Gemini 代码的优化和修复。

## 如何构建

1.  克隆本仓库: `git clone https://github.com/你的用户名/你的仓库名.git`
2.  使用 Android Studio 打开项目。
3.  在 `DefaultConfig.kt` 中，找到 `DEFAULT_API_KEY_FALLBACK` 常量，并替换为你自己的 Kluster.ai API Key，或者在应用首次运行时通过设置对话框输入。
    ```kotlin
    // const val DEFAULT_API_KEY_FALLBACK = "YOUR_DEFAULT_API_KEY_HERE" // 替换为你的 Key
    ```
4.  构建并运行项目。

## 未来可能的改进

*   [ ] 迁移到 Room 数据库以获得更好的数据管理和性能。
*   [ ] 实现会话内消息搜索功能。
*   [x] 支持 Markdown 渲染回复内容。
*   [ ] 优化长聊天记录的加载性能。
*   [ ] 添加更多自定义主题选项。
*   [ ] 支持自定义兼容 OpenAI API 。

## 贡献

欢迎提出 Issue 或 Pull Request。

## 许可证

本项目采用 [MIT 许可证](LICENSE.txt)