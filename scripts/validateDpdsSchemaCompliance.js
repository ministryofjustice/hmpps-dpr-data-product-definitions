/**
 * Validates all the DPD JSON files against the JSON schema:
 * https://raw.githubusercontent.com/ministryofjustice/hmpps-digital-prison-reporting-data-product-definitions-schema/main/schema/data-product-definition-schema.json
 */


const Ajv = require("ajv");
const fs = require("fs");
const glob = require("glob");

const schema = JSON.parse(fs.readFileSync(process.env.SCHEMA_LOCATION));

const ajv = new Ajv({ allErrors: true, strict: false, coerceTypes: false });
const validate = ajv.compile(schema);

const files = glob.sync(
    // "dpd/dev,test,preprod,prod}/definitions/{prisons/orphanage,probation}/**/*.json"
    "dpd/dev/definitions/prisons/clean/**/*.json"
);

if (files.length === 0) {
    console.error("No matching DPD JSON files were found.");
    process.exit(1);
}


let valid = true;

for (const file of files) {
    const data = JSON.parse(fs.readFileSync(file));
    if (!validate(data)) {
        const filteredErrors = validate.errors
        //     .filter(
        //     //The below exclusion is temporary to match previous validation behaviour.
        //     //Long term the DPDs will have to be brought in line with the schema.
        //     err => err.keyword !== "additionalProperties"
        // );

        if (filteredErrors.length > 0) {
            console.error(`Errors in ${file}:`);
            console.error(JSON.stringify(filteredErrors, null, 2));
            valid = false;
        }
    }
}

if (!valid) process.exit(1);