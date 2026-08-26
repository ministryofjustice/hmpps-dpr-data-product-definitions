#!/usr/bin/env kotlinc -script

@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.18.2")
@file:DependsOn("com.opencsv:opencsv:5.9")

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.opencsv.CSVWriter
import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import java.io.FileWriter


// ==========================================
// 1. CONSTANTS
// ==========================================

object TestDataConstants {
    fun generateOffenderId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars.random() }
            .joinToString("")
    }

    fun generateLocation(unitCode: Char): String {
        val wing = Random.nextInt(1, 6)
        val cell = Random.nextInt(1, 1000)
            .toString()
            .padStart(3, '0')

        return "$unitCode-$wing-$cell"
    }

}

object TestDataGenerator {
    fun generate(columnName: String, dataType: String, rowNum: Int): Any =
        when (columnName.uppercase()) {
            // Special column-specific generators
            "OFFENDER_ID_DISPLAY" -> TestDataConstants.generateOffenderId()
            "LAST_NAME" -> "Surname${rowNum.toString().padStart(4, '0')}"
            "FIRST_NAME" -> "GivenName${rowNum.toString().padStart(4, '0')}"
            "LOCATION" -> TestDataConstants.generateLocation(
                ('A'..'Z').random()
            )
            else -> generateByType(dataType)
        }

    fun generateVarchar(type: String): String {
        val chars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val length = Regex("""VARCHAR\\((\\d+)\\)""")
            .find(type.uppercase())
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: 20

        return (1..length.coerceAtMost(100))
            .map { chars.random() }
            .joinToString("")
    }

    private fun generateByType(dataType: String): Any =
        when (dataType.uppercase()) {
            "VARCHAR(30)", "VARCHAR" -> generateVarchar(dataType)
            "DOUBLE PRECISION" -> (1..100).random().toDouble()
            "BIGINT" -> (1..10000).random().toLong()
            "INTEGER" -> (1..10000).random()
            "DATE" -> LocalDate.now().minusDays((0..365).random().toLong()).toString()
            else -> ""
        }
}

// =====================================================
// 2. MAIN:
// =====================================================

fun main() {
    println("Working directory = ${System.getProperty("user.dir")}")

    // Load the DPD from dpd/dev/definitions/prisons/test-resources
    // and create the test DPDs under generated-test-dpds
    val tableToColumnsMap: Map<String, Map<String, String>> = loadDPDtoGenerateTestData()

    // Generate the test data csv under generated-test-data
    generateTestData(tableToColumnsMap,  100)

    // Generate sql script for, createTable + copySql
    val sqlScript = sqlScriptGeneration(tableToColumnsMap)
    println("=======================================sql script========================================================")
    println(sqlScript)

}

// ==========================================
// 3. SUPPORTING METHODS for Main
// ==========================================

fun loadDPDtoGenerateTestData(): Map<String, Map<String, String>> {
    val objectMapper = ObjectMapper()
    val sourceDir = File("../../dpd/dev/definitions/prisons/test-resources")
    val testDPDDir = File("generated-test-dpds")
    testDPDDir.mkdirs()

    val tableToColumnsMap: Map<String, Map<String, String>> = sourceDir
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.associate { file ->

            val root: JsonNode = objectMapper.readTree(file)

            val tableName = file.nameWithoutExtension
                .replace("-", "_")
                .uppercase()

            val reports = root["report"]

            val firstDatasetId = reports
                .map { it["dataset"].asText() }
                .distinct()
                .first()

            val redshiftColumns = getRedshiftColumnsMap(root.toString(), firstDatasetId)

            println("Processing file: ${file.name}")
            println("Table Name: $tableName")
            println("Dataset Id: $firstDatasetId")

            // Create new datasource array
            val mutableRoot = root as ObjectNode
            val sql = "SELECT * FROM datamart.datahub_test.$tableName"

            mutableRoot.set<JsonNode>(
                "datasource",
                objectMapper.createArrayNode().add(
                    objectMapper.createObjectNode()
                        .put("id", "datamart")
                        .put("name", "datamart")
                )
            )

            mutableRoot["dataset"]
                ?.firstOrNull { it["id"]?.asText() == firstDatasetId }
                ?.let { dataset ->
                    (dataset as ObjectNode).put("query", sql)
                }

            val testDPDFile = File(testDPDDir, "test-${file.name}")

            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(testDPDFile, mutableRoot)

            tableName to redshiftColumns
        } ?: emptyMap()

    return tableToColumnsMap
}



