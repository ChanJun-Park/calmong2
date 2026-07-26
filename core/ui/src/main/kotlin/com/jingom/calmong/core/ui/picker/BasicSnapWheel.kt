package com.jingom.calmong.core.ui.picker

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jingom.calmong.core.designsystem.theme.CalMongTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
fun BasicSnapWheel(
    items: List<String>,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5, // 한 화면에 몇 개가 보일지 (android-wheel의 DEF_VISIBLE_ITEMS와 같은 개념)
    itemHeight: Dp = 40.dp,
    onCenterItemIndexChange: (Int) -> Unit = {},
) {
    val itemSize = items.size
    require(itemSize > 0) {
        "1개 이상의 요소를 갖는 items 리스트를 사용해야 합니다."
    }

    val state =
        rememberLazyListState(
            initialFirstVisibleItemIndex = LARGE_NUMBER_OF_ITEMS / 2 - ((LARGE_NUMBER_OF_ITEMS / 2) % itemSize),
        )
    val itemPaddingVertical = 8.dp
    val itemHeightWithPadding = itemHeight + itemPaddingVertical * 2
    val wheelHeight = itemHeightWithPadding * visibleItemCount
    val verticalContentPadding = (wheelHeight - itemHeightWithPadding) / 2

    LazyColumn(
        state = state,
        flingBehavior = rememberCustomSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = verticalContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.basicSnapWheelModifier(wheelHeight, itemHeightWithPadding, visibleItemCount),
    ) {
        items(
            count = LARGE_NUMBER_OF_ITEMS,
        ) { index ->
            val itemIndex = index % itemSize
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(vertical = itemPaddingVertical)
                        .height(itemHeight)
                        .fillMaxWidth(),
            ) {
                Text(
                    text = items[itemIndex],
                )
            }
        }
    }

    val updatedOnCenterItemIndexChange by rememberUpdatedState(onCenterItemIndexChange)
    LaunchedEffect(state, items) {
        snapshotFlow { calculateCenterIndex(state.layoutInfo, itemSize) }
            .distinctUntilChanged()
            .collect(updatedOnCenterItemIndexChange)
    }
}

@Composable
private fun Modifier.basicSnapWheelModifier(
    wheelHeight: Dp,
    itemHeightWithPadding: Dp,
    visibleItemCount: Int,
) = this
    .height(wheelHeight)
    .drawWithContent {
        drawContent()
        repeat(visibleItemCount + 1) {
            drawLine(
                color = Color.Black,
                start = Offset(0f, it * itemHeightWithPadding.toPx()),
                end = Offset(size.width, it * itemHeightWithPadding.toPx()),
            )
        }
        drawRect(
            color = Color.Black.copy(alpha = 0.1f),
            topLeft = Offset(0f, 0f),
            size = Size(width = size.width, height = itemHeightWithPadding.toPx() * (visibleItemCount / 2)),
        )

        drawRect(
            color = Color.Black.copy(alpha = 0.1f),
            topLeft = Offset(0f, itemHeightWithPadding.toPx() * (visibleItemCount / 2 + 1)),
            size = Size(width = size.width, height = itemHeightWithPadding.toPx() * (visibleItemCount / 2)),
        )
    }

@Composable
private fun rememberCustomSnapFlingBehavior(lazyListState: LazyListState): TargetedFlingBehavior {
    val snappingLayout = remember(lazyListState) { CustomSnapLayoutInfoProvider(lazyListState, SnapPosition.Center) }
    val density = LocalDensity.current
    val highVelocityApproachSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay()

    return remember(snappingLayout, highVelocityApproachSpec, density) {
        snapFlingBehavior(
            snapLayoutInfoProvider = snappingLayout,
            decayAnimationSpec =
                exponentialDecay(
                    frictionMultiplier = 0.5f, // 올릴수록 빨리 멈춤(짧게 튕김), 내릴수록 오래 미끄러짐
                    absVelocityThreshold = 0.1f, // 이 속도 밑으로 떨어지면 "멈췄다"고 판단
                ),
            snapAnimationSpec = spring(stiffness = Spring.StiffnessLow),
        )
    }
}

private fun calculateCenterIndex(
    layoutInfo: LazyListLayoutInfo,
    itemsSize: Int,
): Int {
    val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
    val centerIndex =
        layoutInfo
            .visibleItemsInfo
            .minByOrNull {
                val itemCenterOffset = it.offset + it.size / 2
                abs(itemCenterOffset - viewPortCenterOffset)
            }?.index ?: 0

    return centerIndex % itemsSize
}

@Preview(showBackground = true)
@Composable
private fun BasicSnapWheelPreview() {
    CalMongTheme {
        BasicSnapWheel(
            items = List(100) { it.toString() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val LARGE_NUMBER_OF_ITEMS = 100_000
