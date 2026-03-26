plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "ru.sbrf.uddk.ai.testing"
version = "1.0.1"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IC")

    plugins.set(listOf("com.intellij.java"))
}

tasks {

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    withType<JavaExec> {
        defaultCharacterEncoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
