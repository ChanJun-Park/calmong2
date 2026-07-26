plugins {
    id("calmong.jvm.library")
}

dependencies {
    testImplementation(libs.junit)
}

// KASI OpenAPI로 1900~2050 음력 데이터를 생성해 KoreanLunarData.kt를 갱신하는 1회성 dev 도구.
// 인증키는 core/datetime/kasi.properties(gitignore)의 serviceKey에서 읽는다. CI 비포함.
tasks.register<JavaExec>("generateKoreanLunarData") {
    group = "build"
    description = "Generate KoreanLunarData.kt from KASI OpenAPI (needs serviceKey in kasi.properties)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jingom.calmong.core.datetime.tools.KoreanLunarDataGeneratorKt")
    workingDir = projectDir
    // 콘솔 입출력 그대로 노출(생성기가 키는 마스킹해서 출력한다).
    standardInput = System.`in`
}
