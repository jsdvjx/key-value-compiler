plugins {
    kotlin("jvm")
    id("maven-publish")
}
buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin", version = "1.7.10"))
    }
}
group = "ke.bb"



dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.7.10-1.0.6")
}

publishing {
    publications {
        create("maven_public", MavenPublication::class) {
            groupId = "ke.bb"
            artifactId = "key-value-compiler"
            version = "1.1.0"
            from(components.getByName("java"))
        }
    }
}