/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import android.content.Context
import androidx.core.net.toUri
import com.dot.gallery.cloud.data.dao.CloudMediaLocalState
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the MediaStore row id from a stored `localCopyPath`. The download pipeline
 * stores canonical `content://media/<volume>/file/<id>` URIs, so the id is always the
 * last path segment. Returns null for anything else (legacy file:// paths, blanks).
 * Kept free of android.net.Uri so it stays unit-testable on the JVM.
 */
internal fun localCopyMediaStoreId(localCopyPath: String): Long? =
    localCopyPath.substringAfterLast('/').toLongOrNull()

/**
 * Deletes the MediaStore copies referenced by [states]. Callers must only pass rows
 * selected with `appLocalCopy = 1` — upload paths also populate `localCopyPath` with the
 * user's ORIGINAL file, so filtering on the app-owned flag is what makes deletion safe.
 * App-contributed MediaStore entries need no user consent to delete. Failures are logged
 * per item and never abort the batch.
 */
internal suspend fun deleteAppLocalCopies(
    context: Context,
    states: List<CloudMediaLocalState>
): Int = withContext(Dispatchers.IO) {
    var deleted = 0
    for (state in states) {
        val uriString = state.localCopyPath
        if (uriString.isBlank()) continue
        try {
            if (context.contentResolver.delete(uriString.toUri(), null, null) > 0) {
                deleted++
                printDebug("CloudLocalCopies: deleted local copy for ${state.remoteId}")
            } else {
                printDebug("CloudLocalCopies: local copy already gone for ${state.remoteId}")
            }
        } catch (e: Exception) {
            printDebug("CloudLocalCopies: failed to delete local copy for ${state.remoteId}: ${e.message}")
        }
    }
    deleted
}
