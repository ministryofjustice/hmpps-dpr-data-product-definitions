package uk.gov.justice.digital.hmpps.dprdpdsservice.controller.integration

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

class SchemaTest {
  private val schemaLocation: String = "https://raw.githubusercontent.com/ministryofjustice/hmpps-digital-prison-reporting-data-product-definitions-schema/main/schema/data-product-definition-schema.json"

  @Test
  fun `Definitions adhere to schema`() {
    val schemaNode = JsonLoader.fromURL(URL(schemaLocation))
    val schema: JsonSchema = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode)
    listOf("dev", "test", "preprod", "prod").forEach { env ->
      val pathURI = this::class.java.classLoader.getResource("$env/definitions/prisons")!!.toURI()
      val dirs = Files.walk(Paths.get(pathURI), 2)
        .filter { Files.isRegularFile(it) }
        .filter { item -> item.toString().endsWith(".json") }
        .toList()
      validateJsonFiles(dirs, schema)
    }
  }

  private fun validateJsonFiles(
    dirs: MutableList<Path>,
    schema: JsonSchema,
  ) {
    dirs.forEach {
      println("${it.parent.name}/${it.name}")

      val node = JsonLoader.fromPath(it.toString())
      val report = schema.validate(node)

      assert(report.isSuccess) {
        report.toString()
      }
    }
  }
}
