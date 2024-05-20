@file:JvmName("EncodingUtils")

package com.bugsnag.android.performance.internal

import java.util.UUID

private const val UUID_ID_STRING_LENGTH = 32
private const val LONG_ID_STRING_LENGTH = 16

public fun StringBuilder.appendHexPair(b: Int): StringBuilder {
    if (b < 16) append('0')
    return append(b.toString(16))
}

public fun StringBuilder.appendHexLong(value: Long): StringBuilder {
    ensureCapacity(length + LONG_ID_STRING_LENGTH)
    return appendHexPair(((value ushr 56) and 0xff).toInt())
        .appendHexPair(((value ushr 48) and 0xff).toInt())
        .appendHexPair(((value ushr 40) and 0xff).toInt())
        .appendHexPair(((value ushr 32) and 0xff).toInt())
        .appendHexPair(((value ushr 24) and 0xff).toInt())
        .appendHexPair(((value ushr 16) and 0xff).toInt())
        .appendHexPair(((value ushr 8) and 0xff).toInt())
        .appendHexPair((value and 0xff).toInt())
}

public fun StringBuilder.appendHexUUID(uuid: UUID): StringBuilder {
    ensureCapacity(length + UUID_ID_STRING_LENGTH)
    return appendHexLong(uuid.mostSignificantBits)
        .appendHexLong(uuid.leastSignificantBits)
}


internal fun UUID.toHexString(): String {
    return StringBuilder(UUID_ID_STRING_LENGTH)
        .appendHexUUID(this)
        .toString()
}

internal fun Long.toHexString(): String {
    return StringBuilder(LONG_ID_STRING_LENGTH)
        .appendHexLong(this)
        .toString()
}

internal fun StringBuilder.appendHexString(bytes: ByteArray): StringBuilder {
    ensureCapacity(length + (bytes.size * 2))
    bytes.forEach { appendHexPair(it.toInt() and 0xff) }
    return this
}
