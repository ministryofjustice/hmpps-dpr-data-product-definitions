#!/bin/bash

DPD_FOLDER="./src/main/resources/dev/"
DPDs=($(find $DPD_FOLDER -name "*.json"))

for FILE_PATH in "${DPDs[@]}"
do
  FILENAME=$(basename $FILE_PATH)
  DPD_PATH=${FILE_PATH/$DPD_FOLDER\//}
  DPD_PATH=${DPD_PATH/\/$FILENAME/}
  DPD_ID=$(jq -r '.id' $FILE_PATH)

  echo "Path: $DPD_PATH File: $FILENAME ID: $DPD_ID"

  JSON_TEXT=$(jq -Rsc <$FILE_PATH)
  JSON_TEXT=$(echo $JSON_TEXT | sed 's/^"//')
  JSON_TEXT=$(echo $JSON_TEXT | sed 's/"$//')

  aws dynamodb put-item \
    --table-name dpr-data-product-definition \
    --item "{ \"data-product-id\": { \"S\": \"$DPD_ID\" }, \"category\": { \"S\": \"$DPD_PATH\" }, \"definition\": { \"S\": \"$JSON_TEXT\" }, \"filename\": { \"S\": \"$FILENAME\" } }"

done
