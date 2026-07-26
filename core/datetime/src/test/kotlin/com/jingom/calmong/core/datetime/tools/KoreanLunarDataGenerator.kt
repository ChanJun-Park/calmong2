package com.jingom.calmong.core.datetime.tools

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Properties

/**
 * KASI(한국천문연구원) 음양력 OpenAPI로 1899~2050년 음력 데이터를 수집해
 * `KoreanLunarData.kt`(메인 소스)를 생성하는 1회성 dev 도구. CI 비포함.
 * (양력 지원 시작 1900-01-01이 음력 1899년 12월에 속해 1899년부터 수집한다.)
 *
 * 실행: `./gradlew :core:datetime:generateKoreanLunarData`
 * 인증키: `core/datetime/kasi.properties`(gitignore)의 `serviceKey`(Decoding 키) 또는 env `KASI_SERVICE_KEY`.
 *
 * 보안: 인증키와 요청 URL은 어떤 로그/예외에도 평문으로 출력하지 않는다(항상 `***` 마스킹).
 *
 * 수집 방식(walk): 음력 1899-01-01의 양력일(anchor)에서 시작해 `getLunCalInfo(양력일)`로
 * 그 달의 (음력 연/월/윤달/월일수 lunNday)를 얻고, `양력일 += lunNday`로 다음 달 1일로 점프하며
 * 2050년까지 순회한다(약 1,900회 호출 → 10,000/day 한도 안전). 응답은 디스크 캐시로 재사용.
 */

private const val BASE = "http://apis.data.go.kr/B090041/openapi/service/LrsrCldInfoService"

// 양력 지원 시작(1900-01-01)은 음력 1899년 12월에 속하므로 데이터는 음력 1899년부터 수집한다.
private const val MIN_YEAR = 1899
private const val MAX_YEAR = 2050
private const val MONTHS_PER_LOG = 240

private data class LunMonth(
    val lunYear: Int,
    val lunMonth: Int,
    val isLeap: Boolean,
    val days: Int,
)

fun main() {
    val projectDir = File(System.getProperty("user.dir"))
    val serviceKey = loadServiceKey(projectDir)
    if (serviceKey == null) {
        println(
            """
            |[generateKoreanLunarData] 인증키를 찾지 못했습니다.
            |  1) cp core/datetime/kasi.properties.example core/datetime/kasi.properties
            |  2) kasi.properties 의 serviceKey 에 data.go.kr "일반 인증키(Decoding)" 입력
            |  3) ./gradlew :core:datetime:generateKoreanLunarData 재실행
            |키 값은 출력/커밋되지 않습니다.
            """.trimMargin(),
        )
        return
    }

    val client = KasiClient(serviceKey, cacheDir = File(projectDir, "build/kasi-cache"))

    println("[generateKoreanLunarData] anchor(음력 $MIN_YEAR-01-01 → 양력) 조회…")
    val epoch = client.solarOfLunar(MIN_YEAR, 1, 1, leap = false)
    println("[generateKoreanLunarData] epoch = $epoch")

    val months = mutableListOf<LunMonth>()
    var cursor = epoch
    while (true) {
        // KASI 범위(양력 ~2050-12-31)를 넘어서면 빈 응답이 온다 → 정상 종료.
        val m = client.lunarOf(cursor)
        if (m == null || m.lunYear > MAX_YEAR) break
        months += m
        cursor = cursor.plusDays(m.days.toLong())
        if (months.size % MONTHS_PER_LOG == 0) println("  …${m.lunYear}년까지 수집(${months.size}개월)")
    }
    val coveredMaxYear = months.maxOf { it.lunYear }
    println("[generateKoreanLunarData] 총 ${months.size}개월 수집 완료(최대 ${coveredMaxYear}년)")

    val encoded = encodeByYear(months)
    val outFile =
        File(
            projectDir,
            "src/main/kotlin/com/jingom/calmong/core/datetime/KoreanLunarData.kt",
        )
    outFile.parentFile.mkdirs()
    outFile.writeText(renderKotlin(epoch, encoded))
    println("[generateKoreanLunarData] 생성 완료 → ${outFile.relativeTo(projectDir)} (${encoded.size}개 연도)")
}

private fun loadServiceKey(projectDir: File): String? {
    val fromEnv = System.getenv("KASI_SERVICE_KEY")?.trim()
    if (!fromEnv.isNullOrEmpty()) return fromEnv
    val f = File(projectDir, "kasi.properties")
    val fromFile =
        if (f.exists()) {
            Properties().apply { f.inputStream().use { load(it) } }.getProperty("serviceKey")?.trim()
        } else {
            null
        }
    return fromFile?.takeIf { it.isNotEmpty() && it != "PUT_YOUR_DATA_GO_KR_DECODING_KEY_HERE" }
}

/**
 * 연도별 Int 인코딩:
 *  - bit 0..12  : 시간순 월별 대소(1=30일/0=29일)
 *  - bit 13..16 : 윤달 위치(0=없음, 1..12)
 *  - bit 17..21 : 그 해의 음력 월 수(보통 12/13, 마지막 해는 KASI 범위 끝이라 부분일 수 있음)
 * KASI 데이터는 양력 2050-12-31까지라 마지막 음력 연도(2050)는 일부 월만 존재한다.
 */
