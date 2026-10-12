#!/usr/bin/env kotlinc -script

@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.18.2")
@file:DependsOn("com.opencsv:opencsv:5.9")
@file:DependsOn("software.amazon.awssdk:s3:2.25.53")

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.time.LocalDate
import kotlin.random.Random
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.time.LocalDateTime
import kotlin.collections.firstOrNull

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
    fun generate(columnName: String, dataType: String, rowNum: Int): Any {
        val aTozChar = ('A'..'Z').random()
        return when (columnName.uppercase()) {
            // Special column-specific generators
            "OFFENDER_ID_DISPLAY" -> TestDataConstants.generateOffenderId()
            "PRISONER_NUMBER" -> TestDataConstants.generateOffenderId()
            "LAST_NAME" -> "Surname${rowNum.toString().padStart(4, '0')}"
            "FIRST_NAME" -> "GivenName${rowNum.toString().padStart(4, '0')}"
            "LOCATION"  -> TestDataConstants.generateLocation(aTozChar)
            "UNIT_DESCRIPTION_4_SHORT" ->  TestDataConstants.generateLocation(aTozChar)
            "UNIT_CODE_1" -> aTozChar
            "IEP_LEVEL" -> listOf("Basic", "Enhanced", "Standard").random()
            else -> generateByType(dataType)
        }
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
            "DOUBLE PRECISION"       -> (1..100).random().toDouble()
            "BIGINT"                 -> (1..10000).random().toLong()
            "INTEGER"                -> (1..10000).random()
            "DATE"                   -> LocalDate.now()
                                       .minusDays((0..365).random().toLong())
                                       .toString()
            "DATETIME"               -> LocalDateTime.now()
            else -> ""
        }
}

// =====================================================
// 2. MAIN
// =====================================================

fun main() {

    val localDPDgen = System.getenv("DPD_TEST_RESOURCES_PATH") ?: "local"

    // Load the DPD from the environment-specific test-resources folder
    val tableToColumnsMap: Map<String, Map<String, String>> =
        loadDPDtoGenerateTestData(localDPDgen)

    // Read row count supplied by GitHub Actions.
    // Default to 100 if no value has been provided.
    val rowCount = System.getenv("TEST_DATA_ROW_COUNT")
        ?.toIntOrNull()
        ?: 100

    // Generate the test data CSV and upload it to S3
    generateTestData(tableToColumnsMap, rowCount, localDPDgen)

    // Generate SQL script for createTable + copySql
    val sqlScript = sqlScriptGeneration(tableToColumnsMap)
    println(sqlScript)
}


// ==========================================
// 3. SUPPORTING METHODS FOR MAIN
// ==========================================

fun loadDPDtoGenerateTestData(localDPDgen: String): Map<String, Map<String, String>> {
    val objectMapper = ObjectMapper()

    val directory = File(
        System.getenv("DPD_TEST_RESOURCES_PATH")
            ?: "../../dpd/dev/definitions/prisons/test-resources"
    )

    val tableToColumnsMap: Map<String, Map<String, String>> = directory
        .listFiles { file ->
            file.isFile && file.extension == "json"
        }
        ?.associate { file ->

            val root: JsonNode = objectMapper.readTree(file)

            val tableName = file.nameWithoutExtension
                .replace("-", "_")
                .uppercase()

            val reports = root["report"]

            val firstDatasetId = reports
                .map { it["dataset"].asText().removePrefix("\$ref:") }
                .distinct()
                .first()

            val redshiftColumns =
                getRedshiftColumnsMap(root.toString(), firstDatasetId)

            if (localDPDgen == "local") {
                println("Processing file: ${file.name}")
                println("Table Name: $tableName")
                println("Dataset Id: $firstDatasetId")

                val testDPDDir = File("generated-test-dpds")
                testDPDDir.mkdirs()
                // Create new datasource array
                val mutableRoot = root as ObjectNode
                val currentName = root.get("name").asText()
                root.put("name", "Test $currentName")

                mutableRoot.set<JsonNode>(
                    "datasource",
                    objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode()
                            .put("id", "datamart")
                            .put("name", "datamart")
                    )
                )

                val sql = "SELECT * FROM datamart.datahub_test.$tableName"
                mutableRoot["dataset"]
                    ?.firstOrNull { it["id"]?.asText() == firstDatasetId }
                    ?.let { dataset ->
                        (dataset as ObjectNode).put("query", sql)
                    }

                val testDPDFile = File(testDPDDir, "test-${file.name}")

                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(testDPDFile, mutableRoot)
            }

            tableName to redshiftColumns
        } ?: emptyMap()

    return tableToColumnsMap
}


