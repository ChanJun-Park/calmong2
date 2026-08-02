# Step 7 — 접근성 (TalkBack)

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-06-fling-snap-curve.md`](./step-06-fling-snap-curve.md)

## 목표

`BasicSnapWheel`에 스크린리더(TalkBack) 지원을 추가한다: 지금 선택된 값을 읽어주고, TalkBack 사용자가 스와이프 같은 "조정" 제스처로 값을 바꿀 수 있게 만든다.

## 왜 이 단계인가

아주 처음(android-wheel 분석)으로 돌아가면, 이 프로젝트를 시작할 때 짚었던 android-wheel의 한계 중 하나가 "접근성 코드가 전혀 없다"는 거였어. Wear Picker는 `clearAndSetSemantics`로 이걸 해결하는데:

```kotlin
// Picker.kt:250-266
Modifier.clearAndSetSemantics {
    onClick {
        coroutineScope.launch { onSelected() }
        true
    }
    scrollToIndex {
        coroutineScope.launch {
            state.scrollToOption(it)
            onSelected()
        }
        true
    }
    if (!state.isScrollInProgress && contentDescription != null) {
        this.contentDescription = contentDescription
    }
    focused = !readOnly
}
```

근데 이건 Wear의 **로터리(다이얼)/베젤 입력** 모델에 맞춘 방식이야("인덱스로 점프 가능한 컬렉션"으로 표현). 폰에서 TalkBack을 쓰는 사용자는 보통 슬라이더나 네이티브 `NumberPicker`처럼 **"조정 가능한 값 컨트롤"**에 익숙해 — 포커스한 뒤 위/아래로 스와이프하면 값이 오르내리는 그 제스처. Material3 `Slider`가 실제로 이 패턴을 쓰고 있어서(`reference/compose-accessibility/`), 이번 단계는 Wear를 그대로 베끼는 대신 **폰 네이티브 관용구를 새로 가져온다.**

```kotlin
// Modifier.progressSemantics()
return semantics(mergeDescendants = true) {
    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
}

