/**
 * Validates all the DPD JSON files which contain no schemaversion or schemaversion 1.0.0, ignoring unknown properties and coercing types against the JSON schema:
 * https://raw.githubusercontent.com/ministryofjustice/hmpps-digital-prison-reporting-data-product-definitions-schema/refs/heads/main/schema/1.0.0/data-product-definition-schema.json
 * Validates all the DPD JSON files with schemaversion 1.1.0, not ignoring unknown properties and not coercing types against the JSON schema:
 * https://raw.githubusercontent.com/ministryofjustice/hmpps-digital-prison-reporting-data-product-definitions-schema/refs/heads/main/schema/1.1.0/data-product-definition-schema.json
 */


const Ajv = require("ajv");
const fs = require("fs");
const glob = require("glob");

const schema100Location = process.env.SCHEMA_1_0_0_LOCATION;
const schema110Location = process.env.SCHEMA_1_1_0_LOCATION;

if (!schema100Location) {
    console.error("SCHEMA_1_0_0_LOCATION must be set.");
    process.exit(1);
}

if (!schema110Location) {
    console.error("SCHEMA_1_1_0_LOCATION must be set.");
    process.exit(1);
}

function readSchema(location, version) {
    try {
        return JSON.parse(fs.readFileSync(location, "utf8"));
    } catch (error) {
        console.error(
            `Failed to read JSON schema ${version} from ${location}:`,
        );
        console.error(error.message);
        process.exit(1);
    }
}

const schema100 = readSchema(schema100Location, "1.0.0");
const schema110 = readSchema(schema110Location, "1.1.0");

const ajv100 = new Ajv({allErrors: true, strict: false, coerceTypes: true,});
const validateSchema100 = ajv100.compile(schema100);

const ajv110 = new Ajv({allErrors: true, strict: false, coerceTypes: false,});
const validateSchema110 = ajv110.compile(schema110);

const files = glob.sync("dpd/**/*.json");


if (files.length === 0) {
    console.error("No matching DPD JSON files were found.");
    process.exit(1);
}


let valid = true;

for (const file of files) {
    let data;
    try {
        data = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (error) {
        console.error(`Invalid JSON in ${file}:`);
        console.error(error.message);
        valid = false;
        continue;
    }
    const schemaVersion = data.schemaversion;

    if (schemaVersion !== undefined  && schemaVersion !== "1.0.0" && schemaVersion !== "1.1.0") {
        console.error(`Unsupported schema version in ${file}:`);
        console.error(`Expected "1.0.0" or "1.1.0", but was ${JSON.stringify(schemaVersion)}.`);
        valid = false;
        continue;
    }

    if (schemaVersion === "1.1.0") {
        if (!validateSchema110(data)) {
            const errors = validateSchema110.errors ?? [];

            console.error(`Schema 1.1.0 validation errors in ${file}:`);
            console.error(JSON.stringify(errors, null, 2));
            valid = false;
        }
        continue;
    }

    if (!validateSchema100(data)) {
        const errors = (validateSchema100.errors ?? []).filter(
            err => err.keyword !== "additionalProperties",
        );

        if (errors.length > 0) {
            const versionDescription =
                schemaVersion === "1.0.0"
                    ? "schema 1.0.0"
                    : "no schemaversion declared - validated against schema 1.0.0";

            console.error(`Legacy schema 1.0.0 validation errors in ${file} (${versionDescription}):`);
            console.error(JSON.stringify(errors, null, 2));
            valid = false;
        }
    }
}

if (!valid) process.exit(1);
console.log(`Successfully validated ${files.length} DPD JSON file(s).`);