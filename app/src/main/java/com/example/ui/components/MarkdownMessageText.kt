package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MessageBlock {
    data class TextBlock(val text: String) : MessageBlock
    data class CodeBlock(val language: String, val code: String) : MessageBlock
}

@Composable
fun MarkdownMessageText(
    text: String,
    textColor: Color,
    codeContainerColor: Color,
    codeTextColor: Color,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val blocks = parseBlocks(text)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.TextBlock -> {
                    val styled = buildInlineMarkdown(
                        source = block.text,
                        inlineCodeBg = codeContainerColor,
                        inlineCodeColor = codeTextColor
                    )
                    Text(
                        text = styled,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                is MessageBlock.CodeBlock -> {
                    Surface(
                        color = codeContainerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" }.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { clipboard.setText(AnnotatedString(block.code)) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                color = codeTextColor,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseBlocks(raw: String): List<MessageBlock> {
    val lines = raw.lines()
    val blocks = mutableListOf<MessageBlock>()
    val textBuf = StringBuilder()
    val codeBuf = StringBuilder()
    var inCode = false
    var codeLang = ""

    fun flushText() {
        val content = textBuf.toString().trim()
        if (content.isNotEmpty()) {
            blocks.add(MessageBlock.TextBlock(content))
        }
        textBuf.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushText()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
                codeBuf.clear()
            } else {
                blocks.add(MessageBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
                inCode = false
                codeLang = ""
                codeBuf.clear()
            }
        } else if (inCode) {
            codeBuf.appendLine(line)
        } else {
            val formattedLine = when {
                trimmed.startsWith("### ") -> "**${trimmed.removePrefix("### ")}**"
                trimmed.startsWith("## ") -> "**${trimmed.removePrefix("## ")}**"
                trimmed.startsWith("# ") -> "**${trimmed.removePrefix("# ")}**"
                trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ")}"
                trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ")}"
                else -> line
            }
            textBuf.appendLine(formattedLine)
        }
    }

    if (inCode && codeBuf.isNotEmpty()) {
        blocks.add(MessageBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
    } else {
        flushText()
    }
    return blocks
}

private fun buildInlineMarkdown(
    source: String,
    inlineCodeBg: Color,
    inlineCodeColor: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) -> {
                val end = source.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(source.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(source[i])
                    i++
                }
            }
            source[i] == '`' -> {
                val end = source.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBg,
                            color = inlineCodeColor,
                            fontSize = 14.sp
                        )
                    ) {
                        append(" ${source.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(source[i])
                    i++
                }
            }
            else -> {
                append(source[i])
                i++
            }
        }
    }
}
