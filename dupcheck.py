
import json, os, glob
from collections import defaultdict

# Adjust this path to where you've checked out the repo locally:
FOLDER = "dpd/test/definitions/prisons/orphanage"

key_to_files = defaultdict(list)

for path in glob.glob(os.path.join(FOLDER, "*.json")):
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        dpd_id = data.get("id")
        datasets = data.get("report", [])
        # Construct ${id}/${dataset[index].id} for every dataset entry
        for ds in datasets:
            ds_id = ds.get("id")
            if dpd_id and ds_id:
                key = f"{dpd_id}/{ds_id}"
                key_to_files[key].append(os.path.basename(path))
    except Exception as e:
        print(f"⚠️ Skipped {os.path.basename(path)}: {e}")

# Print only keys that appear in more than one file (global duplicates)
for key, files in key_to_files.items():
    if len(files) > 1:
        # De-duplicate file names while preserving order
        seen = []
        for fn in files:
            if fn not in seen:
                seen.append(fn)
        print(f"{key}  =>  {', '.join(seen)}")


