/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.storycards

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dot.gallery.R
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerAutoAdvance
import com.dot.gallery.core.Settings.Misc.rememberStoryViewerDuration
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.model.MediaMetadata
import com.dot.gallery.feature_node.domain.model.StoryCard
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly
import com.dot.gallery.feature_node.presentation.mediaview.LocalMediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.MediaViewerVisualPolicy
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.FavoriteButton
import com.dot.gallery.feature_node.presentation.mediaview.components.actionbuttons.ShareButton
import com.dot.gallery.feature_node.presentation.mediaview.components.media.MediaPreviewComponent
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.rememberWindowInsetsController
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DEFAULT_STORY_DURATION_SECONDS = 5
private const val MIN_STORY_DURATION_SECONDS = 3
private const val MAX_STORY_DURATION_SECONDS = 10
private const val STORY_DISMISS_THRESHOLD_FRACTION = 0.12f
private const val STORY_DISMISS_FADE_DISTANCE_FRACTION = 0.35f

internal fun storyDismissProgress(offsetY: Float, height: Int): Float {
    if (height <= 0) return 0f
    return (offsetY.coerceAtLeast(0f) / (height * STORY_DISMISS_FADE_DISTANCE_FRACTION))
        .coerceIn(0f, 1f)
}

internal fun shouldDismissStory(offsetY: Float, height: Int): Boolean =
    height > 0 && offsetY >= height * STORY_DISMISS_THRESHOLD_FRACTION

internal fun storyDurationMillis(rawSeconds: String): Long =
    (rawSeconds.toIntOrNull() ?: DEFAULT_STORY_DURATION_SECONDS)
        .coerceIn(MIN_STORY_DURATION_SECONDS, MAX_STORY_DURATION_SECONDS) * 1_000L

internal fun advanceStoryElapsed(
    elapsedMillis: Long,
    frameDeltaMillis: Long,
    durationMillis: Long,
    blocked: Boolean,
): Long = if (blocked) {
    elapsedMillis
} else {
    (elapsedMillis + frameDeltaMillis.coerceAtLeast(0L)).coerceAtMost(durationMillis)
}

@Stable
private class StoryDismissState {
    private var heightPx by mutableIntStateOf(0)
    private var dragOffsetY by mutableFloatStateOf(0f)
    private var gestureActive by mutableStateOf(false)
    private val animation = Animatable(0f)

    val offsetY: Float
        get() = if (gestureActive) dragOffsetY else animation.value

    val progress: Float
        get() = storyDismissProgress(offsetY, heightPx)

    val chromeAlpha: Float
        get() = 1f - progress

    val isActive: Boolean
        get() = gestureActive || offsetY > 0f

    fun updateHeight(height: Int) {
        heightPx = height
    }

    fun start(scope: CoroutineScope) {
        dragOffsetY = animation.value
        gestureActive = true
        scope.launch { animation.stop() }
    }

    fun dragBy(deltaY: Float) {
        dragOffsetY = (dragOffsetY + deltaY).coerceIn(0f, heightPx * 1.05f)
    }

    fun finish(scope: CoroutineScope, onDismiss: () -> Unit) {
        settle(
            scope = scope,
            commit = shouldDismissStory(dragOffsetY, heightPx),
            onDismiss = onDismiss,
        )
    }

    fun cancel(scope: CoroutineScope) {
        settle(scope = scope, commit = false, onDismiss = {})
    }

    private fun settle(
        scope: CoroutineScope,
        commit: Boolean,
        onDismiss: () -> Unit,
    ) {
        val startOffset = dragOffsetY
        scope.launch {
            animation.snapTo(startOffset)
            gestureActive = false
            if (commit) {
                animation.animateTo(
                    targetValue = heightPx * 1.05f,
                    animationSpec = tween(durationMillis = 180),
                )
                onDismiss()
            } else {
                animation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun StoryViewerScreen(
    cards: List<StoryCard>?,
    initialCardId: Long = -1L,
    metadataMap: Map<Long, MediaMetadata> = emptyMap(),
    onEnsureMetadata: (Media?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val allowBlur by rememberAllowBlur()
    CompositionLocalProvider(
        LocalMediaViewerVisualPolicy provides MediaViewerVisualPolicy(allowBlur = allowBlur)
    ) {
        StoryViewerContent(
            cards = cards,
            initialCardId = initialCardId,
            metadataMap = metadataMap,
            onEnsureMetadata = onEnsureMetadata,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun StoryViewerContent(
    cards: List<StoryCard>?,
    initialCardId: Long,
    metadataMap: Map<Long, MediaMetadata>,
    onEnsureMetadata: (Media?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Force light status bar icons (white) on dark background, restore on exit
    val windowInsetsController = rememberWindowInsetsController()
    val eventHandler = LocalEventHandler.current
    DisposableEffect(Unit) {
        val previousLight = windowInsetsController.isAppearanceLightStatusBars
        windowInsetsController.isAppearanceLightStatusBars = false
        eventHandler.setFollowTheme(false)
        onDispose {
            windowInsetsController.isAppearanceLightStatusBars = previousLight
            eventHandler.setFollowTheme(true)
        }
    }
    BackHandler { onDismiss() }

    // null = still loading, show spinner
    if (cards == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                    tint = Color.White,
                )
            }
        }
        return
    }

    // Loaded but empty — keep recovery and navigation available
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.story_no_media),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_cd),
                    tint = Color.White,
                )
            }
        }
        return
    }

