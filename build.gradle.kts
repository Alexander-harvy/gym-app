plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
}

// Optional ASCII output location for Windows JDKs that misread Unicode @argfile classpaths.
allprojects {
    providers.gradleProperty("gymBuildRoot").orNull?.let { output ->
        layout.buildDirectory.set(file("$output/${project.name}"))
    }
}
