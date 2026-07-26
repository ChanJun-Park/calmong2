# KoreanLunarData 구조 가이드

`:core:datetime`의 한국 음력 변환이 사용하는 **내장 데이터** `KoreanLunarData`가 어떻게 구성되는지, 어떻게 디코딩·변환에 쓰이는지를 설명한다. 이 문서가 인코딩의 단일 출처다 — 인코딩을 바꾸면 이 문서와 [`KoreanLunarDataGenerator`](src/test/kotlin/com/jingom/calmong/core/datetime/tools/KoreanLunarDataGenerator.kt)(인코더), [`KoreanLunarCalendar`](src/main/kotlin/com/jingom/calmong/core/datetime/KoreanLunarCalendar.kt)(디코더)를 함께 맞춘다.

## 1. 왜 이 데이터가 필요한가

양력(그레고리력)은 규칙이 고정이라 계산만으로 날짜를 알 수 있지만, **한국 음력은 천문 관측 결과**라 규칙만으론 못 구한다. 구체적으로 두 가지가 미리 정해져 있어야 변환이 가능하다.

1. **각 음력 달의 길이** — 음력 한 달은 **29일(작은달)** 또는 **30일(큰달)**. 어느 달이 크고 작은지는 해마다 다르다.
2. **윤달(閏月)의 위치** — 음력은 1년이 약 354일이라 양력과 어긋나, 약 19년에 7번 **윤달**(같은 달을 한 번 더 반복하는 13번째 달)을 끼워 맞춘다. 몇 월 뒤에 윤달이 오는지도 해마다 다르다.

이 두 정보(+기준점 하나)만 있으면 음력↔양력을 **누적 일수 계산**으로 변환할 수 있다. 그래서 해당 기간치를 KASI(한국천문연구원)에서 받아 **앱에 내장**한다(런타임 네트워크 불필요).

- **출처**: 한국천문연구원 음양력 정보 OpenAPI (data.go.kr/15012679)
- **지원(검증) 범위**: **양력 1900-01-01 ~ 2050-12-31** (요구사항). 이 양력 범위만 변환 허용.
- **내장 데이터 범위**: 음력 **1899년 ~ 2050년**. 양력 1900-01-01이 음력 1899년 12월에 속하므로 데이터는 1899년부터 들고 있고, 지원 범위는 그보다 좁게 §8에서 명시한다.

## 2. 전체 모양

`KoreanLunarData.kt`는 생성기가 자동으로 만드는 파일이며, 세 가지만 들고 있다.

```kotlin
internal object KoreanLunarData {
    const val MIN_YEAR = 1899
    const val MAX_YEAR = 2050
    val EPOCH: LocalDate = LocalDate.of(1899, 2, 10) // 음력 1899-01-01의 양력일(기준점)
    val YEARS: IntArray = intArrayOf(0x180AD5, /* … 152개 … */)
}
```

- **`EPOCH`** — 음력 1899년 1월 1일에 해당하는 **양력 날짜**(1899-02-10). 모든 변환은 이 날을 0일째로 두고 일수를 더하고 뺀다.
- **`YEARS`** — 음력 연도 하나를 **`Int` 하나**로 인코딩한 배열. `YEARS[0]`=1899년, `YEARS[1]`=1900년 … `YEARS[151]`=2050년 (즉 `YEARS[year - MIN_YEAR]`).
- 이 `MIN_YEAR`/`MAX_YEAR`/`EPOCH`는 **데이터 범위**(내부용)다. 앱이 노출하는 **지원 범위**는 `KoreanLunarCalendar`의 날짜 상수(§8)다.

## 3. 한 해를 Int 하나에 담는 인코딩

연도 1개 = `Int` 1개. 22비트만 사용한다.

```
bit:  21 20 19 18 17 | 16 15 14 13 | 12 11 10  9  8  7  6  5  4  3  2  1  0
      └── 월 수 ──────┘ └─ 윤달 위치 ┘ └────── 월 대소 13칸 (1=30일/0=29일) ──────┘
       (12·13, 0..13)    (0=없음,1..12)   (시간순 각 달; bit i = i번째 달)
```

| 필드 | 비트 | 의미 |
|---|---|---|
| 월 대소 | 0..12 | **시간순**(§4)으로 각 달이 큰달(30일)이면 1, 작은달(29일)이면 0 |
| 윤달 위치 | 13..16 | 윤달이 **몇 월 뒤에** 오는지(0=윤달 없음, 1..12). 예: 2=2월 다음에 윤2월 |
| 월 수 | 17..21 | 그 해 음력 달 수(평년 12, 윤년 13, 마지막 해는 부분일 수 있음) |

코드의 디코딩 헬퍼([`KoreanLunarCalendar`](src/main/kotlin/com/jingom/calmong/core/datetime/KoreanLunarCalendar.kt)):

