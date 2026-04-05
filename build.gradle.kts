plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.reasoningtestgen"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // OkHttp for LLM API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Apache HTTP Client 5 for integration tests
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    // SLF4J Logging
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
}

// Configure IntelliJ Gradle Plugin
intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
    downloadSources.set(true)
}

tasks {
    // Set JVM target to Java 17
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    // Plugin XML patching for compatibility range
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("252.*")
    }

    // Skip searchable options building
    buildSearchableOptions {
        enabled = false
    }

    // Disable code instrumentation - this is the key fix for runIde
    instrumentCode {
        enabled = false
    }

    instrumentTestCode {
        enabled = false
    }

    // Simple test task
    val runSimpleTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Run simple standalone tests"

        mainClass.set("com.reasoningtestgen.SimpleTestRunner")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )
    }

    // LM Studio integration tests
    val runLMStudioTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Run LM Studio integration tests"

        mainClass.set("com.reasoningtestgen.llm.LMStudioApacheTest")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )
    }

    // Plugin integration test
    val runPluginTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Run plugin integration test with OrderService"

        mainClass.set("com.reasoningtestgen.PluginIntegrationTest")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )
    }

    // Self-correction test
    val runSelfCorrectionTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Run self-correction test with LM Studio"

        mainClass.set("com.reasoningtestgen.SelfCorrectionTest")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )

        // Pass system properties from command line
        systemProperties = System.getProperties().mapKeys { (key, _) -> key.toString() }
            .filterKeys { it.startsWith("selfcorrection.") }
    }
    
    // Plugin dogfooding test
    val runDogfoodingTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Use plugin to generate tests for itself"
        
        mainClass.set("com.reasoningtestgen.PluginDogfoodingTest")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )
    }
    
    // Comprehensive dogfooding test (5 methods)
    val runComprehensiveDogfoodingTest by registering(JavaExec::class) {
        dependsOn(compileTestJava)
        group = "verification"
        description = "Use plugin to generate tests for 5 plugin methods"
        
        mainClass.set("com.reasoningtestgen.ComprehensiveDogfoodingTest")
        classpath = files(
            sourceSets.main.get().output,
            sourceSets.test.get().output,
            configurations.testRuntimeClasspath
        )
    }

    // Configure tests to run without IntelliJ instrumentation
    test {
        useJUnitPlatform()

        // Disable IntelliJ test instrumentation
        systemProperty("idea.ignore.disabled.plugins", "true")

        testLogging {
            events("passed", "skipped", "failed", "standard_out", "standard_error")
            showStandardStreams = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        // Enable standard test reports
        reports {
            html.required.set(true)
            junitXml.required.set(true)
        }
    }
}
