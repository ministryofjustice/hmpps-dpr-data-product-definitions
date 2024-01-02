package uk.gov.justice.digital.hmpps.dprdpdsservice.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name

@Service
class DefinitionsService() {

  fun getDefinitions(path: String): List<Map<String,Any>> {
   val gson : Gson = GsonBuilder()
      .create()
    val pathURI = this::class.java.classLoader.getResource(path)?.toURI() ?: return emptyList()
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