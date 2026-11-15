package com.iank.im.peek.data.remote.common

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Base64

sealed interface JsonBodySegment {
    class RawBytes(val bytes: ByteArray) : JsonBodySegment {
        constructor(text: String) : this(text.toByteArray(Charsets.UTF_8))
    }

    data class Base64File(
        val file: File,
        val capturedLength: Long = file.length()
    ) : JsonBodySegment
}

class StreamingJsonBody(
    private val segments: List<JsonBodySegment>
) : RequestBody() {

    private val exactContentLength: Long = segments.sumOf { segment ->
        when (segment) {
            is JsonBodySegment.RawBytes -> segment.bytes.size.toLong()
            is JsonBodySegment.Base64File -> base64Length(segment.capturedLength)
        }
    }

    override fun contentType(): MediaType = CONTENT_TYPE_JSON

    override fun contentLength(): Long = exactContentLength

    override fun writeTo(sink: BufferedSink) {
        val rawOut = sink.outputStream()
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        for (segment in segments) {
            when (segment) {
                is JsonBodySegment.RawBytes -> {
                    sink.write(segment.bytes)
                }
                is JsonBodySegment.Base64File -> {
                    if (!segment.file.exists()) {
                        throw IOException("Image payload file missing during upload: ${segment.file.name}")
                    }
                    val actualLength = segment.file.length()
                    if (actualLength != segment.capturedLength) {
                        throw IOException("Image payload size changed during streaming: ${segment.file.name}")
                    }
                    val encoderStream = Base64.getEncoder().wrap(NonClosingOutputStream(rawOut))
                    try {
                        FileInputStream(segment.file).use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                encoderStream.write(buffer, 0, read)
                            }
                        }
                    } finally {
                        encoderStream.close()
                    }
                }
            }
        }
        sink.flush()
    }

    private class NonClosingOutputStream(
        private val delegate: OutputStream
    ) : OutputStream() {
        override fun write(b: Int) {
            delegate.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
        }

        override fun flush() {
            delegate.flush()
        }

        override fun close() {
            delegate.flush()
        }
    }

    companion object {
        private val CONTENT_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
        private const val STREAM_BUFFER_SIZE = 32 * 1024

        fun base64Length(rawByteLength: Long): Long {
            if (rawByteLength <= 0L) return 0L
            return ((rawByteLength + 2L) / 3L) * 4L
        }
    }
}
