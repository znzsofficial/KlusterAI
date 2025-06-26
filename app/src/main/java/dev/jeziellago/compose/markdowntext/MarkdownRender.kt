package dev.jeziellago.compose.markdowntext

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import coil3.ImageLoader
import dev.jeziellago.compose.markdowntext.plugins.core.MardownCorePlugin
import dev.jeziellago.compose.markdowntext.plugins.image.ImagesPlugin
import dev.jeziellago.compose.markdowntext.plugins.syntaxhighlight.SyntaxHighlightPlugin
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import androidx.core.graphics.drawable.toDrawable
import com.nekolaska.klusterai.plugins.LatexFencedCodeBlockPlugin

internal object MarkdownRender {

    fun create(
        context: Context,
        imageLoader: ImageLoader?,
        linkifyMask: Int,
        latexTextSizePx: Float,
        latexTextColor: Color,
        latexBackgroundColor: Color,
        enableSoftBreakAddsNewLine: Boolean,
        syntaxHighlightColor: Color,
        syntaxHighlightTextColor: Color,
        headingBreakColor: Color,
        enableUnderlineForLink: Boolean,
        beforeSetMarkdown: ((TextView, Spanned) -> Unit)? = null,
        afterSetMarkdown: ((TextView) -> Unit)? = null,
        onLinkClicked: ((String) -> Unit)? = null,
        style: TextStyle
    ): Markwon {
        val coilImageLoader = imageLoader ?: ImageLoader.Builder(context)
            .build()

        return Markwon.builderNoCore(context)
            .usePlugin(
                MardownCorePlugin(
                    syntaxHighlightColor.toArgb(),
                    syntaxHighlightTextColor.toArgb(),
                    enableUnderlineForLink,
                )
            )
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create(context, coilImageLoader))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(LinkifyPlugin.create(linkifyMask))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(JLatexMathPlugin.create(latexTextSizePx) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().backgroundProvider { latexBackgroundColor.toArgb().toDrawable() }
                builder.theme().textColor(latexTextColor.toArgb())
            })
            .usePlugin(
                LatexFencedCodeBlockPlugin(
                    latexTextSizePx,
                    latexTextColor.toArgb(),
                    latexBackgroundColor.toArgb()
                )
            )
            .apply {
                if (enableSoftBreakAddsNewLine) {
                    usePlugin(SoftBreakAddsNewLinePlugin.create())
                }
            }
            .usePlugin(SyntaxHighlightPlugin())
            .usePlugin(object : AbstractMarkwonPlugin() {

                override fun beforeSetText(textView: TextView, markdown: Spanned) {
                    beforeSetMarkdown?.invoke(textView, markdown)
                }

                override fun afterSetText(textView: TextView) {
                    afterSetMarkdown?.invoke(textView)
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    if (headingBreakColor == Color.Transparent) {
                        builder.headingBreakColor(1)
                    } else {
                        builder.headingBreakColor(headingBreakColor.toArgb())
                    }
                    builder.bulletWidth(style.fontSize.value.toInt())
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    // Setting [MarkwonConfiguration.Builder.linkResolver] overrides
                    // Markwon's default behaviour - see Markwon's [LinkResolverDef]
                    // and how it's used in [MarkwonConfiguration.Builder].
                    // Only use it if the client explicitly wants to handle link clicks.
                    onLinkClicked ?: return
                    builder.linkResolver { _, link ->
                        // handle individual clicks on Textview link
                        onLinkClicked.invoke(link)
                    }
                }
            })
            .build()
    }
}
