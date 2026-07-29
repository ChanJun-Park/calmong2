# Step 5 — 시각 효과 (그라데이션 · 중앙 하이라이트 · 거리 기반 스케일)

> 로드맵: [`00-roadmap.md`](./00-roadmap.md) · 이전 단계: [`step-04-state-hoisting.md`](./step-04-state-hoisting.md)

## 목표

지금 `basicSnapWheelModifier`에 있는 디버그용 격자선/하이라이트 사각형(`BasicSnapWheel.kt`의 `drawLine`/`drawRect` 반복문)을 걷어내고, 실제 "휠"처럼 보이는 시각 효과 세 가지를 넣는다:

1. **상/하 페이드 그라데이션** — 위아래 아이템이 흐려지며 사라지는 효과
2. **중앙 선택 하이라이트** — 지금 선택된 값이 있는 자리를 시각적으로 강조
3. **거리 기반 스케일/알파** — 중앙에서 멀어질수록 아이템이 작아지고(선택적으로 흐려지고) 보이는 원통형 휠 착시

## 왜 이 단계인가

이 프로젝트를 시작할 때 android-wheel을 분석하면서 짚었던 UI 노하우들 — `SHADOWS_COLORS` 그라데이션 셰도우, `wheel_val.xml` 중앙 강조 바, `ITEM_OFFSET_PERCENT`로 위아래를 살짝 잘라 보이게 하는 트릭 — 을 이제 Compose로 구현할 차례다. 다만 이번엔 Wear Picker가 어떻게 "정확히 같은 목적"을 Compose로 풀었는지가 더 직접적인 참고가 된다.

**그라데이션**은 android-wheel의 `GradientDrawable` 오버레이와 목적이 같고, Wear는 `Modifier.drawWithContent { drawContent(); drawGradient(...) }`로 구현한다(`Picker.kt:268-276`, `544-560`).

**거리 기반 스케일**은 android-wheel엔 없던 개념이다(View 버전은 `ITEM_OFFSET_PERCENT`로 위아래를 "잘라서" 보이게 하는 정적인 트릭이었지, 실제로 크기가 변하진 않았다). Wear의 `ScalingLazyColumn`은 아이템이 중앙에서 멀어질수록 실제로 작아지고(옵션에 따라) 투명해진다 — 이번에 `ScalingLazyColumnMeasure.kt`의 `calculateScaleAndAlpha`(317~358행) 원본을 받아왔다. 이 알고리즘은 "아이템의 가장자리가 뷰포트 가장자리로부터 얼마나 떨어졌는가"를 기준으로 삼는데, 우리는 지금까지 이미 "아이템 중심 vs 뷰포트 중심" 거리를 계산해왔으니(`centerItemIndex()`), 그걸 재사용해서 **더 단순한 버전**(아이템 중심 기준 거리)으로 시작할 것을 제안한다. 정확히 Wear와 똑같이 만들 필요는 없다 — 개념(정규화된 거리 → 이징 → `lerp`로 scale/alpha 보간)만 가져오면 된다.

## 참고 소스

| 볼 것 | 위치 |
|---|---|
| 그라데이션 그리기 | `reference/wear-compose-picker/Picker.kt:544-560` (`drawGradient`), `268-276`(적용부) |
| 아이템별 오프스크린 컴포지팅 | `reference/wear-compose-picker/Picker.kt:307-310` (`graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`) |
| 거리 기반 스케일/알파 알고리즘(원본, 참고용) | `reference/wear-scaling-lazy-column/ScalingLazyColumnMeasure.kt:317-358` (`calculateScaleAndAlpha`) |
| 기본 파라미터 값(edgeScale/edgeAlpha/minTransitionArea 등) | `reference/wear-compose-picker/Picker.kt:773-792` (`defaultScalingParams`) — `edgeScale=0.45f`, `edgeAlpha=1.0f` (기본값은 스케일만 바뀌고 알파는 안 바뀜) |
| 우리가 이미 만든 "중심까지의 거리" 계산 | `BasicSnapWheel.kt`의 `LazyListState.centerItemIndex()` — 이번엔 "가장 가까운 아이템 찾기"가 아니라 "각 아이템마다 거리 구하기"로 일반화해야 함 |
| View 시절 대응 기법(대조군) | `trunk/wheel/src/kankan/wheel/widget/WheelView.java`의 `drawShadows()`, `SHADOWS_COLORS`, `drawCenterRect()` |

## 요구사항 명세 (시그니처만 제안)

세 가지를 각각 독립된 조각으로 만들어보자.

**① 그라데이션**
```kotlin
fun Modifier.fadeEdges(gradientColor: Color, gradientRatio: Float = 0.33f): Modifier
```
`BasicSnapWheel`의 `LazyColumn`을 감싸는 `Box`(혹은 `LazyColumn` 자신)의 modifier 체인에 추가.

