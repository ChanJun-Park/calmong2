# Step 3 — 무한 순환 매핑

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-02-center-item-index.md`](./step-02-center-item-index.md)

## 목표

지금까지 `BasicSnapWheel`은 `items: List<String>`의 처음과 끝이 진짜 끝이었다 (0번 아래로도, 마지막 번 위로도 못 감). 이번 단계에서는 **마지막 아이템을 지나면 다시 처음 아이템으로, 처음 아이템에서 반대로 스크롤하면 마지막 아이템으로** 이어지는 무한 순환 스크롤을 만든다.

## 왜 이 단계인가

`ScalingLazyColumn`이 "무한 스크롤"을 공짜로 제공하는 게 아니다. Wear `PickerState`(`reference/wear-compose-picker/Picker.kt:594-727`)를 다시 보면, 실제로는:

1. **아주 큰 고정 개수**(`LARGE_NUMBER_OF_ITEMS`)의 아이템을 리스트에 두고
2. 그 큰 인덱스를 실제 옵션 개수(`numberOfOptions`)로 **모듈로 매핑**해서 어떤 텍스트를 보여줄지 결정하고
3. 리스트의 시작 위치를 **그 큰 범위의 정중앙 근처**로 잡아서, 사용자가 어느 방향으로 스크롤하든 당분간 끝에 부딪히지 않게 한다

는 트릭이다. "진짜 무한"이 아니라 "충분히 커서 실질적으로 끝에 닿을 일이 없는 유한 리스트"인 셈이다.

핵심 코드:
```kotlin
// Picker.kt:622
internal fun numberOfItems() = if (!repeatItems) numberOfOptions else LARGE_NUMBER_OF_ITEMS

// Picker.kt:634-638 — 시작 위치를 "정중앙 근처"로 설정
internal val scalingLazyListState = run {
    val repeats = if (repeatItems) LARGE_NUMBER_OF_ITEMS / numberOfOptions else 1
    val centerOffset = numberOfOptions * (repeats / 2)
    ScalingLazyListState(centerOffset + initiallySelectedOption, 0)
}

// Picker.kt:642 — 실제 화면 인덱스를 옵션 인덱스로 되돌리는 매핑
public val selectedOption: Int
    get() = (scalingLazyListState.centerItemIndex + optionsOffset) % numberOfOptions

