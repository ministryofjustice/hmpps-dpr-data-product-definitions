package uk.gov.justice.digital.hmpps.dprdpdsservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.dprdpdsservice.service.DefinitionsService

@RestController
@Tag(name = "Data Product Definitions API")
class DefinitionsController(val definitionsService: DefinitionsService) {
  @Operation(
    description = "Gets all the definitions by directory",
  )
  @GetMapping("/definitions/**")
  fun getDefinitions(request: HttpServletRequest): List<ProductDefinition> {
    val path: String = request.requestURI
    return definitionsService.getDefinitions(path.removePrefix("/"))
  }
}