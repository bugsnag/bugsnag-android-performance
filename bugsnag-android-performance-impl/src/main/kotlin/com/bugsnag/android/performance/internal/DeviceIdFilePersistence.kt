package com.bugsnag.android.performance.internal

import android.content.Context
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID

/**
 * This class is responsible for persisting and retrieving a device ID to a file.
 *
 * This class is made multi-process safe through the use of a [FileLock], and thread safe
 * through the use of a [ReadWriteLock] in [SynchronizedStreamableStore].
 *
 * This file mirrors the `DeviceIdFilePersistence` class in `bugsnag-android`.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DeviceIdFilePersistence(
    private val file: File,
    private val deviceIdGenerator: () -> UUID,
) {
    /**
     * Loads the device ID from its file system location.
     * If no value is present then a UUID will be generated and persisted.
     */
    public fun loadDeviceId(requestCreateIfDoesNotExist: Boolean): String? {
        return try {
            // optimistically read device ID without a lock - the majority of the time
            // the device ID will already be present so no synchronization is required.

            loadDeviceIdInternal()
                ?: return if (requestCreateIfDoesNotExist) persistNewDeviceUuid(deviceIdGenerator()) else null
        } catch (exc: Throwable) {
            Logger.w("Failed to load device ID", exc)
            null
        }
    }

    /**
     * Loads the device ID from the file.
     *
     * If the file has zero length it can't contain device ID, so reading will be skipped.
     */
    private fun loadDeviceIdInternal(): String? {
        if (file.length() > 0) {
            try {
                val obj = JSONObject(file.readText())
                return obj.getString(KEY_ID)
            } catch (exc: Throwable) {
                // catch AssertionError which can be thrown by JsonReader
                // on Android 8.0/8.1. see https://issuetracker.google.com/issues/79920590
                Logger.w("Failed to load device ID", exc)
            }
        }
        return null
    }

    /**
     * Write a new Device ID to the file.
     */
    private fun persistNewDeviceUuid(uuid: UUID): String? {
        return try {
            // acquire a FileLock to prevent Clients in different processes writing
            // to the same file concurrently
            file.outputStream().channel.use { channel ->
                persistNewDeviceIdWithLock(channel, uuid)
            }
        } catch (exc: IOException) {
            Logger.w("Failed to persist device ID", exc)
            null
        }
    }

    private fun persistNewDeviceIdWithLock(
        channel: FileChannel,
        uuid: UUID,
    ): String? {
        val lock = waitForFileLock(channel) ?: return null

        return try {
            // read the device ID again as it could have changed
            // between the last read and when the lock was acquired

            // the device ID may have changed between the last read
            // and acquiring the lock, so return the generated value
            loadDeviceIdInternal() ?: persistDeviceIdUnderLock(uuid, channel)
        } finally {
            lock.release()
        }
    }

    private fun persistDeviceIdUnderLock(
        uuid: UUID,
        channel: FileChannel,
    ): String {
        val id = uuid.toString()
        val json = JSONObject()
        json.put(KEY_ID, id)
        channel.write(ByteBuffer.wrap(json.toString().toByteArray()))
        return id
    }

    /**
     * Attempt to acquire a file lock. If [OverlappingFileLockException] is thrown
     * then the method will wait for 50ms then try again, for a maximum of 10 attempts.
     */
    private fun waitForFileLock(channel: FileChannel): FileLock? {
        repeat(MAX_FILE_LOCK_ATTEMPTS) {
            try {
                return channel.tryLock()
            } catch (exc: OverlappingFileLockException) {
                Thread.sleep(FILE_LOCK_WAIT_MS)
            }
        }
        return null
    }

    public companion object {
        private const val MAX_FILE_LOCK_ATTEMPTS = 20
        private const val FILE_LOCK_WAIT_MS = 25L
        private const val KEY_ID = "id"

        public fun forContext(context: Context): DeviceIdFilePersistence =
            DeviceIdFilePersistence(File(context.filesDir, "device-id"), UUID::randomUUID)
    }
}

/**
 * Load the device.id into a given [DefaultAttributeSource], this is a `Runnable` so that it can
 * be used as a [Worker] startup task.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LoadDeviceId(
    private val context: Context,
    private val resourceAttributes: Attributes,
) : Runnable {
    override fun run() {
        val deviceIdFilePersistence = DeviceIdFilePersistence.forContext(context)
        val deviceId = deviceIdFilePersistence.loadDeviceId(true)

        // no synchronization required as all startup tasks are run on the worker thread
        resourceAttributes["device.id"] = deviceId
    }
}