// Picker.kt:720-726 — 왜 "충분히 커야" 하는지에 대한 안전장치
private fun verifyNumberOfOptions(numberOfOptions: Int) {
    require(numberOfOptions > 0) { "The picker should have at least one item." }
    require(numberOfOptions < LARGE_NUMBER_OF_ITEMS / 3) {
        "The picker should have less than ${LARGE_NUMBER_OF_ITEMS / 3} items"
    }
}
```

`numberOfOptions < LARGE_NUMBER_OF_ITEMS / 3`라는 제약이 흥미로운데, 이게 "최소 3번은 반복되도록" 강제하는 조건이다 — 왜 하필 3번인지는 셀프 체크 질문에서 직접 생각해보자.

이번 단계에서는 `optionsOffset`(옵션 개수가 **런타임에 바뀔 때** 현재 선택을 유지하기 위한 보정값)까지는 다루지 않는다 — 지금은 `items`의 크기가 고정이라고 가정한다. `optionsOffset`은 나중에(예: 월이 바뀌면 일(day) 선택지 개수가 바뀌는 경우) 다시 다룰 것이다.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| 무한 순환 매핑의 전체 설계 | `reference/wear-compose-picker/Picker.kt:594-727` (`PickerState` 클래스 전체) |
| 모듈로 연산 유틸 | `reference/wear-compose-picker/Picker.kt:826` (`positiveModulo`) |
| 같은 문제를 View 시절에 어떻게 풀었는지 (인덱스 자체를 순환시키는 방식 — 대조군) | `trunk/wheel/src/kankan/wheel/widget/WheelView.java`의 `doScroll()`, `setCurrentItem()` — `while (index < 0) index += itemCount; index %= itemCount;` |

## 요구사항 명세 (시그니처는 유지, 내부 구현만 변경)

`BasicSnapWheel`의 바깥 계약(호출부에서 보는 시그니처)은 그대로 둔다 — `onCenterItemIndexChange`가 **여전히 `0 until items.size` 범위의 "실제 옵션 인덱스"를 알려줘야 한다.** 내부적으로만:

- `items(items = items)` (크기 `items.size`짜리 유한 리스트) 대신, `items(count = LARGE_ITEM_COUNT)`처럼 아주 큰 개수를 렌더링하고, 각 화면 인덱스를 `실제 인덱스 = 화면 인덱스 % items.size`로 매핑해서 텍스트를 뽑아 쓴다.
- 리스트의 초기 스크롤 위치를 `LARGE_ITEM_COUNT`의 정중앙 근처(+ 초기 선택값 offset)로 설정한다 (`rememberLazyListState(initialFirstVisibleItemIndex = ...)`).
- Step 2에서 만든 `calculateCenterIndex`가 반환하는 값은 이제 "화면 인덱스"(클 수 있음)이므로, 콜백으로 내보내기 전에 `% items.size` 매핑을 한 번 더 거쳐야 한다.

## 완료 기준

- [ ] `items`의 마지막 아이템을 지나 계속 스크롤하면 첫 번째 아이템부터 다시 이어진다 (반대 방향도 마찬가지).
- [ ] `onCenterItemIndexChange`로 전달되는 값은 항상 `0 until items.size` 범위 안이다 (거대한 화면 인덱스가 그대로 새어나가지 않는다).
- [ ] `items.size`가 3처럼 작을 때와 60처럼(예: 분(minute) 선택지) 클 때 모두 정상 동작한다.
- [ ] 위/아래로 빠르게 여러 번 플링해도(현실적인 조작 범위 안에서) 리스트의 진짜 끝(0번 또는 `LARGE_ITEM_COUNT - 1`번)에 부딪히지 않는다.
- [ ] Step 1·2에서 만든 스냅/중앙 계산 로직은 수정 없이 그대로 동작한다 (인덱스가 커졌을 뿐 좌표 계산 방식 자체는 바뀌지 않으므로).

## 셀프 체크 질문

1. `LARGE_ITEM_COUNT`를 `items.size`에 비해 얼마나 크게 잡아야 할까? Wear의 `verifyNumberOfOptions`는 `numberOfOptions < LARGE_NUMBER_OF_ITEMS / 3`를 요구해서 **최소 3번 반복**을 보장한다 — 왜 하필 "최소 3번"일까? (힌트: 시작 위치가 정중앙 근처라고 해도, 한쪽 방향으로만 아주 멀리 스크롤하면 반대쪽 반복 구간은 아직 남아있어야 한다. 2번 반복이면 부족하고 3번이면 충분한 이유를 좌우 여유 공간 관점에서 설명해봐.)
2. 화면 인덱스를 실제 인덱스로 바꾸는 연산이 `index % items.size`로 충분할까, 아니면 `positiveModulo`(음수 보정)까지 필요할까? `LazyColumn`의 `firstVisibleItemIndex`/아이템 인덱스가 **음수가 될 수 있는지** 먼저 확인해봐 — `WheelView.java`의 `doScroll()`은 왜 명시적으로 음수 보정(`while (index < 0) index += itemCount`)이 필요했는지와 비교하면서 생각해보면 답이 나온다. (힌트: `WheelView`는 `currentItem`이라는 정수를 직접 증감시켜서 음수가 될 수 있었지만, `LazyColumn`의 아이템 인덱스는 애초에 그 리스트의 실제 인덱스 범위를 벗어날 수 없다.)
3. 초기 스크롤 위치(`initialFirstVisibleItemIndex`)를 "정중앙 근처"로 잡을 때, Step 1에서 만든 `contentPadding` 기반 중앙 정렬 로직과 궁합이 맞으려면 `initialFirstVisibleItemScrollOffset`은 어떤 값이어야 할까? (힌트: Step 1의 `verticalContentPadding` 설계를 다시 떠올려봐 — 그 패딩이 정확할 때 `offset = 0`이면 뭐가 보장되는지.)
4. 지금 설계는 `items`의 개수가 고정이라고 가정한다. 만약 나중에(로드맵 8단계, 날짜 피커) 월이 바뀌어서 "일" 선택지 개수가 28~31 사이로 바뀐다면, 지금 매핑 방식(화면 인덱스 % 고정 크기)에 어떤 문제가 생길지 미리 한번 생각해봐 — 지금 당장 풀 필요는 없다.

## 막히면

- 스크롤하다가 리스트가 갑자기 멈추거나 끝에 닿는다 → `LARGE_ITEM_COUNT`가 충분히 큰지, 초기 위치가 정말 "중앙 근처"인지 확인.
- `onCenterItemIndexChange`에 이상하게 큰 숫자가 찍힌다 → Step 2의 `calculateCenterIndex` 결과에 `% items.size` 매핑을 빼먹지 않았는지 확인.
- 처음 화면에 뜨는 값이 의도한 값이 아니다 → 질문 3의 초기 오프셋 계산을 다시 점검.

## 완료 후

되면 코드 보여줘. Step 4(상태 호이스팅 — 지금까지 `BasicSnapWheel` 내부에 흩어져 있던 로직을 `PickerState` 하나로 캡슐화하고 `Saver`로 복원 가능하게 만들기)로 넘어가자.
