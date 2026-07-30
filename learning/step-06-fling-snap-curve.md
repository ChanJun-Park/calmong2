# Step 6 — 플링/스냅 커브 튜닝 (선택)

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-05-visual-effects.md`](./step-05-visual-effects.md)

## 목표

지금 `rememberCustomSnapFlingBehavior`는 공식 `snapFlingBehavior(decayAnimationSpec = ..., snapAnimationSpec = ...)` 팩토리를 쓴다 — 이건 내부적으로 **두 단계**로 동작한다: ① `decayAnimationSpec`으로 감속 접근 → ② `snapAnimationSpec`(지금은 `spring`)으로 목표 아이템에 정착. 이번 단계에서는 이 두 단계 전환이 실제로 눈에 보이는 "이음매"를 만드는지 직접 확인하고, 필요하다면 Wear Picker의 기본 fling(`ScalingLazyColumnSnapFlingBehavior`)처럼 **감속과 정착을 하나의 연속된 커브로 합친 완전 커스텀 `FlingBehavior`**를 만들어본다.

**"선택" 단계다** — Part 1(확인)까지만 하고 "이음매가 안 느껴진다"고 판단되면 여기서 Step 7로 넘어가도 된다.

## 왜 이 단계인가 — 지난번 `translationY` 논의와 같은 문제

Step 5 마지막에 확인했던 것 기억나? `translationY`에서 clamp된 값을 쓰면 두 구간(2차식 구간과 clamp 이후 구간)의 **기울기(변화율)가 경계에서 안 맞아서** 눈에 보이는 끊김이 생겼었지. 지금 fling도 정확히 같은 종류의 문제를 가질 수 있어: 감속 애니메이션(①)이 끝나는 순간의 속도와, 정착 애니메이션(②)이 시작하는 순간의 속도가 다르면 그 전환 지점에서 똑같이 "끊김"이 생겨.

Wear Picker의 실제 fling(`ScalingLazyColumnSnapFlingBehavior.kt`, Step 1에서 받아둔 원본)은 이 문제를 아예 원천적으로 없앤다 — 감속과 정착을 **하나의 큐빅 베지어 커브**로 합쳐버려서 "전환 지점"이라는 게 존재하지 않게 만든다.

```kotlin
// ScalingLazyColumnSnapFlingBehavior.kt:113-134
val initialSpeed = animationState.velocity

// Inertia of the initial speed.
val initialInertia = 0.5f

val finalSnapDuration = lerp(FINAL_SNAP_DURATION_MIN, FINAL_SNAP_DURATION_MAX, abs(initialSpeed) / SNAP_SPEED_THRESHOLD)

// Initial control point. Has slope (velocity) adjustedSpeed and magnitude (inertia) initialInertia
val adjustedSpeed = initialSpeed * finalSnapDuration / distance
val easingX0 = initialInertia / sqrt(1f + adjustedSpeed * adjustedSpeed)
val easingY0 = easingX0 * adjustedSpeed

