plugins {
  idea
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.5"
  kotlin("jvm") version "2.0.20"
  kotlin("plugin.spring") version "2.0.20"
  id("jacoco")
  id("org.barfuin.gradle.jacocolog") version "3.1.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  // Swagger
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

  implementation("com.h2database:h2")
  implementation("com.google.code.gson:gson:2.11.0")

  testImplementation("com.github.java-json-tools:json-schema-validator:2.2.14")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
kotlin {
  jvmToolchain(21)
}

tasks {
  withType<Test> {
    useJUnitPlatform()
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
