import com.jingom.calmong.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * 순수 Kotlin/JVM 모듈(예: `:core:datetime` 같은 Android 비의존 도메인 로직)용 convention plugin.
 * Android library plugin과 달리 `android {}`가 없으며, Spotless·Detekt는 그대로 재사용한다.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.jvm")
            apply("calmong.spotless")
            apply("calmong.detekt")
        }

        val javaVer = JavaVersion.toVersion(libs.findVersion("javaVersion").get().requiredVersion)

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaVer
            targetCompatibility = javaVer
        }

        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(javaVer.toString()))
            }
        }
    }
}