```kotlin
monthCount(v) = (v ushr 17) and 0x1F   // 월 수
leapMonth(v)  = (v ushr 13) and 0xF    // 윤달 위치(0=없음)
monthDays(v, i) = if ((v ushr i) and 1 == 1) 30 else 29  // 시간순 i번째 달의 일수
```

## 4. "시간순(chronological)" 인덱스 — 윤달을 어떻게 끼우나

월 대소 13칸은 음력 1·2·…월 순서가 아니라, **실제로 흘러가는 순서**로 채운다. 윤달은 `leapMonth` 달 **바로 뒤**에 들어간다.

- 윤달 없음: `[1월, 2월, 3월, … 12월]` → 12칸
- `leapMonth = L`: `[1월, …, L월, 윤L월, (L+1)월, …, 12월]` → 13칸

그래서 "음력 (월, 윤달여부)"와 "시간순 인덱스"는 다음과 같이 오간다(0-based):

| 음력 월·윤달 | → 시간순 인덱스 |
|---|---|
| 윤달이 아닌 `m`월 (윤달 없음 or `m ≤ L`) | `m - 1` |
| 윤달이 아닌 `m`월 (`m > L`) | `m` (윤달 한 칸 밀림) |
| 윤`L`월 | `L` |

역방향(시간순 `i` → 음력 월·윤달):

| 조건 | → 음력 월·윤달 |
|---|---|
| 윤달 없음 또는 `i < L` | `(i+1)`월, 평달 |
| `i == L` | `L`월, **윤달** |
| `i > L` | `i`월, 평달 |

## 5. 변환 알고리즘(요약)

기준점 `EPOCH`(음력 1900-01-01 = 양력 1900-01-31)에서 **일수를 누적**한다.

- **한 해 길이** `yearLength(v)` = 시간순 0..`monthCount-1`번째 달 일수의 합(354 또는 384 근처).
- **음력 → 양력**: `offset = Σ(1900..그 해 직전 연도 길이) + Σ(그 해 시간순 0..대상달 직전 일수) + (일 − 1)` → `EPOCH.plusDays(offset)` → KST `ZonedDateTime`(시각은 그대로 부착).
- **양력 → 음력**: `남은일수 = EPOCH→대상양력일`. 연도별 `yearLength`를 빼며 연도 확정 → 그 해 달별 일수를 빼며 시간순 인덱스 확정 → 남은값+1 = 일, 인덱스를 §4 역표로 (월, 윤달)로 환원. 시각은 KST 환산 후 보존.

전체 구현은 [`KoreanLunarCalendar`](src/main/kotlin/com/jingom/calmong/core/datetime/KoreanLunarCalendar.kt) 참고.

## 6. 차근차근 예제 — 2023년 = `0x1A55AA`

`YEARS[2023 - 1899]` = `YEARS[124]` = `0x1A55AA`.

**6-1. 비트 분해** (`0x1A55AA` = `0b01101_0010_1010110101010`)

| 필드 | 비트값 | 해석 |
|---|---|---|
| 월 수 (17..21) | `01101` = 13 | 13개월 → **윤년** |
| 윤달 위치 (13..16) | `0010` = 2 | 2월 다음에 **윤2월** |
| 월 대소 (0..12) | `1010110101010` | 아래 표 |

**6-2. 월 대소 펼치기**(bit 0이 시간순 첫 달). `1010110101010`을 bit0→bit12로 읽으면:

| 시간순 i | 음력 달 | bit | 일수 |
|---|---|---|---|
| 0 | 1월 | 0 | 29 |
| 1 | 2월(평) | 1 | 30 |
| 2 | **윤2월** | 0 | 29 |
| 3 | 3월 | 1 | 30 |
| 4 | 4월 | 0 | 29 |
| 5 | 5월 | 1 | 30 |
| 6 | 6월 | 0 | 29 |
| 7 | 7월 | 1 | 30 |
| 8 | 8월 | 1 | 30 |
| 9 | 9월 | 0 | 29 |
| 10 | 10월 | 1 | 30 |
| 11 | 11월 | 0 | 29 |
| 12 | 12월 | 1 | 30 |

**6-3. 변환 따라가기** — 음력 2023년 **윤2월 1일**은 양력 며칠?

- 음력 2023-01-01 = 양력 **2023-01-22**(설날, 알려진 값).
- 윤2월 1일의 시간순 인덱스 = 2(§4: 윤`L`월 → `L`). 그 앞 두 달(1월 29일 + 평2월 30일 = **59일**)을 더한다.
- 2023-01-22 **+ 59일 = 2023-03-22**. (평2월 1일은 +29일 = 2023-02-20)

