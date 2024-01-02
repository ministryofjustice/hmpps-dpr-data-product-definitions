package uk.gov.justice.digital.hmpps.dprdpdsservice.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.*
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.FilterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ParameterType
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.ProductDefinition
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.Effect
import uk.gov.justice.digital.hmpps.digitalprisonreportinglib.data.model.policyengine.PolicyType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import kotlin.io.path.name

@Service
class DefinitionsService() {

  fun getDefinitions(path: String): List<Map<String,Any>> {
   val gson : Gson = GsonBuilder()
      .registerTypeAdapter(LocalDateTime::class.java, IsoLocalDateTimeTypeAdaptor())
      .registerTypeAdapter(FilterType::class.java, FilterTypeDeserializer())
      .registerTypeAdapter(ParameterType::class.java, SchemaFieldTypeDeserializer())
      .registerTypeAdapter(Effect::class.java, RuleEffectTypeDeserializer())
      .registerTypeAdapter(PolicyType::class.java, PolicyTypeDeserializer())
      .create()
    val pathURI = this::class.java.classLoader.getResource(path)?.toURI()
    val dirs = Files.walk(Paths.get(pathURI), 1)
      .filter { Files.isRegularFile(it) }
      .filter { item -> item.toString().endsWith(".json")  }
      .toList()
    val result = dirs.map<Path, Map<String,Any>> {
      gson.fromJson(
        this::class.java.classLoader.getResource("$path/${it.name}")?.readText(),
        object : TypeToken<Map<String,Any>>() {}.type
      )
    }
    return result
  }

}