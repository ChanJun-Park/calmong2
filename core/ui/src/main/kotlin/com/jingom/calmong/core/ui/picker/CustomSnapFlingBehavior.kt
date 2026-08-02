package com.jingom.calmong.core.ui.picker

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMinByOrNull
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

fun customSnapFlingBehavior(
    lazyListState: LazyListState,
    lastDraggingStartCenterIndexProvider: () -> Int?,
    snapOffset: Int = 0,
    decay: DecayAnimationSpec<Float> = exponentialDecay(),
): FlingBehavior =
    CustomSnapFlingBehavior(
        lazyListState,
        lastDraggingStartCenterIndexProvider,
        snapOffset,
        decay,
    )

class CustomSnapFlingBehavior(
    val lazyListState: LazyListState,
    val lastDraggingStartCenterIndexProvider: () -> Int?,
    val snapOffset: Int = 0,
    val decay: DecayAnimationSpec<Float> = exponentialDecay(),
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val animationState = AnimationState(initialValue = 0f, initialVelocity = initialVelocity)

        var lastValue = 0f
        val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
        val isAFling = abs(initialVelocity) > 1f && visibleItemsInfo.size > 1
        val finalTarget =
            if (isAFling) {
                // 막힘없이 계속 애니메이션이 진행되었을때 도달할 거리
                val decayTarget = decay.calculateTargetValue(0f, initialVelocity)
                var endOfListReached = false

                animationState.animateDecay(decay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value

                    // When we are "slow" enough, switch from decay to the final snap.
                    if (abs(velocity) < SNAP_SPEED_THRESHOLD) cancelAnimation()

                    // If we can't consume the scroll, also stop.
                    if (abs(delta - consumed) > 0.1f) {
                        endOfListReached = true
                        cancelAnimation()
                    }
                }

                if (endOfListReached) {
                    // We couldn't scroll as much as we wanted, likely we reached the end of the
                    // list,
                    // Snap to the current item and finish.
                    scrollBy((lazyListState.centerItemScrollOffset + snapOffset).toFloat())
                    return animationState.velocity
                } else {
                    val currentCenterItemIndex = lazyListState.centerItemIndex()
                    val itemChanged = lastDraggingStartCenterIndexProvider() != currentCenterItemIndex

                    // Now that scrolling slowed down, adjust the animation to land in the item
                    // closest
                    // to the original target. Note that the target may be off-screen, in that case
                    // we
                    // will land on the last visible item in that direction.
                    lazyListState.layoutInfo.visibleItemsInfo
                        .fastMap {
                            if (!itemChanged && it.index == currentCenterItemIndex) {
                                Float.MAX_VALUE
                            } else {
                                animationState.value + it.offset + snapOffset
                            }
                        }.fastMinByOrNull {
                            abs(it - decayTarget)
                        } ?: decayTarget
                }
            } else {
                val currentCenterItemIndex = lazyListState.centerItemIndex()
                val itemChanged = lastDraggingStartCenterIndexProvider() != currentCenterItemIndex

                if (itemChanged) {
                    (lazyListState.centerItemScrollOffset + snapOffset).toFloat()
                } else {
                    (lazyListState.nextItemScrollOffset + snapOffset).toFloat()
                }
            }

        // We have a velocity (animationState.velocity), and a target (finalTarget),
        // Construct a cubic bezier with the given initial velocity, and ending at 0 speed,
        // unless that will mean that we need to accelerate and decelerate.
        // We can also control the inertia of these speeds, i.e. how much it will accelerate/
        // decelerate at the beginning and end.
        val distance = finalTarget - animationState.value
        // If the distance to fling is zero, nothing to do (and must avoid divide-by-zero below).
        if (distance != 0.0f) {
            val initialSpeed = animationState.velocity

            // Inertia of the initial speed.
            val initialInertia = 0.5f

            // Compute how much time we want to spend on the final snap, depending on the speed
            val finalSnapDuration =
                lerp(
                    FINAL_SNAP_DURATION_MIN,
                    FINAL_SNAP_DURATION_MAX,
                    abs(initialSpeed) / SNAP_SPEED_THRESHOLD,
                )

            // Initial control point. Has slope (velocity) adjustedSpeed and magnitude (inertia)
            // initialInertia
            val adjustedSpeed = initialSpeed * finalSnapDuration / distance
            val easingX0 = initialInertia / sqrt(1f + adjustedSpeed * adjustedSpeed)
            val easingY0 = easingX0 * adjustedSpeed

            // Final control point. Has slope 0, unless that will make us accelerate then
            // decelerate,
            // in that case we set the slope to 1.
            val easingX1 = 0.8f
            val easingY1 = if (easingX0 > easingY0) 0.8f else 1f

            animationState.animateTo(
                targetValue = finalTarget,
                animationSpec =
                    tween(
                        durationMillis = (finalSnapDuration * 1000).roundToInt(),
                        easing = CubicBezierEasing(easingX0, easingY0, easingX1, easingY1),
                    ),
            ) {
                scrollBy(value - lastValue)
                lastValue = value
            }
        }

        return animationState.velocity
    }
}

// Speed, in pixels per second, to switch between decay and final snap.
private const val SNAP_SPEED_THRESHOLD = 1200

// Minimum duration for the final snap after the fling, in seconds, used when the initial speed
// is 0
private const val FINAL_SNAP_DURATION_MIN = .1f

// Maximum duration for the final snap after the fling, in seconds, used when the speed is
// SNAP_SPEED_THRESHOLD
private const val FINAL_SNAP_DURATION_MAX = .35f

// 아이템이 중앙보다 아래에 있으면 양수
private val LazyListState.centerItemScrollOffset: Int
    get() {
        val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
        val centerItemCenterOffset =
            layoutInfo
                .visibleItemsInfo
                .map {
                    it.offset + it.size / 2
                }.minByOrNull {
                    abs(it - viewPortCenterOffset)
                } ?: 0

        return (centerItemCenterOffset - viewPortCenterOffset).toInt()
    }

private val LazyListState.nextItemScrollOffset: Int
    get() {
        val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
        val currentCenterItemIndex = centerItemIndex()
        val nextItemIndex =
            if (lastScrolledBackward) {
                currentCenterItemIndex - 1
            } else {
                currentCenterItemIndex + 1
            }

        val nextItemInfoLayoutInfo =
            layoutInfo
                .visibleItemsInfo
                .find { it.index == nextItemIndex }
                ?: return 0

        return (nextItemInfoLayoutInfo.offset + nextItemInfoLayoutInfo.size / 2 - viewPortCenterOffset).toInt()
    }
