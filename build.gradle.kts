plugins {
  idea
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.+"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
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
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

  implementation("com.h2database:h2")
  implementation("com.google.code.gson:gson:2.10.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}
kotlin {
  jvmToolchain(19)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
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
