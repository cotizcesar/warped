package com.warped.data.local.inference

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class GgufMetadata(
    val name: String = "Unknown",
    val architecture: String = "Unknown",
    val quantization: String = "Unknown",
    val parameterCount: String = "Unknown",
    val contextLength: Int = 2048
)

object GgufMetadataParser {

    private const val MAX_STRING_LENGTH = 4096

    fun parse(file: File): Result<GgufMetadata> {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(4)
                raf.read(header)
                val magic = String(header, StandardCharsets.US_ASCII)
                if (magic != "GGUF") {
                    return Result.failure(IllegalArgumentException("Not a valid GGUF file"))
                }

                val versionBuf = ByteArray(4)
                raf.read(versionBuf)
                val version = ByteBuffer.wrap(versionBuf).order(ByteOrder.LITTLE_ENDIAN).int

                val tensorCountBuf = ByteArray(8)
                raf.read(tensorCountBuf)
                val tensorCount = ByteBuffer.wrap(tensorCountBuf).order(ByteOrder.LITTLE_ENDIAN).long

                val metadataKvCountBuf = ByteArray(8)
                raf.read(metadataKvCountBuf)
                val kvCount = ByteBuffer.wrap(metadataKvCountBuf).order(ByteOrder.LITTLE_ENDIAN).long

                var name = "Unknown"
                var architecture = "Unknown"
                var quantization = "Unknown"
                var parameterCount = "Unknown"
                var contextLength = 2048

                for (i in 0 until minOf(kvCount, 100)) {
                    try {
                        val keyLenBuf = ByteArray(8)
                        raf.read(keyLenBuf)
                        val keyLen = ByteBuffer.wrap(keyLenBuf).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
                        if (keyLen < 0 || keyLen > MAX_STRING_LENGTH) break

                        val keyBytes = ByteArray(keyLen)
                        raf.read(keyBytes)
                        val key = String(keyBytes, StandardCharsets.UTF_8)

                        val valueTypeBuf = ByteArray(4)
                        raf.read(valueTypeBuf)
                        val valueType = ByteBuffer.wrap(valueTypeBuf).order(ByteOrder.LITTLE_ENDIAN).int

                        when {
                            key == "general.name" && valueType == 8 -> {
                                val strLenBuf = ByteArray(8)
                                raf.read(strLenBuf)
                                val strLen = ByteBuffer.wrap(strLenBuf).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
                                if (strLen < 0 || strLen > MAX_STRING_LENGTH) break
                                val strBytes = ByteArray(strLen)
                                raf.read(strBytes)
                                name = String(strBytes, StandardCharsets.UTF_8)
                            }
                            key == "general.architecture" && valueType == 8 -> {
                                val strLenBuf = ByteArray(8)
                                raf.read(strLenBuf)
                                val strLen = ByteBuffer.wrap(strLenBuf).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
                                if (strLen < 0 || strLen > MAX_STRING_LENGTH) break
                                val strBytes = ByteArray(strLen)
                                raf.read(strBytes)
                                architecture = String(strBytes, StandardCharsets.UTF_8)
                            }
                            key == "general.file_type" && valueType == 4 -> {
                                val fileTypeBuf = ByteArray(4)
                                raf.read(fileTypeBuf)
                                val fileType = ByteBuffer.wrap(fileTypeBuf).order(ByteOrder.LITTLE_ENDIAN).int
                                quantization = mapQuantization(fileType)
                            }
                            key == "general.quantization_version" && valueType == 4 -> {
                                val qvBuf = ByteArray(4)
                                raf.read(qvBuf)
                            }
                            key.contains("block_count") && valueType == 4 -> {
                                val countBuf = ByteArray(4)
                                raf.read(countBuf)
                                val count = ByteBuffer.wrap(countBuf).order(ByteOrder.LITTLE_ENDIAN).int
                                parameterCount = "${count / 1000.0}B"
                            }
                            key == "llama.context_length" && valueType == 4 -> {
                                val ctxBuf = ByteArray(4)
                                raf.read(ctxBuf)
                                contextLength = ByteBuffer.wrap(ctxBuf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            else -> {
                                skipValue(raf, valueType)
                            }
                        }
                    } catch (e: Exception) {
                        break
                    }
                }

                Result.success(GgufMetadata(
                    name = name,
                    architecture = architecture,
                    quantization = quantization,
                    parameterCount = parameterCount,
                    contextLength = contextLength
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapQuantization(fileType: Int): String = when (fileType) {
        0 -> "F32"
        1 -> "F16"
        2 -> "Q4_0"
        3 -> "Q4_1"
        6 -> "Q5_0"
        7 -> "Q5_1"
        8 -> "Q8_0"
        10 -> "Q2_K"
        12 -> "Q3_K"
        14 -> "Q4_K"
        16 -> "Q5_K"
        17 -> "Q6_K"
        else -> "Q${fileType}"
    }

    private fun skipValue(raf: RandomAccessFile, type: Int) {
        when (type) {
            4 -> raf.skipBytes(4)
            6 -> raf.skipBytes(4)
            8 -> {
                val lenBuf = ByteArray(8)
                raf.read(lenBuf)
                val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).long
                if (len < 0 || len > MAX_STRING_LENGTH) return
                raf.skipBytes(len.toInt())
            }
            9 -> raf.skipBytes(4)
            else -> raf.skipBytes(8)
        }
    }
}
