@file:JvmName("EncodingUtils")

package com.bugsnag.android.performance.internal

import java.util.UUID

private const val UUID_ID_STRING_LENGTH = 32
private const val LONG_ID_STRING_LENGTH = 16

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.appendHexPair(b: Int): StringBuilder {
    if (b < 16) append('0')
    return append(b.toString(16))
}

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.appendHexLong(value: Long): StringBuilder {
    return appendHexPair(((value ushr 56) and 0xff).toInt())
        .appendHexPair(((value ushr 48) and 0xff).toInt())
        .appendHexPair(((value ushr 40) and 0xff).toInt())
        .appendHexPair(((value ushr 32) and 0xff).toInt())
        .appendHexPair(((value ushr 24) and 0xff).toInt())
        .appendHexPair(((value ushr 16) and 0xff).toInt())
        .appendHexPair(((value ushr 8) and 0xff).toInt())
        .appendHexPair((value and 0xff).toInt())
}

internal fun UUID.toHexString(): String {
    return StringBuilder(UUID_ID_STRING_LENGTH)
        .appendHexLong(mostSignificantBits)
        .appendHexLong(leastSignificantBits)
        .toString()
}

internal fun Long.toHexString(): String {
    return StringBuilder(LONG_ID_STRING_LENGTH)
        .appendHexLong(this)
        .toString()
}

internal fun ByteArray.toHexString(): String {
    val buffer = StringBuilder(size * 2)
    forEach { buffer.appendHexPair(it.toInt() and 0xff) }
    return buffer.toString()
}