// Slider.kt의 sliderSemantics (일부)
semantics {
    stateDescription = state.value.formatForSemantics()
    setProgress(action = { targetValue -> /* ... */ })
}
```

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| `progressBarRangeInfo`를 설정하는 공식 헬퍼 | `reference/compose-accessibility/ProgressSemantics.kt` |
| `stateDescription` + `setProgress` 실사용 예 | Material3 `Slider.kt:3112-3170` (URL은 `reference/compose-accessibility/README.md` 참고, 파일이 커서 다운로드는 안 함) |
| Wear의 다른 접근 방식(대조군) | `reference/wear-compose-picker/Picker.kt:247-266` |
| `clearAndSetSemantics` vs `semantics` 차이를 실감할 대상 | 우리 `BasicSnapWheel`의 `LazyColumn` — 아이템이 10만 개(`LARGE_NUMBER_OF_ITEMS`) |

## 요구사항 명세 (시그니처만 제안)

```kotlin
@Composable
fun BasicSnapWheel(
    state: WheelPickerState,
    gradientColor: Color,
    modifier: Modifier = Modifier,
    visibleItemCount: Int = 5,
    itemHeight: Dp = 40.dp,
    optionLabel: (optionIndex: Int) -> String = { it.toString() }, // 접근성 안내용 텍스트 — 화면에 그리는 optionContent와는 별개
    optionContent: @Composable BoxScope.(optionIndex: Int) -> Unit,
)
```

`optionContent`는 시각적으로 그리는 내용이고, `optionLabel`은 TalkBack이 읽을 텍스트야 — 둘이 항상 일치할 필요는 없어(예: 아이콘만 그리는 옵션이라면 `optionLabel`이 그걸 말로 설명해줄 수도 있음).

## 완료 기준

- [ ] TalkBack을 켜고 이 위젯에 포커스를 맞추면 현재 선택된 값이 읽힌다.
- [ ] 위/아래로 스와이프하는 조정 제스처로 값이 실제로 바뀌고, 바뀐 값이 다시 안내된다.
- [ ] TalkBack으로 이 위젯을 탐색할 때, 10만 개 아이템 하나하나가 개별 탐색 대상으로 노출되지 않는다 — "조정 가능한 값 컨트롤 하나"로만 인식돼야 한다.
- [ ] Step 1~6에서 확인했던 스와이프/스냅/무한순환 동작이 여전히 정상 작동한다(접근성 시맨틱 추가가 실제 터치 인터랙션을 방해하면 안 됨).

## 셀프 체크 질문

1. `progressBarRangeInfo`의 `steps`는 "범위 안의 값 개수"가 아니라 **"최소~최대 사이의 이산 단계 수"**야. `Slider.kt`의 `for (i in 0..state.steps + 1) { lerp(start, end, i / (steps + 1)) }`를 보면 이 식이 총 몇 개의 값을 만들어내는지 세어봐. 우리 옵션이 `N`개(0..N-1)일 때 `steps`엔 정확히 얼마를 넣어야 할까?
2. `Modifier.progressSemantics()`는 `semantics(mergeDescendants = true)`를 쓰지, `clearAndSetSemantics`를 쓰지 않아. 우리 `BasicSnapWheel`은 안에 아이템이 10만 개인 `LazyColumn`인데, 이대로 `mergeDescendants = true`만 쓰면 어떻게 될까? 실제로 TalkBack을 켜서 확인해보고, 화면에 보이는 아이템들의 텍스트까지 다 읽힌다면 Wear처럼 `clearAndSetSemantics`로 감싸야 한다는 뜻이야.
3. `setProgress`의 `action` 콜백은 `(Float) -> Boolean`이라 `suspend`가 아니야. 그런데 실제로 값을 바꾸려면 `WheelPickerState.animateScrollToOption()`(suspend 함수)을 호출해야 해 — 이 안에서 코루틴을 어떻게 시작시킬 수 있을까? Wear의 `scrollToIndex` 액션도 똑같은 문제를 풀어야 했어(`Picker.kt:255-261`을 다시 보면 힌트가 있어).
4. `stateDescription`과 `contentDescription`의 역할이 어떻게 다를까? Slider가 굳이 값이 바뀔 때마다 `stateDescription`을 갱신하는 이유가 뭘지 생각해봐 — "이 컨트롤이 뭔지에 대한 고정된 설명"과 "지금 이 컨트롤의 상태"는 다른 개념이라는 게 힌트야.
5. (선택, 더 파고들고 싶다면) `setProgress`의 `action`이 받는 `targetValue: Float`는 TalkBack이 "이 값으로 맞춰줘"라고 요청하는 임의의 실수값이야. 이걸 우리 옵션 인덱스(정수)로 반올림/스냅할 때, `Slider.kt`가 하는 것처럼 `steps`를 고려한 반올림이 필요할지, 아니면 단순히 `targetValue.roundToInt()`로 충분할지 판단해봐.

## 막히면

- TalkBack이 아무것도 안 읽는다 → `clearAndSetSemantics`/`semantics` 모디파이어가 실제로 `LazyColumn`(또는 그걸 감싸는 `Box`)에 적용됐는지, `mergeDescendants` 관련 설정이 맞는지 확인.
- 스와이프 조정 제스처가 안 먹는다 → `progressBarRangeInfo`와 `setProgress`가 반드시 **같은 시맨틱 노드**에 함께 있어야 해 — 따로 떨어진 모디파이어에 나눠 넣으면 인식이 안 될 수 있어.
- 값은 바뀌는데 안내가 갱신 안 된다 → `stateDescription`을 계산하는 부분이 리컴포지션에 반응하는 상태(`state.selectedOption` 읽기)를 실제로 참조하고 있는지 확인 — Step 2에서 다뤘던 "무엇을 읽어야 리컴포지션이 트리거되는가"와 같은 종류의 문제야.

## 완료 후

여기까지 되면 로드맵 8단계 중 7개가 끝나 — 마지막 Step 8(년/월/일 여러 개의 피커를 조합해서 실제 날짜 선택 컴포넌트로 완성)만 남아. 결과 보여줘.
