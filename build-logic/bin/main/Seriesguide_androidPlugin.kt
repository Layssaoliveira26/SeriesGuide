/**
 * Precompiled [seriesguide.android.gradle.kts][Seriesguide_android_gradle] script plugin.
 *
 * @see Seriesguide_android_gradle
 */
public
class Seriesguide_androidPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Seriesguide_android_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
