/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.local

import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import androidx.core.net.toUri
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.DetectedFaceDao
import com.dot.gallery.cloud.data.dao.PersonDao
import com.dot.gallery.cloud.data.entity.FaceExclusionEntity
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.core.Resource
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, on-device people provider backed by the [PersonDao]/[DetectedFaceDao] tables that the
 * [com.dot.gallery.core.workers.FaceIndexerWorker] populates. Available whenever the face
 * detector model is installed.
 */
@Singleton
class LocalPeopleProvider @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: DetectedFaceDao,
    private val cloudMediaDao: CloudMediaDao,
    private val mediaRepository: MediaRepository,
    private val modelManager: ModelManager
) : LocalCapabilityProvider(), PeopleCapableProvider {

    override val providerType: ProviderType = ProviderType.LOCAL_PEOPLE
    override val displayName: String = ProviderType.LOCAL_PEOPLE.displayName
    override val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.PEOPLE)

    override suspend fun initialize() { /* No eager model load; sessions are created lazily. */ }

    override fun release() { }

    override val isAvailable: Boolean
        get() = modelManager.isReady(ModelGroup.FACE_DETECT)

    override fun getPeople(): Flow<Resource<List<PersonInfo>>> =
        personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE).map { people ->
            Resource.Success(
                people.map { p ->
                    PersonInfo(
                        id = p.id,
                        name = p.name,
                        providerType = ProviderType.LOCAL_PEOPLE,
                        serverConfigId = LOCAL_PEOPLE_CONFIG_ID,
                        thumbnailUrl = p.thumbnailUrl,
                        assetCount = p.faceCount
                    )
                }
            )
        }

    override fun getPersonMedia(personId: String): Flow<Resource<List<Media>>> =
        faceDao.observeMediaIdsForPerson(personId).map { ids ->
            if (ids.isEmpty()) {
                return@map Resource.Success(emptyList())
            }
            val idSet = ids.toHashSet()
            val local = mediaRepository.getCompleteMedia().first().data.orEmpty()
            val cloud = cloudMediaDao.getAllCachedAsync().map { it.toUriMedia() }
            Resource.Success((local + cloud).filter { it.id in idSet })
        }

    /**
     * Persons whose faces were detected in [mediaId], emitted live as assignments change —
     * used by the media viewer's "remove from person" action to review which people a
     * media is counted as.
     */
    fun getMediaPeople(mediaId: Long): Flow<List<PersonInfo>> =
        faceDao.observePersonIdsForMedia(mediaId).map { ids ->
            ids.mapNotNull { personDao.getById(it) }.map { p ->
                PersonInfo(
                    id = p.id,
                    name = p.name,
                    providerType = ProviderType.LOCAL_PEOPLE,
                    serverConfigId = LOCAL_PEOPLE_CONFIG_ID,
                    thumbnailUrl = p.thumbnailUrl,
                    assetCount = p.faceCount
                )
            }
        }

    override fun getPersonThumbnailUrl(personId: String): String? = null

    override suspend fun updatePersonName(personId: String, name: String): Result<Unit> =
        runCatching { personDao.updateName(personId, name) }

    override suspend fun updatePersonBirthDate(personId: String, birthDate: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Local people provider does not track birth dates"))

    /** Reassign every face of [sourceId] to [targetId] and delete the now-empty source person. */
    suspend fun mergePeople(sourceId: String, targetId: String) {
        faceDao.reassignPerson(sourceId, targetId)
        personDao.deleteById(sourceId)
        personDao.updateFaceCount(targetId, faceDao.countForPerson(targetId), System.currentTimeMillis())
    }

    /**
     * Remove [mediaIds] from [personId]'s aggregation without deleting the media: face rows
     * are un-assigned and a durable exclusion is recorded so future re-scans don't cluster
     * the same media back onto this person.
     *
     * @return true if the person still exists afterwards (false when the last face was
     *         removed and the empty person was deleted).
     */
    suspend fun removeMediaFromPerson(personId: String, mediaIds: List<Long>): Boolean {
        if (mediaIds.isEmpty()) return personDao.getById(personId) != null
        val person = personDao.getById(personId) ?: return false
        val now = System.currentTimeMillis()
        faceDao.insertExclusions(mediaIds.map { FaceExclusionEntity(it, personId, now) })
        faceDao.unassignPersonMedia(personId, mediaIds)
        val remaining = faceDao.countForPerson(personId)
        if (remaining <= 0) {
            deleteThumbnailFile(person)
            personDao.deleteById(personId)
            return false
        }
        personDao.updateFaceCount(personId, remaining, now)
        // Drop the persisted centroid — the next index run rebuilds it without the
        // removed faces (persisted/cluster count mismatch triggers a rebuild).
        faceDao.deleteClusters(listOf(personId))
        if (person.thumbnailMediaId?.let { it in mediaIds } == true) {
            deleteThumbnailFile(person)
            personDao.updateThumbnail(personId, null, null)
        }
        return true
    }

    private fun deleteThumbnailFile(person: PersonEntity) {
        person.thumbnailUrl?.let { url ->
            runCatching { File(requireNotNull(url.toUri().path)).delete() }
        }
    }

    suspend fun setHidden(personId: String, hidden: Boolean) = personDao.setHidden(personId, hidden)

    suspend fun setCover(personId: String, mediaId: Long, thumbnailUrl: String?) =
        personDao.updateThumbnail(personId, mediaId, thumbnailUrl)
}
