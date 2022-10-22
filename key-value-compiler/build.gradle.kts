plugins {
    kotlin("jvm")
}
buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.7.10"))
    }
}
group = "ke.bb"
version = "1.0.0"



dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.10-1.0.6")
}
