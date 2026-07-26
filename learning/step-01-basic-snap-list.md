# Step 1 — 중앙에 스냅되는 기본 리스트

> 로드맵: [`00-roadmap.md`](./00-roadmap.md)

## 목표

숫자 0~9를 세로로 나열한 `LazyColumn`을 만들고, 스크롤을 놓으면 **항상 아이템 하나가 화면(또는 컴포넌트) 정중앙에 딱 맞춰 멈추도록** 한다. 무한 스크롤, 그라데이션, 스케일 효과는 아직 다루지 않는다.

## 왜 이 단계부터인가

Wear Picker(`reference/wear-compose-picker/Picker.kt:248-325`)는 `ScalingLazyColumn` + `flingBehavior = PickerDefaults.flingBehavior(state)` 조합으로 스크롤 후 자동으로 중앙 아이템에 스냅되는 걸 기본으로 깔고, 그 위에 무한 루프/시각효과/접근성을 얹는 구조다. 우리도 같은 순서로 간다 — **먼저 "스냅되는 리스트"라는 지반을 다진 뒤에 나머지를 쌓는다.**

폰 Compose에는 `ScalingLazyColumnSnapFlingBehavior` 같은 커스텀 decay+snap 클래스 대신, 공식적으로 이미 만들어진 스냅 API가 있다:

```kotlin
// reference/compose-foundation-snapping/LazyListSnapLayoutInfoProvider.kt:113-121
@Composable
public fun rememberSnapFlingBehavior(
    lazyListState: LazyListState,
    snapPosition: SnapPosition = SnapPosition.Center, // 기본값이 이미 "중앙 스냅"
): FlingBehavior
```

`LazyColumn`의 `flingBehavior` 파라미터에 이걸 넘기면 android-wheel의 `WheelScroller`(제스처 감지 + `Scroller` + `justify` 핸들러 루프, 약 250줄) 전체가 대체된다. `reference/compose-foundation-snapping/SnapFlingBehaviorSample.kt:41-62`에 공식 사용 예제(`SnapFlingBehaviorSimpleSample`)가 있으니 API 사용법 자체가 궁금하면 그걸 먼저 열어봐도 좋다 — 단, 그 샘플은 **가로 리스트**이고 스냅 위치를 화면 중앙이 아니라 "아이템"에만 맞추는 예제이므로, 우리 문제(세로 + 컴포넌트 정중앙 스냅)에 맞게 변형이 필요하다.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| 스냅 API 시그니처와 기본 동작 | `reference/compose-foundation-snapping/LazyListSnapLayoutInfoProvider.kt` (특히 41~101행의 `calculateSnapOffset`) |
| 공식 사용 예제 | `reference/compose-foundation-snapping/SnapFlingBehaviorSample.kt` |
| "보이는 아이템 개수"와 "패딩"으로 레이아웃을 잡는 아이디어의 원형 (View 시절 버전) | `trunk/wheel/src/kankan/wheel/widget/WheelView.java` 의 `DEF_VISIBLE_ITEMS`, `PADDING`, `getDesiredHeight()` |

## 요구사항 명세 (구현은 직접, 시그니처만 제시)

아래와 같은 컴포저블을 만든다고 가정하자. 시그니처만 참고하고 내부는 스스로 채워라.

```kotlin
@Composable
fun BasicSnapWheel(
    items: List<String>,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,   // 한 화면에 몇 개가 보일지 (android-wheel의 DEF_VISIBLE_ITEMS와 같은 개념)
    itemHeight: Dp = 40.dp,
)
```

## 완료 기준

- [ ] 손가락을 떼면 항상 아이템 **하나**가 컴포넌트 정중앙에 정확히 위치한다 (두 아이템 사이 어중간한 위치에서 멈추지 않는다).
- [ ] 빠르게 스와이프(플링)해도 관성이 자연스럽게 이어지다가 스냅된다.
- [ ] 리스트의 첫 번째 아이템(0)과 마지막 아이템(9)도 정중앙까지 스크롤될 수 있어야 한다 (즉 양 끝에서 "더 이상 스크롤 안 되는데 중앙에 못 옴" 상태가 없어야 함).
- [ ] `visibleItemCount`를 3, 5, 7로 바꿔가며 테스트했을 때 모두 정상 동작한다.

## 셀프 체크 질문

1. `LazyColumn` 안의 아이템 하나가 컴포넌트 **정중앙**까지 스크롤되게 하려면, 리스트 위/아래에 어떤 여백(`contentPadding`)이 얼마나 필요할까? `itemHeight`와 `visibleItemCount`(또는 컴포넌트 전체 높이)로 이 값을 어떻게 계산할 수 있을까?
   - 힌트: `trunk/wheel/src/kankan/wheel/widget/WheelView.java`의 `PADDING`, `ITEM_OFFSET_PERCENT`가 풀던 문제와 본질적으로 같은 문제다. 다만 View 버전은 캔버스를 직접 그려서 해결했고, 우리는 `contentPadding`만으로 해결한다는 게 다르다.
2. `SnapPosition.Center`는 정확히 무엇을 기준으로 "중앙"을 계산하는가? `LazyListSnapLayoutInfoProvider.kt`의 `calculateSnapOffset` / `calculateDistanceToDesiredSnapPosition` 호출부를 보면 뷰포트 크기(`singleAxisViewportSize`)와 아이템 오프셋을 어떻게 조합하는지 알 수 있다 — 여기서 컴포넌트 전체 높이를 어떻게 정해야 할지 힌트를 얻을 수 있다.
3. `itemHeight`를 고정 `Dp`로 받는 대신, 실제 배치된 아이템의 실측 높이를 쓰는 게 나을 수도 있다. 왜 그럴까? (힌트: 폰트 크기/디스플레이 밀도에 따라 `Dp` 지정값과 실제 렌더 높이가 다를 수 있는 경우가 있다.) 이번 단계에서는 고정값으로 시작해도 무방하지만, 나중에 문제가 될 수 있다는 것만 기억해두자.

## 막히면

- "스냅은 되는데 중앙이 아니라 위/아래 쪽에서 멈춘다" → 질문 1, 2 다시 확인.
- "플링이 뚝뚝 끊긴다" → `flingBehavior`를 실제로 `LazyColumn(flingBehavior = ...)`에 연결했는지, `remember`로 감싸서 매 리컴포지션마다 새로 만들어지지 않는지 확인.
- API 자체의 동작이 이해가 안 되면 `SnapFlingBehaviorSample.kt`를 그대로 실행해보고 우리 요구사항과 뭐가 다른지 비교.

## 완료 후

여기까지 되면 코드(또는 스크린샷/짧은 화면 녹화)를 보여줘. 같이 리뷰하고, 통과하면 `step-02-*.md`(선택값 추출)를 만들어줄게.
