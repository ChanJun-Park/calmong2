package com.jingom.calmong.core.ui.picker

import androidx.annotation.IntRange
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.jingom.calmong.core.designsystem.theme.CalMongTheme
import kotlin.math.abs
import kotlin.math.absoluteValue

@Composable
fun BasicSnapWheel(
    state: WheelPickerState,
    gradientColor: Color,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5, // 한 화면에 몇 개가 보일지 (android-wheel의 DEF_VISIBLE_ITEMS와 같은 개념)
    itemHeight: Dp = 40.dp,
    optionContent: @Composable BoxScope.(optionIndex: Int) -> Unit,
) {
    val itemPaddingVertical = 8.dp
    val itemHeightWithPadding = itemHeight + itemPaddingVertical * 2
    val wheelHeight = itemHeightWithPadding * visibleItemCount
    val verticalContentPadding = (wheelHeight - itemHeightWithPadding) / 2

    LazyColumn(
        state = state.lazyListState,
        flingBehavior = rememberCustomSnapFlingBehavior(state.lazyListState),
        contentPadding = PaddingValues(vertical = verticalContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.basicSnapWheelModifier(wheelHeight, itemHeightWithPadding, gradientColor),
    ) {
        items(
            count = state.numberOfItems,
        ) { index ->
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(vertical = itemPaddingVertical)
                        .height(itemHeight)
                        .fillMaxWidth()
                        .wheelItemScaling(
                            itemIndex = index,
                            lazyListState = state.lazyListState,
                        ),
            ) {
                val optionIndex = (index + state.optionsOffset) % state.numberOfOptions
                optionContent(optionIndex)
            }
        }
    }
}

@Composable
private fun Modifier.basicSnapWheelModifier(
    wheelHeight: Dp,
    itemHeightWithPadding: Dp,
    gradientColor: Color,
) = this
    .height(wheelHeight)
    .fadeEdges(gradientColor)
    .centerHighlight(itemHeightWithPadding)

@Composable
private fun Modifier.fadeEdges(
    gradientColor: Color,
    gradientRatio: Float = 0.33f,
): Modifier =
    this
        .drawWithContent {
            drawContent()

            val gradientHeight = size.height * gradientRatio
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors = listOf(gradientColor, gradientColor.copy(alpha = 0f)),
                        start = Offset(0f, 0f),
                        end = Offset(0f, gradientHeight),
                    ),
                topLeft = Offset(0f, 0f),
                size = Size(width = size.width, height = gradientHeight),
            )

            drawRect(
                brush =
                    Brush.linearGradient(
                        colors = listOf(gradientColor.copy(alpha = 0f), gradientColor),
                        start = Offset(0f, size.height - gradientHeight),
                        end = Offset(0f, size.height),
                    ),
                topLeft = Offset(0f, size.height - gradientHeight),
                size = Size(width = size.width, height = gradientHeight),
            )
        }

@Composable
private fun Modifier.centerHighlight(
    itemHeightWithPadding: Dp,
    cornerRadius: Dp = 8.dp,
    highlightColor: Color = CalMongTheme.colors.functional.general.shadow,
): Modifier =
    this
        .drawWithContent {
            val topY = (size.height - itemHeightWithPadding.toPx()) / 2
            val horizontalPadding = 8.dp.toPx()
            drawRoundRect(
                color = highlightColor,
                topLeft = Offset(horizontalPadding, topY),
                size = Size(width = size.width - horizontalPadding * 2, height = itemHeightWithPadding.toPx()),
                cornerRadius = CornerRadius(x = cornerRadius.toPx(), y = cornerRadius.toPx()),
            )

            drawContent()
        }

