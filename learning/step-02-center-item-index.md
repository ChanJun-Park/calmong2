# Step 2 — 선택된 값(중앙 아이템 인덱스) 추출

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-01-basic-snap-list.md`](./step-01-basic-snap-list.md)

## 목표

`BasicSnapWheel`이 스크롤되는 동안, **지금 뷰포트 정중앙에 가장 가까운 아이템의 index**를 실시간으로 계산해서 바깥(호출부)에 알려주도록 만든다. 아직 무한 스크롤 매핑(Step 3)이나 `PickerState` 캡슐화(Step 4)는 하지 않는다 — 지금 다루는 유한한 `items: List<String>` 안에서 "지금 몇 번이 선택돼 있나"만 정확히 뽑아낸다.

## 왜 이 단계인가

Wear의 `ScalingLazyListState`는 `centerItemIndex`를 프레임워크가 이미 계산해서 제공한다 — `Picker.kt:864-865`의 `PickerRotarySnapLayoutInfoProvider.currentItemIndex`가 그냥 `scalingLazyListState.centerItemIndex`를 읽기만 하는 걸 봤을 것이다. 하지만 우리가 쓰는 일반 `LazyListState`에는 이런 편의 프로퍼티가 없다. **직접 계산**해야 한다.

사실 이 계산은 완전히 새로운 문제가 아니다 — Step 1에서 참고했던 `LazyListSnapLayoutInfoProvider.calculateSnapOffset()`(`reference/compose-foundation-snapping/LazyListSnapLayoutInfoProvider.kt:66-100`)이 내부적으로 이미 "뷰포트 중앙에 가장 가까운 아이템"을 찾고 있다. 스냅 애니메이션은 이 계산 결과(offset)를 "얼마나 스크롤할지"에 쓰지만, 우리는 같은 계산 결과를 "몇 번 아이템인지"를 얻는 데 쓸 것이다 — 같은 정보를 다른 목적으로 재사용하는 셈이다.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| 아이템 정보(인덱스/오프셋/크기) | `reference/compose-foundation-snapping/LazyListItemInfo.kt` — `index`, `offset`(리스트 컨테이너 시작 기준), `size` |
| 뷰포트 정보 | `reference/compose-foundation-snapping/LazyListLayoutInfo.kt` — `visibleItemsInfo`, `viewportStartOffset`, `viewportEndOffset`, `viewportSize` |
| "중앙에 가장 가까운 아이템 찾기"의 원형 로직 | `reference/compose-foundation-snapping/LazyListSnapLayoutInfoProvider.kt:66-100` (`calculateSnapOffset`) — 각 아이템까지의 거리를 구해서 가장 작은 쪽을 고르는 방식 |
| 같은 개념이 Wear에서 어떻게 노출되는지 (계산 코드는 없고 인터페이스만) | `reference/wear-compose-picker/Picker.kt:850-873` (`PickerRotarySnapLayoutInfoProvider`) |
| `derivedStateOf`로 불필요한 리컴포지션을 피하라는 공식 가이드 | `reference/wear-compose-picker/Picker.kt:96-97` (Picker 함수 kdoc 주석) |

## 요구사항 명세 (시그니처만 제안)

```kotlin
@Composable
fun rememberCenterItemIndex(state: LazyListState): State<Int>
```

그리고 `BasicSnapWheel`에 콜백 파라미터를 하나 추가해서 바깥으로 흘려보낸다:

```kotlin
@Composable
fun BasicSnapWheel(
    items: List<String>,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Dp = 40.dp,
    onCenterItemIndexChange: (Int) -> Unit = {},
)
```

(정확한 시그니처는 자유롭게 바꿔도 된다 — 예를 들어 콜백 대신 리턴값으로 `State<Int>`를 노출하는 방식도 가능. 다만 "왜 그 방식을 골랐는지" 설명할 수 있어야 한다.)

## 완료 기준

- [ ] 리스트를 스크롤하는 동안(드래그 중, 스냅 완료 전에도) 화면에 "현재 선택: N" 같은 텍스트가 실시간으로 갱신된다.
- [ ] 스냅이 끝난 상태에서 화면 정중앙에 보이는 숫자와, "현재 선택: N"에 찍히는 값이 **항상 일치**한다.
- [ ] `visibleItemCount`를 3/5/7로 바꿔도 정확하다.
- [ ] 스크롤 안 하고 가만히 있을 때는 값이 계속 재계산/재구성되지 않는다 (리컴포지션 카운터를 하나 찍어보거나, Layout Inspector의 recomposition 카운트로 확인).

## 셀프 체크 질문

1. 아이템의 "중심 좌표"는 `item.offset`과 `item.size`로 어떻게 표현할 수 있을까?
2. 뷰포트의 "중심 좌표"는 `viewportStartOffset`, `viewportEndOffset`(또는 `viewportSize`)으로 어떻게 표현할 수 있을까? `LazyListLayoutInfo.kt`의 각 필드 주석을 다시 읽어봐 — `viewportStartOffset`이 `beforeContentPadding`이 있을 때 음수가 될 수 있다는 설명이 힌트다.
3. `visibleItemsInfo`를 순회하면서 "중심까지의 거리가 가장 작은 아이템"을 찾는 로직을 직접 짜본다면, `calculateSnapOffset()`의 방식(거리가 0 이하인 것 중 최대, 0 이상인 것 중 최소 — 두 후보를 따로 추적)과, 그냥 "절댓값 거리 최소값 하나만 추적"하는 방식 중 뭐가 더 간단할까? 이번 단계에서는 어느 쪽으로 가도 되지만, 왜 그 원본 코드가 굳이 두 후보를 나눠 추적했는지 한번 생각해봐 (힌트: 짝수 거리로 두 아이템이 동일하게 가까운 경우를 어떻게 다룰지와 관련 있다).
4. `state.layoutInfo`를 매 스크롤 프레임마다 읽는 건 자연스러운데, 이 값에서 계산한 "중앙 인덱스"를 그냥 `val centerIndex = calculate(state.layoutInfo)`로 매번 다시 계산하는 것과, `remember { derivedStateOf { calculate(state.layoutInfo) } }`로 감싸는 것은 리컴포지션 관점에서 뭐가 다를까? (힌트: 인덱스 값 자체는 스크롤 1px마다 바뀌지 않는다 — 여러 프레임 동안 같은 값을 유지하다가 어느 순간 1 증가/감소한다.)
5. 지난 대화에서 android-wheel의 `OnWheelChangedListener`(값이 바뀔 때)와 `OnWheelScrollListener`(스크롤이 끝났을 때)가 분리돼 있었던 걸 다뤘다. 이번 `onCenterItemIndexChange`는 드래그 중에도 실시간으로 불리게 설계했는데, 이건 android-wheel의 어느 리스너에 더 가까운가? 스냅이 완전히 끝났을 때만 알림을 받고 싶다면 무엇을 추가로 확인해야 할까? (`state.isScrollInProgress`가 힌트)

## 막히면

- 계산된 인덱스가 화면에 보이는 값보다 항상 하나 어긋난다 → 질문 1, 2의 "중심 좌표" 계산식을 다시 점검. 특히 `item.offset`이 뷰포트 기준인지 콘텐츠 시작 기준인지(`LazyListItemInfo.kt` 주석: "relative to the start of the lazy list container") 확인.
- 리컴포지션이 너무 자주 일어난다 → `derivedStateOf` 적용 여부, 그리고 `rememberCenterItemIndex` 자체를 `remember(state)`로 감쌌는지 확인.

## 완료 후

되면 코드 보여줘. 리뷰하고 통과하면 Step 3(무한 순환 매핑 — `LARGE_NUMBER_OF_ITEMS` + `optionsOffset` 트릭)으로 넘어가자.
