/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared MediaStore writer for cloud downloads. Used by the manual "Download" action
 * (`MediaHandlerImpl.downloadCloudMedia`) and by `CloudDownloadWorker` for automatic
 * remote -> local sync, so both paths produce identical MediaStore rows.
 */
object CloudMediaStoreWriter {

    /**
     * @param displayName file name shown in MediaStore (remote label).
     * @param mimeType remote MIME type — decides the Images vs Video collection.
     * @param relativeSubPath sub-path appended to `Pictures/` or `Movies/`
     *   (e.g. `Cloud` for manual downloads, `Album/Sub` when mirroring remote folders,
     *   or the provider display name as a fallback root).
     * @param takenTimestamp remote capture time in epoch millis; written to DATE_TAKEN so
     *   the downloaded file sorts where it was shot, not when it was fetched.
     */
    data class Request(
        val displayName: String,
        val mimeType: String,
        val relativeSubPath: String = "Cloud",
        val takenTimestamp: Long? = null
    )

    /**
     * Copies [source] (typically a `file://` cache URI from `downloadAsset`) into a new
     * MediaStore row and returns its content URI, or null on failure. A failed write
     * cleans up the pending row so no phantom entries are left behind.
     */
    suspend fun write(context: Context, source: Uri, request: Request): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val isVideo = request.mimeType.startsWith("video/")
            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val relativePath = if (isVideo)
                Environment.DIRECTORY_MOVIES + "/" + request.relativeSubPath.trim('/')
            else
                Environment.DIRECTORY_PICTURES + "/" + request.relativeSubPath.trim('/')

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, request.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, request.mimeType)
                request.takenTimestamp?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertUri = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openOutputStream(insertUri)?.use { output ->
                    resolver.openInputStream(source)?.use { input ->
                        input.copyTo(output)
                    }
                } ?: throw java.io.IOException("Could not open download streams")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(insertUri, values, null, null)
                }
                insertUri
            } catch (e: Exception) {
                runCatching { resolver.delete(insertUri, null, null) }
                null
            }
        }

    /**
     * Best-effort delete of the download source. Cache files are plain files for the
     * providers today, but keep the content-resolver attempt for content:// sources.
     */
    fun deleteSource(context: Context, source: Uri) {
        try {
            context.contentResolver.delete(source, null, null)
        } catch (_: Exception) {
            java.io.File(source.path ?: return).delete()
        }
    }
}
