package com.jingom.calmong.core.ui.picker

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastSumBy
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * A [CustomSnapLayoutInfoProvider] for LazyLists.
 * snap 동작이 일어날때 항상 fling 하고 있던 방향에 있는 아이템으로 snap 하도록 SnapLayoutInfoProvider 의 도악을 수정했다.
 * CustomSnapLayoutInfoProvider 은 stickyHeader 와 같은 요소가 없는 리스트에만 사용해야 한다.
 *
 * @param lazyListState The [LazyListState] with information about the current state of the list
 * @param snapPosition The desired positioning of the snapped item within the main layout. This
 *   position should be considered with regard to the start edge of the item and the placement
 *   within the viewport.
 * @return A [CustomSnapLayoutInfoProvider] that can be used with [snapFlingBehavior]
 */
@Suppress("ktlint:standard:function-naming")
fun CustomSnapLayoutInfoProvider(
    lazyListState: LazyListState,
    snapPosition: SnapPosition = SnapPosition.Center,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val layoutInfo: LazyListLayoutInfo
            get() = lazyListState.layoutInfo

        private val averageItemSize: Int
            get() {
                val layoutInfo = layoutInfo
                return if (layoutInfo.visibleItemsInfo.isEmpty()) {
                    0
                } else {
                    val numberOfItems = layoutInfo.visibleItemsInfo.size
                    layoutInfo.visibleItemsInfo.fastSumBy { it.size } / numberOfItems
                }
            }

        override fun calculateApproachOffset(
            velocity: Float,
            decayOffset: Float,
        ): Float =
            (decayOffset.absoluteValue - averageItemSize).coerceAtLeast(0.0f) *
                decayOffset.sign

        override fun calculateSnapOffset(velocity: Float): Float {
            var lowerBoundOffset = Float.NEGATIVE_INFINITY
            var upperBoundOffset = Float.POSITIVE_INFINITY

            layoutInfo.visibleItemsInfo.fastForEach { item ->
                val offset =
                    calculateDistanceToDesiredSnapPosition(
                        mainAxisViewPortSize = layoutInfo.singleAxisViewportSize,
                        beforeContentPadding = layoutInfo.beforeContentPadding,
                        afterContentPadding = layoutInfo.afterContentPadding,
                        itemSize = item.size,
                        itemOffset = item.offset,
                        itemIndex = item.index,
                        snapPosition = snapPosition,
                        itemCount = layoutInfo.totalItemsCount,
                    )

                // Find item that is closest to the center
                if (offset <= 0 && offset > lowerBoundOffset) {
                    lowerBoundOffset = offset
                }

                // Find item that is closest to center, but after it
                if (offset >= 0 && offset < upperBoundOffset) {
                    upperBoundOffset = offset
                }
            }

            val finalDistance = if (velocity > 0) upperBoundOffset else lowerBoundOffset
            return finalDistance.takeIf { it.isValidDistance() } ?: 0f
        }
    }

internal fun calculateDistanceToDesiredSnapPosition(
    mainAxisViewPortSize: Int,
    beforeContentPadding: Int,
    afterContentPadding: Int,
    itemSize: Int,
    itemOffset: Int,
    itemIndex: Int,
    snapPosition: SnapPosition,
    itemCount: Int,
): Float {
    val desiredDistance =
        with(snapPosition) {
            position(
                mainAxisViewPortSize,
                itemSize,
                beforeContentPadding,
                afterContentPadding,
                itemIndex,
                itemCount,
            )
        }.toFloat()

    return itemOffset - desiredDistance
}

internal val LazyListLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width

internal fun Float.isValidDistance(): Boolean = this != Float.POSITIVE_INFINITY && this != Float.NEGATIVE_INFINITY
