package uk.gov.justice.digital.hmpps.dprdpdsservice.controller.integration

import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.name

class SchemaTest {

  @Test
  fun `Definitions adhere to schema`() {
    val schemaNode = JsonLoader.fromResource("/data-product-definition-schema.json")
    val schema: JsonSchema = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode)

    val pathURI = this::class.java.classLoader.getResource("definitions/prisons")!!.toURI()
    val dirs = Files.walk(Paths.get(pathURI), 2)
      .filter { Files.isRegularFile(it) }
      .filter { item -> item.toString().endsWith(".json") }
      .toList()
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