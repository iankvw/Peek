package com.iank.im.peek.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iank.im.peek.R

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class OrderedItem(val number: String, val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    textColor: Color,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val quoteBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val linkColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = buildInlineAnnotatedString(block.text, codeBackground, linkColor),
                        style = style.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInlineAnnotatedString(block.text, codeBackground, linkColor),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildInlineAnnotatedString(block.text, codeBackground, linkColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.OrderedItem -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = buildInlineAnnotatedString(block.text, codeBackground, linkColor),
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp)
                                .background(quoteBarColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = buildInlineAnnotatedString(block.text, codeBackground, linkColor),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = textColor.copy(alpha = 0.88f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        color = Color(0xFF131314),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E1F20))
                                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC4C7C5)
                                )
                                IconButton(
                                    onClick = { onCopyCode(block.code) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.cd_copy_code_block),
                                        tint = Color(0xFFE3E3E3),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color(0xFFE3E3E3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }
                is MarkdownBlock.Table -> {
                    MarkdownTable(
                        headers = block.headers,
                        rows = block.rows,
                        textColor = textColor,
                        codeBackground = codeBackground,
                        linkColor = linkColor
                    )
                }
                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = textColor.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color,
    codeBackground: Color,
    linkColor: Color
) {
    val borderColor = textColor.copy(alpha = 0.25f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.background(textColor.copy(alpha = 0.08f))
        ) {
            headers.forEach { header ->
                Text(
                    text = buildInlineAnnotatedString(header, codeBackground, linkColor),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                    modifier = Modifier
                        .width(128.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        rows.forEach { row ->
            HorizontalDivider(color = borderColor)
            Row {
                headers.indices.forEach { colIdx ->
                    val cellText = row.getOrElse(colIdx) { "" }
                    Text(
                        text = buildInlineAnnotatedString(cellText, codeBackground, linkColor),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        modifier = Modifier
                            .width(128.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n"))
            paragraphBuffer.clear()
        }
    }

    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()

        // Fenced code block
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            idx++
            while (idx < lines.size && !lines[idx].trim().startsWith("```")) {
                codeLines += lines[idx]
                idx++
            }
            blocks += MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n"))
            idx++
            continue
        }

        // Blank line
        if (trimmed.isEmpty()) {
            flushParagraph()
            idx++
            continue
        }

        // Horizontal rule
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks += MarkdownBlock.Divider
            idx++
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,4})\\s+(.*)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2]
            )
            idx++
            continue
        }

        // Table detection
        if (trimmed.startsWith("|") && idx + 1 < lines.size && isTableSeparator(lines[idx + 1])) {
            flushParagraph()
            val headers = splitTableRow(trimmed)
            idx += 2
            val tableRows = mutableListOf<List<String>>()
            while (idx < lines.size && lines[idx].trim().startsWith("|")) {
                tableRows += splitTableRow(lines[idx].trim())
                idx++
            }
            blocks += MarkdownBlock.Table(headers, tableRows)
            continue
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (idx < lines.size && lines[idx].trim().startsWith(">")) {
                quoteLines += lines[idx].trim().removePrefix(">").trimStart()
                idx++
            }
            blocks += MarkdownBlock.BlockQuote(quoteLines.joinToString("\n"))
            continue
        }

        // Bullet list
        val bulletMatch = Regex("^[-*]\\s+(.*)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.BulletItem(bulletMatch.groupValues[1])
            idx++
            continue
        }

        // Ordered list
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)$").find(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.OrderedItem(
                number = orderedMatch.groupValues[1],
                text = orderedMatch.groupValues[2]
            )
            idx++
            continue
        }

        paragraphBuffer += line
        idx++
    }
    flushParagraph()
    return blocks
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return false
    return trimmed.replace("|", "").replace("-", "").replace(":", "").isBlank()
}

private fun splitTableRow(row: String): List<String> {
    return row.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { it.trim() }
}

private fun buildInlineAnnotatedString(
    text: String,
    codeBg: Color,
    linkColor: Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            fontSize = 13.sp
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end > i + 3) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1 && !text.startsWith("**", i)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("[", i) -> {
                val closeBracket = text.indexOf(']', i + 1)
                val hasParens = closeBracket > i && closeBracket + 1 < text.length && text[closeBracket + 1] == '('
                val closeParen = if (hasParens) text.indexOf(')', closeBracket + 2) else -1
                if (closeParen > closeBracket + 2) {
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                    } else {
                        append(label)
                    }
                    i = closeParen + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
