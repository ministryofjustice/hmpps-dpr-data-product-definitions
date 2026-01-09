/**
 * Detects duplicate variant IDs inside all DPDs
 */

const fs = require('fs')
const path = require('path')

const folders = ['dev', 'test', 'preprod', 'prod'].map(env => `dpd/${env}/definitions/prisons/orphanage`)
const fileMap = {}
folders.forEach((folder) => {
  fs.globSync(path.join(process.cwd(), folder, "*.json")).forEach((dpdFileLocation) => {
    try {
      const fileContents = JSON.parse(String(fs.readFileSync(dpdFileLocation)))
      const reportMap = {}
      fileContents.report.forEach((report) => {
        if (reportMap[report.id]) {
          reportMap[report.id] += 1
          return
        }
        reportMap[report.id] = 1
      })
      const filteredReportMap = Object.entries(reportMap).filter(([_, timesFound]) => timesFound > 1)
      if (filteredReportMap.length > 0) {
        fileMap[dpdFileLocation] = Object.fromEntries(filteredReportMap)
      }
    } catch (e) {
      console.error(`Skipped ${dpdFileLocation} due to error`)
      console.error(e)
    }
  })
})

if (Object.keys(fileMap).length > 0) {
  console.error('The following files have non unique entries for variant ids - these will be printed as `"<variant_id>": <numTimes>`')
  console.error(JSON.stringify(fileMap, undefined, 2))
  process.exit(1)
}
process.exit(0)