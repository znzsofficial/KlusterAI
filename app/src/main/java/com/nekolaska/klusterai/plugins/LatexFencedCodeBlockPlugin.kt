package com.nekolaska.klusterai.plugins

import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import androidx.annotation.ColorInt
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.FencedCodeBlock
import org.scilab.forge.jlatexmath.ParseException
import ru.noties.jlatexmath.JLatexMathDrawable

class LatexFencedCodeBlockPlugin(
    private val textSize: Float,
    @ColorInt private val textColor: Int,
    @ColorInt private val backgroundColor: Int
) : AbstractMarkwonPlugin() {

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(FencedCodeBlock::class.java) { visitor, node ->
            if (isLatexCodeBlock(node)) {
                renderLatexBlock(visitor, node)
            } else {
                renderNormalCodeBlock(visitor, node)
            }
        }
    }

    private fun isLatexCodeBlock(node: FencedCodeBlock): Boolean {
        val info = node.info?.lowercase()?.trim()
        return "latex" == info || "tex" == info
    }

    private fun renderLatexBlock(visitor: MarkwonVisitor, node: FencedCodeBlock) {
        try {
            val drawable = JLatexMathDrawable.builder(node.literal)
                .textSize(textSize)
                .color(textColor)
                .background(backgroundColor)
                .build()

            // 创建所有需要的 Span
            val imageSpan = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
            val alignmentSpan = AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER)

            // Drawable 创建成功，现在可以安全地修改 builder 了
            visitor.ensureNewLine()
            val length = visitor.length()
            visitor.builder().append('\u200b') // 插入占位符

            // 修正 #3：使用 setSpans 应用我们手动创建的 Span 数组
            visitor.setSpans(length, arrayOf(imageSpan, alignmentSpan))

            visitor.ensureNewLine()

        } catch (_: ParseException) {
            // 如果创建 Drawable 失败，直接降级渲染，此时 builder 还没有被修改
            renderNormalCodeBlock(visitor, node)
        }
    }

    private fun renderNormalCodeBlock(visitor: MarkwonVisitor, node: FencedCodeBlock) {
        visitor.ensureNewLine()
        val length = visitor.length()

        val code = visitor.configuration().syntaxHighlight().highlight(node.info, node.literal)
        visitor.builder().append(code)

        // 这是 setSpansForNodeOptional 的正确用法：
        // 让 Markwon 为 FencedCodeBlock 应用它自己的、已注册的样式（如背景色）
        visitor.setSpansForNodeOptional(node, length)

        visitor.ensureNewLine()
    }
}