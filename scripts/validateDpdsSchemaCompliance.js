/**
 * Validates all the DPD JSON files against the JSON schema:
 * https://raw.githubusercontent.com/ministryofjustice/hmpps-digital-prison-reporting-data-product-definitions-schema/main/schema/data-product-definition-schema.json
 */


const Ajv = require("ajv");
const fs = require("fs");
const glob = require("glob");

const schema = JSON.parse(fs.readFileSync(process.env.SCHEMA_LOCATION));

const ajv = new Ajv({ allErrors: true, strict: false, coerceTypes: true });
const validate = ajv.compile(schema);

const files = glob.sync("dpd/**/*.json");

let valid = true;

for (const file of files) {
    const data = JSON.parse(fs.readFileSync(file));
    if (!validate(data)) {
        console.error(`Errors in ${file}:`);
        console.error(JSON.stringify(validate.errors, null, 2));
        valid = false;
    }
}

if (!valid) process.exit(1);