**② 중앙 하이라이트**
정적인 오버레이라서 스크롤 상태를 몰라도 된다 — `Box`로 겹쳐 그리거나, `drawWithContent`에서 뷰포트 정중앙에 고정된 사각형/선을 그리면 된다. android-wheel의 `wheel_val.xml`(반투명 그라데이션 바)을 참고해도 좋고, 완전히 다른 스타일(예: 위아래 구분선만)로 가도 된다 — 디자인은 자유.

**③ 거리 기반 스케일/알파**
```kotlin
fun Modifier.wheelItemScaling(
    itemIndex: Int,
    lazyListState: LazyListState,
    edgeScale: Float = 0.6f,
    edgeAlpha: Float = 1.0f, // Wear 기본값처럼 알파는 안 바꾸는 것부터 시작해도 됨
): Modifier
```
`optionContent`를 감싸는 `Box`의 modifier에 아이템 인덱스별로 적용.

## 완료 기준

- [ ] 위/아래 아이템이 자연스럽게 흐려지며 사라진다 (그라데이션이 배경색과 이질감 없이 이어짐 — 하드코딩된 색 대신 실제 배경색을 파라미터로 받아야 함).
- [ ] 중앙에 선택된 값이 시각적으로 명확히 구분된다.
- [ ] 중앙에서 멀어질수록 아이템이 점점 작아진다. `visibleItemCount`를 3/5/7로 바꿔도 자연스럽게 동작한다.
- [ ] 스크롤 중에 스케일이 프레임 단위로 부드럽게 갱신된다 (뚝뚝 끊기지 않음).
- [ ] 디버그용 격자선/사각형 코드가 완전히 제거됐다.
- [ ] Step 1~4에서 확인했던 완료 기준(스냅/무한순환/상태 호이스팅)이 여전히 전부 통과한다.

## 셀프 체크 질문

1. Wear의 기본 설정은 `edgeAlpha = 1.0f`라서 사실 **알파는 안 바뀌고 스케일만 바뀐다.** 스케일과 알파를 둘 다 적용하는 것과 스케일만 적용하는 것, 어느 쪽이 우리 휠에 더 잘 어울릴지 실제로 둘 다 만들어보고 비교해봐.
2. Wear가 아이템별로 `graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }`을 쓰는 이유는, `ScalingLazyColumn`이 스케일된 아이템의 위치를 재배치하는 과정에서 이웃 아이템과 경계가 살짝 겹칠 수 있어서 알파 블렌딩이 이중으로 겹치는 걸 막기 위해서야(`ScalingLazyColumnMeasure.kt`의 `calculateItemInfo`를 보면 스케일된 아이템의 위치를 다시 계산하는 부분이 있어). **우리 방식**(각 아이템이 자기 행 높이 안에서만 축소되고, 이웃 행과 겹치지 않는 경우)도 똑같이 오프스크린 컴포지팅이 필요할까? 알파를 실제로 적용해보고, `compositingStrategy` 유무에 따라 눈에 보이는 차이가 있는지 직접 확인해봐 — 필요 없다면 안 넣어도 된다.
3. `Modifier.graphicsLayer { }`(람다를 받는 오버로드)와 `Modifier.graphicsLayer(scaleX = value, ...)`(파라미터를 직접 받는 오버로드) 중 스크롤에 반응하는 시각 효과에는 어느 쪽을 써야 할까? 람다 버전은 그리기 단계에서 매 프레임 실행되고, 파라미터 버전은 `scaleX` 값이 바뀔 때마다 그 값을 계산하는 컴포저블이 리컴포지션돼야 해. Step 2~3에서 "스크롤 중 불필요한 리컴포지션을 어떻게 피할까"를 고민했던 걸 떠올리면서 어느 쪽이 맞을지 판단해봐.
4. `wheelItemScaling`이 특정 아이템의 "중심까지 거리"를 알아야 하는데, 그 아이템 자신의 위치 정보를 얻으려면 `lazyListState.layoutInfo.visibleItemsInfo`에서 어떻게 찾아야 할까? (힌트: `it.index == itemIndex`로 필터링.) 그리고 그 아이템이 지금 화면에 안 보이는 상태(`visibleItemsInfo`에 없음)라면 스케일 값을 어떻게 처리해야 안전할까?

## 막히면

- 그라데이션 경계가 부자연스럽다 → `gradientColor`가 실제 배경색과 일치하는지, `Brush.verticalGradient`의 시작/끝 좌표가 의도한 범위(뷰포트 전체 높이 기준 비율)인지 확인.
- 스케일이 계단식으로 뚝뚝 끊긴다 → `graphicsLayer` 람다 버전을 쓰고 있는지, 혹시 컴포저블 본문에서 `state`를 읽어서 리컴포지션에 의존하고 있진 않은지 확인 (질문 3).
- 화면 밖 아이템 때문에 크래시/이상한 값이 나온다 → 질문 4의 "안 보이는 아이템" 처리 확인.

## 완료 후

되면 코드/스크린샷 보여줘. Step 6(플링/스냅 커브 튜닝, 선택) 또는 Step 7(접근성)로 넘어가자 — 어느 쪽이 더 궁금해?