→ `LunarDateTime(2023, 2, 1, isLeapMonth = true).toZonedDateTime()` = 양력 2023-03-22, `isLeapMonth=false`면 2023-02-20. (테스트로 고정되어 있음)

## 7. 사용 예

```kotlin
// 음력 → 양력(KST)
LunarDateTime(2024, 8, 15).toZonedDateTime()        // 추석 → 2024-09-17T00:00+09:00
// 양력 → 음력 (시각 보존)
ZonedDateTime.of(2024, 9, 17, 14, 30, 0, 0, KoreanLunarCalendar.ZONE)
    .toLunarDateTime()                               // LunarDateTime(2024, 8, 15, time=14:30)
```

## 8. 지원 범위(년·월·일)·경계

지원 범위는 데이터에서 파생되어 [`KoreanLunarCalendar`](src/main/kotlin/com/jingom/calmong/core/datetime/KoreanLunarCalendar.kt)에 **명시 상수**로 노출된다(재생성하면 자동으로 갱신).

| 구분 | 시작 | 끝 |
|---|---|---|
| **양력** (`MIN_SOLAR_DATE`/`MAX_SOLAR_DATE`, `LocalDate`) | **1900-01-01** | **2050-12-31** |
| **음력** (`MIN_LUNAR_DATE`/`MAX_LUNAR_DATE`, `LunarDateTime`) | **1899-12-01**(평달) | **2050-11-18**(평달) |

- **지원 범위는 양력 기준**(요구사항: 1900-01-01 ~ 2050-12-31). 이에 대응하는 음력 끝점이 위 음력 범위다 — 양력 1900-01-01 = 음력 1899-12-01, 양력 2050-12-31 = 음력 2050-11-18.
- 양력 경계 상수는 **하드코딩**(1900-01-01 / 2050-12-31), 음력 경계는 그 양력일을 변환해 **데이터에서 파생**한다(재생성해도 자동 정합).
- **내장 데이터는 지원 범위보다 넓다**(음력 1899년 전체 ~ 2050년 11월, 양력 ~2051-01-12). 데이터가 있어도 지원 범위(양력 1900-01-01~2050-12-31)를 벗어나면 변환을 **거부**한다.
- **범위 밖은 모두 `IllegalArgumentException`**: 양력이 `MIN_SOLAR_DATE`~`MAX_SOLAR_DATE`를 벗어남, 변환 결과 양력이 범위를 벗어나는 음력일(예: 음력 1899-11월, 음력 2050-11-19 이후), 데이터에 없는 음력 연도(<1899 또는 >2050), 존재하지 않는 달·윤달, 그 달에 없는 일(예: 29일 달의 30일).
- 코드에서 경계 확인: `KoreanLunarCalendar.MIN_SOLAR_DATE` / `.MAX_SOLAR_DATE` / `.MIN_LUNAR_DATE` / `.MAX_LUNAR_DATE`.

## 9. 그 외 주의
- **시간대**: 변환은 **KST '벽시계 날짜'** 기준이라 과거 한국 표준시 오프셋 변천(1908/1948~1988 등)과 무관하다(인스턴트가 아니라 날짜를 매핑). 한국은 1988년 이후 DST 없음.
- **윤달 ≠ 중국 음력**: 한국 음력은 KST(동경 135°) 기준 삭(신월)으로 계산해 중국 음력과 윤달 위치·하루가 다를 수 있다. 반드시 KASI 데이터를 쓴다.

## 10. 재생성 방법

데이터를 다시 만들거나 범위를 넓힐 때:

1. `cp core/datetime/kasi.properties.example core/datetime/kasi.properties`
2. `kasi.properties`의 `serviceKey`에 data.go.kr **"일반 인증키(Decoding)"** 입력. *이 파일은 .gitignore되어 커밋되지 않으며, 키는 로그/URL에 노출되지 않는다(`***` 마스킹).*
3. `./gradlew :core:datetime:generateKoreanLunarData` 실행 → `KoreanLunarData.kt` 갱신.
   - walk 방식: `EPOCH`에서 시작해 `getLunCalInfo(양력일)`로 그 달의 `lunNday`(월 일수)·`lunLeapmonth`(평/윤)를 읽고 `+= lunNday`로 다음 달 1일로 점프(약 1,900회 호출, 10,000/day 한도 안전). 응답은 `build/kasi-cache/`에 캐시(재실행 시 재요청 없음).
4. 검증: `./gradlew :core:datetime:test`(설날·추석·윤달·왕복 등). 샘플은 [KASI 음양력변환계산](https://astro.kasi.re.kr/life/pageView/8)과 대조.

생성기·인코더: [`KoreanLunarDataGenerator.kt`](src/test/kotlin/com/jingom/calmong/core/datetime/tools/KoreanLunarDataGenerator.kt).