private fun encodeByYear(months: List<LunMonth>): Map<Int, Int> {
    val byYear = months.groupBy { it.lunYear }
    val maxYear = byYear.keys.max()
    val result = LinkedHashMap<Int, Int>()
    for (year in MIN_YEAR..maxYear) {
        val ms = byYear[year] ?: error("$year 년 데이터 누락")
        if (year < maxYear) {
            val expected = if (ms.any { it.isLeap }) 13 else 12
            require(ms.size == expected) { "$year 년 월 수 ${ms.size} != $expected" }
        }
        var sizeBits = 0
        ms.forEachIndexed { index, m -> if (m.days == 30) sizeBits = sizeBits or (1 shl index) }
        val leapMonth = ms.firstOrNull { it.isLeap }?.lunMonth ?: 0
        result[year] = sizeBits or (leapMonth shl 13) or (ms.size shl 17)
    }
    return result
}

private fun renderKotlin(
    epoch: LocalDate,
    encoded: Map<Int, Int>,
): String {
    val maxYear = MIN_YEAR + encoded.size - 1
    val rows =
        encoded.entries.chunked(8).joinToString(",\n") { chunk ->
            "        " + chunk.joinToString(", ") { (_, v) -> "0x%06X".format(v) }
        }
    return """
        |// 이 파일은 KoreanLunarDataGenerator(KASI OpenAPI)로 자동 생성됩니다. 직접 수정하지 마세요.
        |// 데이터 출처: 한국천문연구원(KASI) 음양력 정보 OpenAPI (data.go.kr/15012679).
        |// 인코딩(각 연도 Int): bit 0..12 = 시간순 월별 대소(1=30일/0=29일),
        |//   bit 13..16 = 윤달 위치(0=없음, 1..12), bit 17..21 = 그 해 음력 월 수.
        |// KASI는 양력 2050-12-31까지라 마지막 음력 연도($maxYear)는 일부 월만 포함된다.
        |package com.jingom.calmong.core.datetime
        |
        |import java.time.LocalDate
        |
        |internal object KoreanLunarData {
        |    const val MIN_YEAR = $MIN_YEAR
        |    const val MAX_YEAR = $maxYear
        |
        |    /** 음력 ${MIN_YEAR}-01-01에 해당하는 양력일(누적 일수 계산의 기준점). */
        |    val EPOCH: LocalDate = LocalDate.of(${epoch.year}, ${epoch.monthValue}, ${epoch.dayOfMonth})
        |
        |    /** index 0 = ${MIN_YEAR}년 … index ${encoded.size - 1} = ${maxYear}년. */
        |    val YEARS: IntArray =
        |        intArrayOf(
        |$rows,
        |        )
        |}
        |
        """.trimMargin()
}

/** KASI OpenAPI 클라이언트 — 인증키/URL을 절대 평문 출력하지 않는다. */
private class KasiClient(
    private val serviceKey: String,
    private val cacheDir: File,
) {
    private val encodedKey = URLEncoder.encode(serviceKey, "UTF-8")

    init {
        cacheDir.mkdirs()
    }

    fun solarOfLunar(
        year: Int,
        month: Int,
        day: Int,
        leap: Boolean,
    ): LocalDate {
        val body =
            call(
                op = "getSolCalInfo",
                cacheKey = "sol-%04d-%02d-%02d-%s".format(year, month, day, if (leap) "윤" else "평"),
                params =
                    linkedMapOf(
                        "lunYear" to "%04d".format(year),
                        "lunMonth" to "%02d".format(month),
                        "lunDay" to "%02d".format(day),
                        "leapMonth" to if (leap) "윤" else "평",
                    ),
            )
        return LocalDate.of(
            field(body, "solYear").toInt(),
            field(body, "solMonth").toInt(),
            field(body, "solDay").toInt(),
        )
    }

    /** KASI 범위를 벗어나 빈 응답이면 null. */
    fun lunarOf(solar: LocalDate): LunMonth? {
        val body =
            call(
                op = "getLunCalInfo",
                cacheKey = "lun-%04d-%02d-%02d".format(solar.year, solar.monthValue, solar.dayOfMonth),
                params =
                    linkedMapOf(
                        "solYear" to "%04d".format(solar.year),
                        "solMonth" to "%02d".format(solar.monthValue),
                        "solDay" to "%02d".format(solar.dayOfMonth),
                    ),
            )
        if (body.contains("\"totalCount\":0")) return null
        val lunDay = field(body, "lunDay").toInt()
        require(lunDay == 1) { "walk가 음력 1일이 아닌 날에 도달함: $solar (lunDay=$lunDay)" }
        return LunMonth(
            lunYear = field(body, "lunYear").toInt(),
            lunMonth = field(body, "lunMonth").toInt(),
            isLeap = field(body, "lunLeapmonth") == "윤",
            days = field(body, "lunNday").toInt(),
        )
    }

    private fun call(
        op: String,
        cacheKey: String,
        params: Map<String, String>,
    ): String {
        val cache = File(cacheDir, "$op-$cacheKey.json")
        if (cache.exists()) return cache.readText()

        val query =
            buildString {
                append("?serviceKey=").append(encodedKey).append("&_type=json")
                params.forEach { (k, v) -> append("&").append(k).append("=").append(URLEncoder.encode(v, "UTF-8")) }
            }
        val url = "$BASE/$op$query"
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val status = conn.responseCode
        val text =
            (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() } ?: ""
        if (status !in 200..299 || !text.contains("\"resultCode\":\"00\"")) {
            error("KASI $op 실패 (HTTP $status): ${mask(text).take(300)}")
        }
        cache.writeText(text)
        return text
    }

    /** KASI json 응답에서 "field":"value" 추출. */
    private fun field(
        body: String,
        name: String,
    ): String =
        Regex("\"$name\":\"?([^\",}]*)\"?")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: error("응답에 $name 없음: ${mask(body).take(200)}")

    /** 인증키가 혹시라도 섞여 나오면 마스킹. */
    private fun mask(s: String): String = s.replace(serviceKey, "***").replace(encodedKey, "***")
}
