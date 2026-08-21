#!/usr/bin/env kotlinc -script

@file:DependsOn("com.google.code.gson:gson:2.10.1")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.18.2")
@file:DependsOn("org.apache.poi:poi-ooxml:5.2.5")
@file:DependsOn("software.amazon.awssdk:s3:2.25.53")

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.random.Random
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest

// ==========================================
// - Input DPD from dpd/dev/definitions/prisons/test-resources
// - S3 test-data csv Upload
// - Redshift sql script (Create table + Load the test-data to the table)
// - Create the Test DPD
// ==========================================

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
            "DATE" -> LocalDate.now().minusDays((0..365).random().toLong()).toString()
            else -> ""
        }
}

// =====================================================
// Main
// - Load the DPD from dpd/dev/definitions/prisons/test-resources
// - create a map with the column name and datatype
// - get the Redshift datatypes for the types of the column
// - Create a redshift table with the above details
// - Generate the test data for the column name and datatype for the table
// - Create the CSV
// - upload it in the s3
// - Once uploaded it to s3 + Create a sql script to load redshift table
// - Connect to redshift and execute the above 2 sql script one for creating a redshift table and loading the table
// - create a test DPD with the redshift table select statement
// =====================================================

fun main() {
//    println("Hello from Kotlin triggered by GitHub Actions!")
//    println("Working directory = ${System.getProperty("user.dir")}")

    // - Load the DPD from dpd/dev/definitions/prisons/test-resources
    val objectMapper = ObjectMapper()
    val file = File(
        "dpd/dev/definitions/prisons/test-resources/ors-prisoner-iep-levels.json"
    )
    val root: JsonNode = objectMapper.readTree(file)

    val tableName = file.nameWithoutExtension
        .replace("-", "_")
        .uppercase()

    val reports = root["report"]

    val datasetId = reports
        .first { it["name"].asText() == "By IEP Level and IEP Date" }
        .get("dataset")
        .asText()

    // - create a map with the column name and datatype
    val redshiftColumns = getRedshiftColumnsMap(root.toString(), datasetId)

// ---- Generate the test data for the column name and datatype for the table
// ---- Create the CSV
// ---- upload it in the s3

   val csvFileName = generateTestData(tableName, redshiftColumns as Map<String, String>,  1)
//   println(csvFileName)

// - Generate sql script for, createTable + copySql and pass it on to the Redshift
    val sqlScript = sqlScriptGeneration(tableName, redshiftColumns,  csvFileName)
    println(sqlScript)
}

fun getSchemaFields(json: String, datasetId: String): Map<String, String>? {
    val mapper = ObjectMapper()
    val root = mapper.readTree(json)

    val dataset = root["dataset"]
        ?.firstOrNull { it["id"]?.asText() == datasetId }

    val fieldMap = dataset?.get("schema")["field"]
        ?.associate { field ->
            field["name"].asText() to field["type"].asText()
        }

    return fieldMap
}

fun getRedshiftColumnsMap (root: String, datasetId: String) : Map<String, String>? {
    val fields = getSchemaFields(root.toString(), datasetId)
    val redshiftTypeMapping = mapOf(
        "string" to "VARCHAR(30)",
        "date" to "DATE",
        "double" to "DOUBLE PRECISION",
        "long" to "BIGINT"
    )

    val redshiftColumns = fields?.mapValues { (_, type) ->
        type?.let { redshiftTypeMapping[it.lowercase()] } ?: "VARCHAR(30)"
    }

    return redshiftColumns
}


fun generateTestData(tableName: String, redshiftColumns:Map<String, String>,   rowCount: Int = 1): String {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet(tableName)
    val headerRow = sheet.createRow(0)

    redshiftColumns.keys.forEachIndexed { index, column ->
        headerRow.createCell(index).setCellValue(column)
    }

    repeat(rowCount) { rowIndex ->
        val row = sheet.createRow(rowIndex + 1)
        redshiftColumns.entries.forEachIndexed { colIndex, (column, type) ->
            val value =
                TestDataGenerator.generate(
                    columnName = column,
                    dataType = type,
                    rowNum = rowIndex + 1
                )
            when (value) {
                is String -> row.createCell(colIndex).setCellValue(value)
                is Double -> row.createCell(colIndex).setCellValue(value)
                is Long -> row.createCell(colIndex).setCellValue(value.toDouble())
                is Int -> row.createCell(colIndex).setCellValue(value.toDouble())
                else -> row.createCell(colIndex).setCellValue(value.toString())
            }
        }
    }

    redshiftColumns.keys.indices.forEach{
        sheet.autoSizeColumn(it)
    }

    val csvFileName = "${tableName}.csv"
    val outputFile = File(
        csvFileName
    )

    FileOutputStream(outputFile).use {
        workbook.write(it)
    }
    workbook.close()


    val s3 = S3Client.builder().build()
    s3.putObject(
        PutObjectRequest.builder()
            .bucket("dpr-working-development")
            .key("datahub-test-data/${csvFileName}")
            .build(),
        RequestBody.fromFile(outputFile)
    )

    return csvFileName
}

fun sqlScriptGeneration(tableName: String, redshiftColumns: Map<String, String>, csvFileName: String): String{

    // - Create a redshift table with the above details
    val sqlScript = buildString {
        appendLine("BEGIN; ")
        appendLine("DROP TABLE IF EXISTS datahub_test.$tableName; ")
        appendLine("CREATE TABLE datahub_test.$tableName (")
        append(
            redshiftColumns?.entries?.joinToString(",\n") {
                "    ${it.key} ${it.value}"
            }
        )
        appendLine()
        appendLine("); ")
        appendLine("COPY datahub_test.$tableName ")
        appendLine("FROM 's3://dpr-working-development/datahub-test-data/$csvFileName' ")
        appendLine("CSV ")
        appendLine("IGNOREHEADER 1;")
        appendLine(" COMMIT; ")
    }

    return sqlScript

}

main()