@Composable
fun Modifier.wheelItemScaling(
    itemIndex: Int,
    lazyListState: LazyListState,
): Modifier =
    this
        .graphicsLayer {
            val fraction = lazyListState.distanceRangeFromViewPortCenter(itemIndex)
            val safeFraction = fraction.coerceIn(-1f, 1f)
            val distanceFromCenter = lazyListState.distanceFromViewPortCenter(itemIndex)
            val alphaRate = lerp(1f, 0f, safeFraction.absoluteValue)

            alpha = alphaRate
            rotationX = lerp(0f, 80f, -safeFraction)
            scaleY = lerp(1f, 0.4f, safeFraction.absoluteValue)
            translationY = -distanceFromCenter * 0.6f * fraction.absoluteValue
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

private fun LazyListState.centerItemIndex(): Int {
    val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
    val centerIndex =
        layoutInfo
            .visibleItemsInfo
            .minByOrNull {
                val itemCenterOffset = it.offset + it.size / 2
                abs(itemCenterOffset - viewPortCenterOffset)
            }?.index ?: 0

    return centerIndex
}

private fun LazyListState.distanceRangeFromViewPortCenter(itemIndex: Int): Float {
    val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
    val halfOfScrollHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
    val itemCenterOffset =
        layoutInfo
            .visibleItemsInfo
            .find { it.index == itemIndex }
            ?.run {
                offset + size / 2
            } ?: return 0f

    val distance = itemCenterOffset - viewPortCenterOffset
    return (distance / halfOfScrollHeight)
}

private fun LazyListState.distanceFromViewPortCenter(itemIndex: Int): Float {
    val viewPortCenterOffset = layoutInfo.viewportSize.height.toFloat() / 2 + layoutInfo.viewportStartOffset
    val itemCenterOffset =
        layoutInfo
            .visibleItemsInfo
            .find { it.index == itemIndex }
            ?.run {
                offset + size / 2
            } ?: return 0f

    return itemCenterOffset - viewPortCenterOffset
}

@Composable
fun rememberWheelPickerState(
    initialNumberOfOptions: Int,
    initiallySelectedOption: Int = 0,
): WheelPickerState =
    rememberSaveable(
        inputs = arrayOf(initialNumberOfOptions, initiallySelectedOption),
        saver = WheelPickerState.Saver,
    ) {
        WheelPickerState(initialNumberOfOptions, initiallySelectedOption)
    }

@Stable
class WheelPickerState(
    @IntRange(from = 1)
    initialNumberOfOptions: Int,
    initiallySelectedOption: Int = 0,
) : ScrollableState {
    init {
        verifyNumberOfOptions(initialNumberOfOptions)
    }

    private var _numberOfOptions by mutableIntStateOf(initialNumberOfOptions)
    var numberOfOptions: Int
        get() = _numberOfOptions
        set(newNumberOfOptions) {
            verifyNumberOfOptions(newNumberOfOptions)

            optionsOffset =
                positiveModulo(
                    selectedOption.coerceAtMost(newNumberOfOptions - 1) - lazyListState.centerItemIndex(),
                    newNumberOfOptions,
                )

            _numberOfOptions = newNumberOfOptions
        }

    internal var optionsOffset = 0

    val selectedOption: Int
        get() = (lazyListState.centerItemIndex() + optionsOffset) % numberOfOptions

    val numberOfItems = LARGE_NUMBER_OF_ITEMS

    internal val lazyListState =
        run {
            val repeats = LARGE_NUMBER_OF_ITEMS / numberOfOptions
            val centerOffset = numberOfOptions * (repeats / 2)

            LazyListState(
                firstVisibleItemIndex = centerOffset + initiallySelectedOption,
                firstVisibleItemScrollOffset = 0,
            )
        }

    override val isScrollInProgress: Boolean
        get() = lazyListState.isScrollInProgress

    override val canScrollForward: Boolean
        get() = lazyListState.canScrollForward

    override val canScrollBackward: Boolean
        get() = lazyListState.canScrollBackward

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        lazyListState.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = lazyListState.dispatchRawDelta(delta)

    suspend fun scrollToOption(index: Int) {
        lazyListState.scrollToItem(index = getClosestTargetItemIndex(index))
    }

    suspend fun animateScrollToOption(index: Int) {
        lazyListState.animateScrollToItem(index = getClosestTargetItemIndex(index))
    }

    private fun getClosestTargetItemIndex(option: Int): Int {
        val stepsPrev = positiveModulo(selectedOption - option, numberOfOptions)
        val stepsNext = positiveModulo(option - selectedOption, numberOfOptions)
        return lazyListState.centerItemIndex() +
            if (stepsPrev <= stepsNext) -stepsPrev else stepsNext
    }

    private fun verifyNumberOfOptions(numberOfOptions: Int) {
        require(numberOfOptions > 0) { "The picker should have at least one item." }
        require(numberOfOptions < LARGE_NUMBER_OF_ITEMS / 3) {
            // Set an upper limit to ensure there are at least 3 repeats of all the options
            "The picker should have less than ${LARGE_NUMBER_OF_ITEMS / 3} items"
        }
    }

    companion object {
        val Saver: Saver<WheelPickerState, Any> =
            listSaver<WheelPickerState, Any?>(
                save = { listOf(it.numberOfOptions, it.selectedOption) },
                restore = { saved ->
                    WheelPickerState(
                        initialNumberOfOptions = saved[0] as Int,
                        initiallySelectedOption = saved[1] as Int,
                    )
                },
            )
    }
}

private fun positiveModulo(
    n: Int,
    mod: Int,
): Int {
    require(mod > 0)
    return ((n % mod) + mod) % mod
}

@Preview(showBackground = true)
@Composable
private fun BasicSnapWheelPreview() {
    CalMongTheme {
        val items = List(100) { it.toString() }
        val state = rememberWheelPickerState(items.size, 0)
        BasicSnapWheel(
            state = state,
            gradientColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth(),
            optionContent = {
                Text(items[it])
            },
        )
    }
}

private const val LARGE_NUMBER_OF_ITEMS = 100_000