fun getRedshiftColumnsMap (root: String, datasetId: String) : Map<String, String> {
    val fields = getSchemaFields(root, datasetId)
    val redshiftTypeMapping = mapOf(
        "string" to "VARCHAR(30)",
        "date" to "DATE",
        "double" to "DOUBLE PRECISION",
        "long" to "BIGINT",
        "int" to "INTEGER"
    )

    val redshiftColumns = fields.mapValues { (_, type) ->
        redshiftTypeMapping[type.lowercase()] ?: "VARCHAR(30)"
    }

    return redshiftColumns
}

fun getSchemaFields(json: String, datasetId: String): Map<String, String> {
    val mapper = ObjectMapper()
    val root = mapper.readTree(json)

    val dataset = root["dataset"]
        ?.firstOrNull { it["id"]?.asText() == datasetId }
        ?: return emptyMap()

    val fieldMap = dataset.get("schema")["field"]
        .associate { field ->
            field["name"].asText() to field["type"].asText()
        }

    return fieldMap
}

fun generateTestData( tableToColumnsMap: Map<String, Map<String, String>>, rowCount: Int = 1) {
    val outputDir = File("generated-test-data")
    outputDir.mkdirs()

    tableToColumnsMap.forEach { (tableName, redshiftColumns) ->
        val csvFileName = "$tableName.csv"
        val outputFile = File(outputDir, csvFileName)

        CSVWriter(FileWriter(outputFile)).use { writer ->

            // Header row
            writer.writeNext(redshiftColumns.keys.toTypedArray())

            // Data rows
            repeat(rowCount) { rowIndex ->

                val row = redshiftColumns.entries.map { (column, type) ->
                    TestDataGenerator.generate(
                        columnName = column,
                        dataType = type,
                        rowNum = rowIndex + 1
                    ).toString()
                }

                writer.writeNext(row.toTypedArray())
            }
        }
    }
}

fun sqlScriptGeneration( tableToColumnsMap: Map<String, Map<String, String>>): String {
    // Get AWS account ID from environment or use default
//    val accountId = System.getenv("AWS_ACCOUNT_ID") ?: "771283872747"
    val iamRoleArn = "arn:aws:iam::***:role/dpr-redshift-cluster-role"

    // Build sqlScript for all the DPDs
    val sqlScript = buildString {
        appendLine("BEGIN; ")

        tableToColumnsMap.forEach { (tableName, redshiftColumns) ->
            val csvFileName = "$tableName.csv"

            // Create a redshift table
            appendLine("DROP TABLE IF EXISTS datahub_test.$tableName; ")
            appendLine("CREATE TABLE datahub_test.$tableName (")
            append(
                redshiftColumns?.entries?.joinToString(",\n") {
                    "    ${it.key} ${it.value}"
                }
            )
            appendLine()
            appendLine("); ")

            // Load the S3 csv to Redshift table
            appendLine("COPY datahub_test.$tableName ")
            appendLine("FROM 's3://dpr-working-development/datahub-test-data/$csvFileName' ")
            appendLine("IAM_ROLE '$iamRoleArn' ")
            appendLine("CSV ")
            appendLine("IGNOREHEADER 1;")
        }
        appendLine(" COMMIT; ")
    }

    return sqlScript
}

main()
