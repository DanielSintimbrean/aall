plugins { kotlin("jvm") version "2.2.0" }
repositories { mavenCentral() }
java { targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
// Only core/ compiles here, so an Android import in it fails the build.
sourceSets.main { kotlin.setSrcDirs(listOf("../android/src/main/java/dev/danields/aall/core")) }
dependencies { implementation("org.json:json:20250517"); testImplementation("junit:junit:4.13.2") }
// At quiet level, so `gradle -q` still names a failing test and why.
tasks.test { testLogging.quiet { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