fun getRedshiftColumnsMap(
    root: String,
    datasetId: String
): Map<String, String> {

    val fields = getSchemaFields(root, datasetId)

    val redshiftTypeMapping = mapOf(
        "string" to "VARCHAR(30)",
        "date" to "DATE",
        "double" to "DOUBLE PRECISION",
        "long" to "BIGINT",
        "int" to "INTEGER",
        "datetime" to "DATETIME",
    )

    val redshiftColumns = fields.mapValues { (_, type) ->
        redshiftTypeMapping[type.lowercase()] ?: "VARCHAR(30)"
    }

    return redshiftColumns
}


fun getSchemaFields(
    json: String,
    datasetId: String
): Map<String, String> {

    val mapper = ObjectMapper()
    val root = mapper.readTree(json)

    val dataset = root["dataset"]
        ?.firstOrNull { it["id"]?.asText() == datasetId }
        ?: return emptyMap()

    val fieldMap = dataset
        .get("schema")["field"]
        .associate { field ->
            field["name"].asText() to field["type"].asText()
        }

    return fieldMap
}


fun generateTestData(
    tableToColumnsMap: Map<String, Map<String, String>>,
    rowCount: Int = 1,
    localDPDgen: String
) {
    var outputDir: File? = null
    if (localDPDgen == "local") {
        outputDir = File("generated-test-data")
        outputDir.mkdirs()
    }

    tableToColumnsMap.forEach { (tableName, redshiftColumns) ->

        val csvFileName = "$tableName.csv"

        var outputFile: File? = null
        if (localDPDgen == "local") {
            outputFile = File(outputDir, csvFileName)
        } else {
            outputFile = File(csvFileName)
        }

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

        if (localDPDgen != "local") {
            val s3 = S3Client.builder().build()
            val s3Path = System.getenv("TEST_DATA_S3_PATH") ?: "dpr-working-development"

            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(s3Path)
                    .key("datahub-test-data/$csvFileName")
                    .build(),
                RequestBody.fromFile(outputFile)
            )
        }
    }
}


fun sqlScriptGeneration(
    tableToColumnsMap: Map<String, Map<String, String>>
): String {

    // Get AWS account ID from environment or use default
    val accountId = System.getenv("AWS_ACCOUNT_ID") ?: "771283872747"
    val iamRoleArn = "arn:aws:iam::${accountId}:role/dpr-redshift-cluster-role"
    val s3Path = System.getenv("TEST_DATA_S3_PATH") ?: "dpr-working-development"

    // Build SQL script for all the DPDs
    val sqlScript = buildString {

        appendLine("BEGIN; ")

        tableToColumnsMap.forEach { (tableName, redshiftColumns) ->

            val csvFileName = "$tableName.csv"

            // Create a Redshift table
            appendLine("DROP TABLE IF EXISTS datahub_test.$tableName; ")

            appendLine(
                "CREATE TABLE datahub_test.$tableName ("
            )

            append(
                redshiftColumns.entries.joinToString(",\n") {
                    "    ${it.key} ${it.value}"
                }
            )

            appendLine()
            appendLine("); ")

            // Load the S3 CSV to Redshift table
            appendLine("COPY datahub_test.$tableName ")

            appendLine(
                "FROM 's3://$s3Path/datahub-test-data/$csvFileName' "
            )

            appendLine("IAM_ROLE '$iamRoleArn' ")
            appendLine("CSV ")
            appendLine("IGNOREHEADER 1;")
        }

        appendLine(" COMMIT; ")
    }

    return sqlScript
}


main()