animationState.animateTo(
    finalTarget,
    tween((finalSnapDuration * 1000).roundToInt(), easing = CubicBezierEasing(easingX0, easingY0, easingX1, easingY1)),
) { ... }
```

`easingX0`/`easingY0`가 정확히 뭘 하는지 보면: 큐빅 베지어의 **시작 제어점의 기울기**(`easingY0/easingX0` = `adjustedSpeed`)를 감속이 끝나는 순간의 실제 속도(`initialSpeed`)로 맞춰준다. 즉 "정착 애니메이션의 시작 기울기 = 감속 애니메이션이 끝난 시점의 기울기"를 수학적으로 강제해서 이어붙이는 거야 — 지난번에 `translationY`에서 손으로 확인했던 "경계에서 기울기를 맞춰야 끊김이 없다"는 원리를 그대로, 훨씬 정교하게 적용한 사례.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| Wear의 단일 커브 fling (이미 받아둔 파일) | `reference/wear-compose-picker/ScalingLazyColumnSnapFlingBehavior.kt` 전체 |
| 지금 우리가 쓰는 공식 2단계 fling (비교 대상) | `reference/compose-foundation-snapping/SnapFlingBehavior.kt` (`SnapFlingBehavior.fling()` — `tryApproach` 후 별도로 `animateWithTarget` 호출하는 구조) |
| 우리 프로젝트의 현재 fling 연결부 | `BasicSnapWheel.kt`의 `rememberCustomSnapFlingBehavior`, `CustomLazyListSnapLayoutInfoProvider.kt` |

## Part 1 — 먼저 확인부터

**질문**: 지금 두 단계 방식이 실제로 이음매가 느껴져? 확인 방법 제안:
- 아주 세게 플링해서, 감속 구간에서 정착 구간으로 넘어가는 순간에 속도가 갑자기 변하는 느낌이 있는지 관찰
- `snapAnimationSpec`을 일부러 극단적인 값(예: `spring(stiffness = Spring.StiffnessHigh)`)으로 바꿔서, 이음매가 있다면 훨씬 두드러지게 만들어서 확인 — 있다면 이렇게 하면 확실히 보일 거고, 없다면 이 방법으로도 안 보일 거야.

**여기서 "안 느껴진다"고 결론 나면 Part 2는 건너뛰고 Step 7로 넘어가도 된다.** 지금 우리 스냅 거리가 짧고(`itemHeightWithPadding` 한두 칸), `spring(stiffness = StiffnessLow)`가 원래 부드러운 편이라 이음매가 안 느껴질 가능성도 충분히 있어.

## Part 2 — 완전 커스텀 `FlingBehavior` (진행하기로 했다면)

**요구사항 명세**:
```kotlin
class WheelSnapFlingBehavior(
    private val lazyListState: LazyListState,
    private val decay: DecayAnimationSpec<Float> = exponentialDecay(),
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Wear의 performFling 구조를 참고해서 직접 구현
    }
}
```

`ScalingLazyColumnSnapFlingBehavior.kt`의 `performFling`을 그대로 베끼기보다, 구조를 이해하고 우리 것에 맞게 옮기는 걸 목표로 해:
1. `animateDecay`로 감속시키면서 매 프레임 `scrollBy` 호출
2. 속도가 `SNAP_SPEED_THRESHOLD` 밑으로 떨어지면 감속을 멈추고
3. 그 시점의 실제 속도로 제어점 기울기를 맞춘 큐빅 베지어를 만들어서 목표 아이템까지 `animateTo`

**완료 기준**
- [ ] 세게 플링해도, 약하게 플링해도 감속→정착 전환이 자연스럽다 (Part 1에서 세팅한 "확인 방법"으로 재검증)
- [ ] 리스트 끝(우리 경우엔 무한 순환이라 해당 없음)이나 극단적으로 짧은 플링에서도 깨지지 않는다
- [ ] Step 1~5의 기존 완료 기준이 전부 유지된다

**셀프 체크 질문**
1. `animateDecay`가 멈추는 조건(`abs(velocity) < SNAP_SPEED_THRESHOLD`)과, 우리가 이미 만든 `CustomSnapLayoutInfoProvider`의 "항상 플링 방향으로 스냅"(`calculateSnapOffset`의 `if (velocity > 0) upperBoundOffset else lowerBoundOffset`) 로직이 같이 동작하면 서로 영향을 줄까? 둘 다 "속도"를 기준으로 판단을 내리는 로직이라는 걸 염두에 두고 생각해봐.
2. `WheelPickerState`가 `ScrollableState`를 구현하고 있고, 지금 `flingBehavior`는 `state.lazyListState`를 직접 받아서 만들어져. 커스텀 `FlingBehavior`로 바꾸면 이 연결 지점이 `WheelPickerState` 안으로 들어가야 할지, 지금처럼 `BasicSnapWheel` 쪽에 남아도 될지 — Step 4에서 논의했던 캡슐화 기준으로 다시 판단해봐.
3. `easingX0`/`easingY0` 공식에서 `initialInertia = 0.5f`가 하는 역할이 뭘까? 이 값을 0에 가깝게, 또는 1에 가깝게 바꿔보면서 시각적으로 어떤 차이가 나는지 실험해봐도 좋아.

## 막히면

- 커스텀 fling을 만들었는데 오히려 더 어색하다 → Part 1에서 "이음매가 안 느껴진다"고 판단했다면 억지로 Part 2를 안 해도 된다는 걸 기억해.
- `animateDecay`가 끝나기 전에 목표를 지나쳐버린다 → `finalTarget` 계산(가장 가까운 스냅 위치)이 감속이 끝나는 시점 기준으로 다시 계산되고 있는지 확인.

## 완료 후

Part 1만 했든 Part 2까지 했든, 결과 알려줘. Step 7(접근성)로 넘어가자.
