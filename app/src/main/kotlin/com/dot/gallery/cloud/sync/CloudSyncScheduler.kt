/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.sync

import androidx.work.WorkManager
import com.dot.gallery.cloud.data.dao.CloudServerConfigDao
import com.dot.gallery.cloud.data.entity.CloudServerConfigEntity
import com.dot.gallery.feature_node.presentation.util.printDebug
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val configDao: CloudServerConfigDao
) {

    suspend fun reconcile() {
        val plan = cloudSyncSchedulePlan(configDao.getAll().first())
        if (plan == null) {
            printDebug("CloudSyncScheduler: No sync-enabled configs, canceling workers")
            CloudSyncWorker.cancel(workManager)
            CloudUploadWorker.cancel(workManager)
            CloudDownloadWorker.cancel(workManager)
            return
        }

        printDebug(
            "CloudSyncScheduler: Scheduling sync + upload " +
                "(interval=${plan.intervalMinutes}, syncWifiOnly=${plan.syncWifiOnly}, " +
                "uploadWifiOnly=${plan.uploadWifiOnly}, " +
                "downloadWifiOnly=${plan.downloadWifiOnly})"
        )
        CloudSyncWorker.schedule(
            workManager,
            intervalMinutes = plan.intervalMinutes,
            wifiOnly = plan.syncWifiOnly
        )
        CloudUploadWorker.schedule(
            workManager,
            intervalMinutes = plan.intervalMinutes,
            wifiOnly = plan.uploadWifiOnly
        )
        if (plan.downloadWifiOnly != null) {
            CloudDownloadWorker.schedule(
                workManager,
                intervalMinutes = plan.intervalMinutes,
                wifiOnly = plan.downloadWifiOnly
            )
        } else {
            CloudDownloadWorker.cancel(workManager)
        }
    }
}

internal data class CloudSyncSchedulePlan(
    val intervalMinutes: Long,
    val syncWifiOnly: Boolean,
    val uploadWifiOnly: Boolean,
    /** Non-null when at least one account enabled remote downloads; the value is the shared wifiOnly constraint. */
    val downloadWifiOnly: Boolean?
)

/**
 * Single source of truth for whether cloud sync and backup may use a metered network.
 * The account's Wi-Fi-only switch governs remote sync. Uploads additionally honour the
 * per-media cellular overrides exposed by Backup Options.
 */
internal object CloudCellularPolicy {
    fun allowsSync(config: CloudServerConfigEntity): Boolean = !config.wifiOnly

    fun allowsUpload(config: CloudServerConfigEntity, mimeType: String): Boolean =
        !config.wifiOnly || if (mimeType.startsWith("video/")) {
            config.cellularVideos
        } else {
            config.cellularPhotos
        }

    fun allowsAnyUpload(config: CloudServerConfigEntity): Boolean =
        !config.wifiOnly || config.cellularPhotos || config.cellularVideos
}

internal fun cloudSyncSchedulePlan(
    configs: List<CloudServerConfigEntity>
): CloudSyncSchedulePlan? {
    val syncConfigs = configs.filter { it.isActive && it.syncEnabled }
    val downloadConfigs = configs.filter { it.isActive && it.downloadRemoteEnabled }
    if (syncConfigs.isEmpty() && downloadConfigs.isEmpty()) return null
    // Shared WorkManager constraints must be permissive enough for every account. Each worker
    // applies the same policy again per account (and, for uploads, per media type).
    return CloudSyncSchedulePlan(
        intervalMinutes = (syncConfigs + downloadConfigs)
            .minOf { it.syncIntervalMinutes }.toLong().coerceAtLeast(15L),
        syncWifiOnly = syncConfigs.none(CloudCellularPolicy::allowsSync),
        uploadWifiOnly = syncConfigs.none(CloudCellularPolicy::allowsAnyUpload),
        downloadWifiOnly = if (downloadConfigs.isEmpty()) null
            else downloadConfigs.none(CloudCellularPolicy::allowsSync)
    )
}

internal fun cloudSyncScheduleChanged(
    oldConfig: CloudServerConfigEntity,
    newConfig: CloudServerConfigEntity
): Boolean = oldConfig.isActive != newConfig.isActive ||
    oldConfig.syncEnabled != newConfig.syncEnabled ||
    oldConfig.syncIntervalMinutes != newConfig.syncIntervalMinutes ||
    oldConfig.wifiOnly != newConfig.wifiOnly ||
    oldConfig.cellularPhotos != newConfig.cellularPhotos ||
    oldConfig.cellularVideos != newConfig.cellularVideos ||
    oldConfig.requireCharging != newConfig.requireCharging ||
    oldConfig.syncAlbums != newConfig.syncAlbums ||
    oldConfig.downloadRemoteEnabled != newConfig.downloadRemoteEnabled
