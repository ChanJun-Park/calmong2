# Step 4 — 상태 호이스팅 + 복원 (`WheelPickerState`)

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-03-infinite-loop-mapping.md`](./step-03-infinite-loop-mapping.md)

## 목표

지금 `BasicSnapWheel` 내부에 흩어져 있는 것들 —

- `LazyListState` 생성과 초기 인덱스 계산
- `LARGE_NUMBER_OF_ITEMS` 상수
- `calculateCenterIndex` 매핑
- "몇 번이 선택됐는지"를 콜백(`onCenterItemIndexChange`)으로만 알 수 있고, 밖에서 "특정 값으로 점프시켜라" 같은 명령은 줄 수 없는 구조

를 Wear의 `PickerState`처럼 **하나의 재사용 가능한 상태 클래스**(`WheelPickerState`)로 캡슐화한다. 그리고 화면 회전이나 프로세스 재생성 후에도 선택값이 유지되도록 `rememberSaveable` + `Saver`를 적용한다.

## 왜 이 단계인가

맨 처음 android-wheel과 Wear Picker를 비교했을 때 짚었던 차이 중 하나가 이거였다 — *"`PickerState`가 `ScrollableState`를 구현하고 `rememberSaveable`로 복원 가능한데, android-wheel엔 이런 상태 저장/복원 개념 자체가 없었다."* 지금 우리 `BasicSnapWheel`도 아직 android-wheel과 같은 처지다: 상태가 컴포저블 내부에 갇혀 있고, 밖에서 제어할 수도 복원할 수도 없다. 이번 단계가 그 gap을 메꾸는 단계다.

```kotlin
// Picker.kt:594-600
@Stable
public class PickerState
constructor(
    initialNumberOfOptions: Int,
    initiallySelectedOption: Int = 0,
    public val repeatItems: Boolean = true,
) : ScrollableState {
```

`PickerState`가 `ScrollableState`를 직접 구현하는 게 핵심이다 — 이러면 Compose의 스크롤 생태계(제스처, `flingBehavior`, 접근성 스크롤 시맨틱)에 자연스럽게 편입된다. 우리도 같은 방향으로 간다.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| 클래스 선언, 생성자, `ScrollableState` 구현 | `reference/wear-compose-picker/Picker.kt:594-600`, `687-705` |
| 선택값 계산 (plain getter, `derivedStateOf` 아님) | `reference/wear-compose-picker/Picker.kt:640-642` |
| `rememberSaveable` + `Saver` 패턴 | `reference/wear-compose-picker/Picker.kt:672-684` (`listSaver`) |
| "특정 옵션으로 점프" — 무한 순환에서 더 가까운 방향 찾기 | `reference/wear-compose-picker/Picker.kt:644-670` (`scrollToOption`/`animateScrollToOption`), `707-718` (`getClosestTargetItemIndex`) |
| `derivedStateOf` 책임을 호출자에게 넘기라는 안내 | `reference/wear-compose-picker/Picker.kt:93-97` (kdoc) |
| 모듈로 유틸 | `reference/wear-compose-picker/Picker.kt:826` (`positiveModulo`) |

## 요구사항 명세 (시그니처만 제안)

```kotlin
@Composable
fun rememberWheelPickerState(
    itemCount: Int,
    initialSelectedIndex: Int = 0,
): WheelPickerState

@Stable
class WheelPickerState /* 생성자는 자유롭게 설계 */ : ScrollableState {
    val selectedIndex: Int

    suspend fun scrollToOption(index: Int)
    suspend fun animateScrollToOption(index: Int)

    // ScrollableState 멤버(scroll, dispatchRawDelta, isScrollInProgress,
    // canScrollForward, canScrollBackward)는 내부 LazyListState로 위임
}

@Composable
fun BasicSnapWheel(
    state: WheelPickerState,     // 이제 밖에서 생성해서 주입
    items: List<String>,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Dp = 40.dp,
    // onCenterItemIndexChange 콜백은 제거 대상 — state.selectedIndex를 직접 읽으면 된다
)
```

`items.size`와 `state`의 `itemCount`가 항상 일치해야 한다는 제약이 새로 생긴다 — 이걸 어떻게 보장/검증할지도 설계에 포함해야 한다. (이번 단계에서는 `itemCount`가 런타임에 바뀌는 경우까지는 다루지 않는다 — 예를 들어 월이 바뀌어 "일" 선택지 개수가 바뀌는 케이스는 Wear의 `optionsOffset` 같은 별도 보정 로직이 필요한데, 이건 나중 단계로 미룬다.)

## 완료 기준

- [ ] `BasicSnapWheel`이 더 이상 자기 내부에서 `rememberLazyListState`를 직접 만들지 않고, 바깥에서 주입받은 `WheelPickerState`를 사용한다.
- [ ] `onCenterItemIndexChange` 콜백 없이, 호출부에서 `state.selectedIndex`를 읽는 것만으로 현재 선택값을 알 수 있다.
- [ ] 화면 회전(또는 개발자 옵션의 "액티비티 유지 안 함")을 시켜도 선택된 값이 유지된다.
- [ ] `state.scrollToOption(index)` 호출 시, 무한 순환 목록에서 **항상 더 가까운 방향**으로 스크롤된다 — 예를 들어 옵션이 5개(0~4)이고 현재 0번이 선택된 상태에서 `scrollToOption(4)`를 호출하면, 앞으로 4칸이 아니라 **뒤로 1칸**만 움직여야 한다.
- [ ] `state.animateScrollToOption(index)`도 동일한 방향 선택 로직으로 애니메이션 스크롤된다.
- [ ] Step 1~3에서 확인했던 완료 기준(중앙 스냅, 무한 순환, `visibleItemCount` 3/5/7)이 전부 여전히 통과한다.

## 셀프 체크 질문

1. `getClosestTargetItemIndex`(`Picker.kt:707-718`)는 `positiveModulo(selectedOption - option, numberOfOptions)`로 "뒤로 가는 스텝 수"를, `positiveModulo(option - selectedOption, numberOfOptions)`로 "앞으로 가는 스텝 수"를 각각 구해서 더 짧은 쪽을 고른다. 옵션이 5개이고 0번에서 4번으로 갈 때 이 두 계산값이 각각 뭐가 나오는지 직접 손으로 계산해보고, 왜 그 결과가 "뒤로 1칸"을 의미하는지 설명해봐. 그리고 두 스텝 수가 정확히 같을 때(개수가 짝수일 때 정반대 위치) Wear는 어느 방향을 택하는지 코드에서 확인해봐(`stepsPrev <= stepsNext`의 부등호 방향이 힌트).
2. Wear의 `Saver`는 `listOf(it.numberOfOptions, it.selectedOption, it.repeatItems)`(`Picker.kt:676`)만 저장했다가 복원 시 **완전히 새로운 `PickerState`를 그 값들로 다시 생성**한다 — `LazyListState`의 스크롤 픽셀 오프셋 같은 저수준 값은 아예 저장하지 않는다. 우리 `WheelPickerState`도 "선택된 옵션 인덱스 + 아이템 개수"만 저장했다가 복원 시 초기 위치를 다시 계산하는 방식과, `LazyListState`가 자체적으로 가진 `Saver`(`rememberLazyListState`도 내부적으로 상태를 저장한다)에 얹혀가는 방식 중 어느 쪽이 더 간단하고 견고할까? 무한 순환 매핑(Step 3) 때문에 저수준 스크롤 위치를 그대로 복원하면 어떤 문제가 생길 수 있는지도 생각해봐.
3. `selectedIndex`를 클래스 안에서 plain getter(`Picker.kt:640-642`처럼 매번 계산)로 둘지, Step 2에서 했던 것처럼 `derivedStateOf`로 감싸서 캐싱해둘지 정해야 한다. Wear는 이 책임을 호출자에게 넘긴다(`Picker.kt:93-97`의 kdoc: "content description은 보통 `derivedStateOf`로 감싸서 쓰라"). 우리도 그렇게 할지, 아니면 Step 2처럼 클래스 내부에 캡슐화할지 정하고 이유를 설명해봐 — 정답은 없고, 트레이드오프를 이해하는 게 중요하다.
4. `WheelPickerState : ScrollableState`가 되면, 지금 `rememberCustomSnapFlingBehavior(lazyListState: LazyListState)`가 받는 파라미터 타입과 안 맞게 된다. `WheelPickerState`가 내부 `LazyListState`를 그대로 외부에 노출하는 프로퍼티를 두는 것과, `flingBehavior` 생성 로직 자체를 `WheelPickerState`를 아는 곳(예: `BasicSnapWheel` 내부, 혹은 `WheelPickerState`에 딸린 팩토리 함수)으로 옮기는 것 중 캡슐화 관점에서 뭐가 나을까?

## 막히면

- `scrollToOption`이 항상 앞으로만(또는 항상 뒤로만) 움직인다 → 질문 1의 두 스텝 수 계산과 부등호를 다시 점검.
- 회전 후 값이 초기화된다 → `rememberSaveable`에 `saver` 파라미터를 실제로 연결했는지, `Saver`의 `restore` 람다가 저장된 값으로 제대로 새 상태를 만드는지 확인.
- `LazyColumn`에 `state`를 넘기는 부분에서 타입이 안 맞는다 → 질문 4에서 결정한 설계(내부 `LazyListState` 노출 여부)를 다시 확인.

## 완료 후

되면 코드 보여줘. Step 5(시각 효과 — 그라데이션/중앙 하이라이트/거리 기반 스케일)로 넘어가자.
