package uk.gov.justice.digital.hmpps.dprdpdsservice.controller.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.util.UriBuilder

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DefinitionsServiceIntegrationTest {

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Value("\${dpr.user.role}")
  lateinit var authorisedRole: String

  @Autowired
  lateinit var jwtAuthHelper: JwtAuthHelper

  @Test
  fun `Definitions API returns the full list of definitions for a valid directory which has definition json files`() {
    val requestPath = "prisons/test"
    val expectedJson1 = this::class.java.classLoader.getResource("dev/definitions/$requestPath/test-report.json")?.readText()
    val expectedJson2 = this::class.java.classLoader.getResource("dev/definitions/$requestPath/external-movements-with-summaries.json")?.readText()
    val expectedJson3 = this::class.java.classLoader.getResource("dev/definitions/$requestPath/missing-ethnicity-metrics.json")?.readText()
    val expectedJson4 = this::class.java.classLoader.getResource("dev/definitions/$requestPath/data-quality-metrics-filters-testing.json")?.readText()

    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/$requestPath")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .json(
        """[$expectedJson1, $expectedJson2, $expectedJson3, $expectedJson4]""",
      )
  }

  @Test
  fun `Definitions API returns an empty list of definitions for a missing directory`() {
    val requestPath = "prisons"

    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/$requestPath")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .json(
        """[]""",
      )
  }

  @Test
  fun `Definitions API returns an empty list of definitions for an directory with no json files`() {
    val requestPath = "nonexistent"

    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/$requestPath")
          .build()
      }
      .headers(setAuthorisation(roles = listOf(authorisedRole)))
      .exchange()
      .expectStatus()
      .isOk()
      .expectBody()
      .json(
        """[]       
      """,
      )
  }

  @Test
  fun `Definitions API returns unauthorized when no user is provided`() {
    val requestPath = "nonexistent"

    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/$requestPath")
          .build()
      }
      .exchange()
      .expectStatus()
      .isUnauthorized()
  }

  @Test
  fun `Definitions API returns forbidden when a user without the right role is provided`() {
    val requestPath = "nonexistent"

    webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/definitions/$requestPath")
          .build()
      }
      .headers(setAuthorisation(roles = listOf("NOT_AUTHORISED")))
      .exchange()
      .expectStatus()
      .isForbidden()
  }

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)
}
