/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.core.capabilities

import com.dot.gallery.feature_node.domain.model.Media
import kotlinx.serialization.Serializable

@Serializable
enum class RemoteNameConflictPolicy {
    KEEP_BOTH
}

@Serializable
enum class RemoteAlbumCopyState {
    COPIED,
    ALREADY_PRESENT,
    ATTACH_PENDING,
    FAILED
}

@Serializable
data class RemoteAlbumCopyResult(
    val state: RemoteAlbumCopyState,
    val remoteId: String? = null,
    val message: String = "",
    val retryable: Boolean = false
) {
    val isComplete: Boolean
        get() = state == RemoteAlbumCopyState.COPIED ||
            state == RemoteAlbumCopyState.ALREADY_PRESENT
}

fun remoteCopyFileName(original: String, copyNumber: Int): String {
    require(copyNumber > 0)
    val dot = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
    return original.substring(0, dot) + " ($copyNumber)" + original.substring(dot)
}

fun remoteAlbumFilePath(remoteAlbumId: String, fileName: String): String {
    val album = remoteAlbumId.trim('/')
    require(album.isNotBlank() && album.split('/').none { it == "." || it == ".." })
    require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName)
    return "$album/$fileName"
}

interface RemoteAlbumWriteProvider : SyncCapableProvider {
    suspend fun copyToAlbum(
        media: Media,
        remoteAlbumId: String,
        conflictPolicy: RemoteNameConflictPolicy = RemoteNameConflictPolicy.KEEP_BOTH,
        checksum: String? = null,
        continuationRemoteId: String? = null
    ): RemoteAlbumCopyResult
}
