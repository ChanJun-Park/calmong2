# Compose Wheel Picker 학습 로드맵

## 목표

`androidx.wear.compose.material.Picker`(Wear OS 공식 컴포저블, 소스 분석: [`reference/wear-compose-picker/`](../reference/wear-compose-picker/))의 설계를 **베이스**로 삼아, 일반 폰 앱(Jetpack Compose, non-Wear)에서 동작하는 Wheel 스타일 날짜/시간 피커를 처음부터 직접 구현한다.

## 왜 "베이스"이지 "포팅"이 아닌가

Wear Picker는 `ScalingLazyColumn`, `RotarySnapLayoutInfoProvider` 등 **Wear OS 전용** 파운데이션 위에 만들어져 있어서, 폰 앱(`androidx.compose.foundation`)에는 그대로 가져다 쓸 수 없다. 대신 아래 설계 아이디어들은 그대로 재사용 가능하고, 이번 학습의 핵심이다.

| Wear Picker의 아이디어 | 폰 Compose에서 재구현할 도구 |
|---|---|
| `ScalingLazyColumn` (무한 스크롤 지원 리스트) | `LazyColumn` + 직접 만든 무한 인덱스 매핑 |
| `ScalingLazyColumnSnapFlingBehavior` (decay+snap 커브) | `androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior` (참고: [`reference/compose-foundation-snapping/`](../reference/compose-foundation-snapping/)) |
| `PickerState : ScrollableState` + `Saver` | 동일한 패턴을 `LazyListState` 위에 직접 구현 |
| `drawWithContent` 그라데이션/셰도우 | 동일하게 사용 가능 (Wear 전용 아님) |
| 아이템별 `graphicsLayer(Offscreen)` + 스케일/알파 | 동일하게 사용 가능 (Wear 전용 아님) |
| `clearAndSetSemantics` 접근성 | 동일하게 사용 가능 (Wear 전용 아님) |

즉 이번 학습은 "Wear 전용 API를 폰에서도 쓰는 법"이 아니라, **Wear Picker 설계자가 문제를 어떻게 분해했는지**를 배우고 폰 환경의 재료(`LazyColumn`, `compose.foundation.gestures.snapping`)로 같은 결론에 도달하는 연습이다.

## 진행 방식

- 각 단계는 `learning/step-NN-*.md` 파일로 정리한다. **지금은 1단계 자료만 만든다** — 완료하고 결과(코드/스크린샷/질문)를 가져오면 다음 단계 자료를 만들어준다.
- 각 단계 자료에는: 목표, 설계 근거, 참고 소스 위치, (코드가 아닌) 요구사항 명세, 완료 기준, 셀프 체크 질문이 포함된다.
- 실제 코드는 전부 직접 작성한다 — 학습 자료는 구현체가 아니라 방향과 참고자료만 제공한다.

## 8단계 개요

1. **[완료]** **스냅 스크롤 뼈대** — `LazyColumn` + `snapFlingBehavior`로 중앙 스냅되는 리스트 하나 만들기. ([`step-01-basic-snap-list.md`](./step-01-basic-snap-list.md)) — `core/ui`의 `BasicSnapWheel.kt`로 구현 완료. decay 커스터마이징은 레벨 1(파라미터 튜닝) 선에서 충분하다고 판단하고 다음 단계로.
2. **[완료]** **선택값 추출** — 뷰포트 중앙에 있는 아이템의 인덱스를 상태로 뽑아내기 (`ScalingLazyListState.centerItemIndex`에 해당하는 것을 `LazyListState.layoutInfo`로 직접 계산). ([`step-02-center-item-index.md`](./step-02-center-item-index.md))
3. **[완료]** **무한 순환 매핑** — `LARGE_NUMBER_OF_ITEMS` + 모듈로 매핑 트릭으로 무한 스크롤 흉내내기 (`PickerState.kt:594-727` 분석 기반). ([`step-03-infinite-loop-mapping.md`](./step-03-infinite-loop-mapping.md))
4. **[진행중] 상태 호이스팅 + 복원** — `PickerState : ScrollableState` + `rememberSaveable`/`Saver`로 재사용 가능한 상태 클래스(`WheelPickerState`) 만들기, `scrollToOption`/`animateScrollToOption` 구현. ([`step-04-state-hoisting.md`](./step-04-state-hoisting.md))
5. **시각 효과** — 상하 그라데이션(`drawWithContent`+`Brush`), 중앙 하이라이트, 거리 기반 스케일/알파(오프스크린 컴포지팅 포함).
6. **플링/스냅 커브 튜닝(선택)** — 기본 `rememberSnapFlingBehavior`로 충분한지 판단하고, 필요하면 `ScalingLazyColumnSnapFlingBehavior`의 decay+cubic-bezier 이어붙이기 기법을 직접 구현.
7. **접근성** — `clearAndSetSemantics`로 스크린리더 지원 (선택 값 읽기, 인덱스로 점프).
8. **조합 및 완성** — 년/월/일(또는 시/분) 피커를 묶어 실제 날짜/시간 선택 컴포넌트로 완성.

## 참고 소스 (전체 출처는 각 폴더의 README 참고)

- [`reference/wear-compose-picker/`](../reference/wear-compose-picker/) — `Picker.kt`, `ScalingLazyColumnSnapFlingBehavior.kt`, material3 `Picker.kt`
- [`reference/compose-foundation-snapping/`](../reference/compose-foundation-snapping/) — `SnapFlingBehavior.kt`, `LazyListSnapLayoutInfoProvider.kt`, 공식 샘플
