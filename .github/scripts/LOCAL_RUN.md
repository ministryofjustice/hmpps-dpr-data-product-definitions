## LOCAL RUN for test data generation 

brew install kotlin
kotlinc -version

chmod +x ./.github/scripts/LocalTestDataGen.main.kts
cd ./.github/scripts
kotlinc -script LocalTestDataGen.main.kts

Once the script has been executed, the following outputs are generated:
1> A test data CSV file for each DPD definition, located under:
    .github/scripts/generated-test-data
2> A corresponding test DPD file for each DPD definition, located under:
    .github/scripts/generated-test-dpds
3> A consolidated Redshift SQL script, printed to the console, 
   containing the table creation and data loading statements for all processed DPDs.
