plugins {
    id("calmong.android.library")
    id("calmong.android.library.compose")
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.jingom.calmong.core.ui"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:datetime"))

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation)

    testImplementation(libs.junit)
}