    // Resolve initial page; -1 means the target card hasn't loaded yet
    val targetIndex by rememberedDerivedState(cards, initialCardId) {
        if (initialCardId == -1L) 0
        else cards.indexOfFirst { it.id == initialCardId }
    }

    val pagerState = rememberPagerState(
        initialPage = targetIndex.coerceAtLeast(0),
        pageCount = { cards.size }
    )

    // When the target card appears after initial load, scroll to it
    LaunchedEffect(targetIndex) {
        if (targetIndex > 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    val scope = rememberCoroutineScope()
    val dismissState = remember(initialCardId) { StoryDismissState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 1f - dismissState.progress))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> cards[index].id },
            beyondViewportPageCount = 0,
        ) { page ->
            val card by rememberedDerivedState(cards, page) {
                cards[page]
            }
            val isCurrentPage by rememberedDerivedState(pagerState.currentPage) {
                pagerState.currentPage == page
            }
            StoryCardViewer(
                card = card,
                isCurrentPage = isCurrentPage,
                metadataMap = metadataMap,
                onEnsureMetadata = onEnsureMetadata,
                onDismiss = onDismiss,
                dismissState = dismissState,
                isPagerScrollInProgress = pagerState.isScrollInProgress,
                onCardFinished = {
                    scope.launch {
                        val page = pagerState.targetPage
                        if (page < cards.lastIndex) {
                            pagerState.animateScrollToPage(page + 1)
                        } else {
                            onDismiss()
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun StoryCardViewer(
    card: StoryCard,
    isCurrentPage: Boolean,
    metadataMap: Map<Long, MediaMetadata> = emptyMap(),
    onEnsureMetadata: (Media?) -> Unit = {},
    onDismiss: () -> Unit,
    dismissState: StoryDismissState,
    isPagerScrollInProgress: Boolean,
    onCardFinished: () -> Unit
) {
    val mediaList = card.mediaList
    if (mediaList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.story_no_media), color = Color.White)
        }
        return
    }

    var currentMediaIndex by rememberSaveable(card.id) { mutableIntStateOf(0) }
    val currentMedia by rememberedDerivedState(mediaList, currentMediaIndex) {
        mediaList[currentMediaIndex.coerceIn(0, mediaList.lastIndex)]
    }
    val autoAdvance by rememberStoryViewerAutoAdvance()
    val durationStr by rememberStoryViewerDuration()
    val durationMs = remember(durationStr) { storyDurationMillis(durationStr) }
    var isPaused by rememberSaveable(card.id) { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var elapsedMs by remember(currentMedia.id) { mutableLongStateOf(0L) }
    var progress by remember(currentMedia.id) { mutableFloatStateOf(0f) }
    val dismissScope = rememberCoroutineScope()
    val dismissOffsetY = dismissState.offsetY
    val chromeAlpha = dismissState.chromeAlpha
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val timerBlocked = isPaused || isPressed || isPagerScrollInProgress || !isResumed ||
        dismissState.isActive
    val playWhenReady = rememberUpdatedState(isCurrentPage && !timerBlocked)
    val allowBlur = LocalMediaViewerVisualPolicy.current.allowBlur
    val hazeState = com.dot.gallery.feature_node.presentation.util.LocalHazeState.current

    fun showPreviousMedia() {
        if (currentMediaIndex > 0) currentMediaIndex--
    }

    fun showNextMedia() {
        if (currentMediaIndex < mediaList.lastIndex) currentMediaIndex++ else onCardFinished()
    }

    // Auto-advance timer
    LaunchedEffect(currentMedia.id, isCurrentPage, autoAdvance, durationMs, timerBlocked) {
        if (!isCurrentPage || !autoAdvance || currentMedia.isVideo || timerBlocked) return@LaunchedEffect
        var lastFrameMillis = 0L
        while (elapsedMs < durationMs) {
            withFrameMillis { frameMillis ->
                val delta = if (lastFrameMillis == 0L) 0L else frameMillis - lastFrameMillis
                lastFrameMillis = frameMillis
                elapsedMs = advanceStoryElapsed(elapsedMs, delta, durationMs, timerBlocked)
            }
            progress = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        }
        showNextMedia()
    }

    val blurContainerColor = remember {
        Color.Black.copy(alpha = 0.5f)
    }
    val fallbackContainerColor = remember {
        Color.Black.copy(alpha = 0.4f)
    }
    val previousActionLabel = stringResource(R.string.story_previous_photo)
    val nextActionLabel = stringResource(R.string.story_next_photo)
    val positionDescription = stringResource(
        R.string.story_position,
        currentMediaIndex + 1,
        mediaList.size,
    )
    val navigationActions = buildList {
        if (currentMediaIndex > 0) {
            add(CustomAccessibilityAction(previousActionLabel) {
                showPreviousMedia()
                true
            })
        }
        add(CustomAccessibilityAction(nextActionLabel) {
            showNextMedia()
            true
        })
    }

    // Look up metadata for the current media and trigger collection if needed
    val mediaMetadata by rememberedDerivedState(metadataMap, currentMedia) {
        metadataMap[currentMedia.id]
    }
    LaunchedEffect(currentMedia.id) {
        onEnsureMetadata(currentMedia)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { dismissState.updateHeight(it.height) }
    ) {
        // Media display using the same component as the media view screen
        // key() forces full tear-down/rebuild when media changes, ensuring
        // the VideoPlayer's SurfaceView and ExoPlayer are properly recycled
        key(currentMedia.id) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dismissOffsetY.roundToInt()) }
            ) {
                MediaPreviewComponent(
                    media = currentMedia,
                    uiEnabled = true,
                    playWhenReady = playWhenReady,
                    onItemClick = { /* handled by gesture overlay */ },
                    onSwipeDown = {},
                    rotationDisabled = true,
                    onImageRotated = {},
                    offset = IntOffset.Zero,
                    isPanorama = mediaMetadata?.isPanorama == true,
                    isPhotosphere = mediaMetadata?.isPhotosphere == true,
                    isMotionPhoto = mediaMetadata?.isMotionPhoto == true,
                    storyActive = true,
                    onVideoEnded = { if (autoAdvance) showNextMedia() },
                    videoController = { _, _, currentTime, duration, _, _, _ ->
                        val videoPosition = currentTime.longValue
                        LaunchedEffect(videoPosition, duration) {
                            if (duration > 0L) {
                                progress = (videoPosition.toFloat() / duration).coerceIn(0f, 1f)
                            }
                        }
                    }
                )
            }
        }

        // Gesture overlay for story navigation (left/right tap, long-press to pause)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    stateDescription = positionDescription
                    customActions = navigationActions
                }
                .pointerInput(card.id) {
                    detectTapGestures(
                        onTap = { offset ->
                            when {
                                offset.x < size.width / 3f -> showPreviousMedia()
                                offset.x > size.width * 2f / 3f -> showNextMedia()
                            }
                        },
                        onLongPress = { },
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        }
                    )
                }
                .pointerInput(card.id) {
                    detectVerticalDragGestures(
                        onDragStart = { dismissState.start(dismissScope) },
                        onVerticalDrag = { change, dragAmount ->
                            dismissState.dragBy(dragAmount)
                            change.consume()
                        },
                        onDragEnd = { dismissState.finish(dismissScope, onDismiss) },
                        onDragCancel = { dismissState.cancel(dismissScope) },
                    )
                }
        )

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Top: Progress segments + back button + title + pause ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Segmented progress bar (no end tip — just rounded segments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = positionDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = currentMediaIndex + progress,
                            range = 0f..mediaList.size.toFloat(),
                            steps = mediaList.size - 1,
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                mediaList.forEachIndexed { index, _ ->
                    val segmentProgress = when {
                        index < currentMediaIndex -> 1f
                        index == currentMediaIndex -> if (autoAdvance || currentMedia.isVideo) progress else 1f
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentProgress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Back button + centered title + pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button — circular with blur, white tint
                val backBgModifier = if (allowBlur) {
                    Modifier
                        .clip(CircleShape)
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                        )
                } else {
                    Modifier.background(fallbackContainerColor, CircleShape)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .then(backBgModifier)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = Color.White
                    )
                }

                // Centered title + subtitle
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (card.subtitle != null) {
                        Text(
                            text = card.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Pause button — rounded with blur
                if (autoAdvance || currentMedia.isVideo) {
                    val pauseBgModifier = if (allowBlur) {
                        Modifier
                            .clip(CircleShape)
                            .hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                            )
                    } else {
                        Modifier.background(fallbackContainerColor, CircleShape)
                    }
                    IconButton(
                        onClick = { isPaused = !isPaused },
                        modifier = Modifier
                            .then(pauseBgModifier)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = stringResource(
                                if (isPaused) R.string.story_resume else R.string.story_pause
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Spacer matching back button width for centering
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ── Bottom: Action buttons + counter chip ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = chromeAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Action buttons row (share, favorite, etc.)
            if (!currentMedia.readUriOnly) {
                val actionBgModifier = if (allowBlur) {
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                        )
                } else {
                    Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
                }
                Row(
                    modifier = Modifier
                        .then(actionBgModifier)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShareButton(
                        media = currentMedia,
                        enabled = true
                    )
                    FavoriteButton(
                        media = currentMedia,
                        enabled = true
                    )
                }
            }

            // Item counter chip with blur
            val chipBgModifier = if (allowBlur) {
                Modifier
                    .clip(RoundedCornerShape(100))
                    .hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.ultraThin(containerColor = blurContainerColor)
                    )
            } else {
                Modifier.background(fallbackContainerColor, RoundedCornerShape(100))
            }
            Text(
                text = stringResource(R.string.story_counter, currentMediaIndex + 1, mediaList.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .then(chipBgModifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
