# Instructions for running the Lucene search and auto-suggest indexer

The ontology argument is a directory containing a text dump of the tables specified in the **TABLE_ACCESS** table in the i2b2 database including the **TABLE_ACCESS** table:
For the i2b2 demo ontology, the following tables from the i2b2metadata database are utilized:

I2B2
ICD10_ICD9
PHI
TABLE_ACCESS

The table text dump file can have have any delimiter. The default delimiter is '|'. There should be a header line in the exported data files.
The exported TABLE_ACCESS is required to have the following header columns:
c_table_cd, c_table_name, c_hlevel, c_fullname, c_name, c_synonym_cd, c_visualattributes, c_basecode, c_metadataxml, c_tooltip

The other exported data tables are required to have the following header columns:
c_hlevel, c_fullname, c_name, c_synonym_cd, c_visualattributes, c_basecode, c_metadataxml, c_tooltip, m_applied_path


Here is an example line in the ontology file (| delimited):

5|\Diagnoses\(A00-B99) Cert~ugmm\(A00-A09) Inte~3luo\(A01) Typhoid~nea4\(A01.4) Paraty~o5cj\(002.9)Paratyp~ksu3\|Paratyphoid fever, unspecified|N|LI ||ICD9:002.9||concept_cd|concept_dimension|concept_path|T|LIKE|\Diagnoses\(A00-B99) Cert~ugmm\(A00-A09) Inte~3luo\(A01) Typhoid~nea4\(A01.4) Paraty~o5cj\(002.9)Paratyp~ksu3\||Diagnoses \ Certain infectious and parasitic diseases (a00-b99) \ Intestinal infectious diseases (a00-a09) \ Typhoid and paratyphoid fevers \ Paratyphoid fever, unspecified \ Paratyphoid fever, unspecified|@|2014-05-30 00:00:00|||Integration_tool|||\Diagnoses\(A00-B99) Cert~ugmm\(A00-A09) Inte~3luo\(A01) Typhoid~nea4\(A01.4) Paraty~o5cj\|(002.9)Paratyp~ksu3|

The category definition file can have any delimiter. The default delimiter is ','. This file contains the concept path, code category, concept category, and optionally the code set.
The concept path is a combination of the C_TABLE_CD and C_FULLNAME columns in the TABLE_ACCESS table. There should be no header line in the category definition file
Here is an example of the category definition file (must be tab delimited):

\\i2b2_DEMO\i2b2\Demographics   Demographics    Demographic     
\\i2b2_DIAG\i2b2\Diagnoses      Diagnoses   Diagnosis   Diagnoses ICD9
\\i2b2_EXPR\i2b2\Expression Profiles Data   Expression Profiles Data   


How to run:

```bash
java -jar shrine-ontology-lucene-indexer-<insert shrine version here>-jar-with-dependencies.jar  <command line arguments>
```

Example cmd to create the lucene index:
```bash
java -jar shrine-lucene-indexer-<insert shrine version here>-jar-with-dependencies.jar -o demo_ontology/dsv/ -c category_definition.txt
```
Example cmd to create the suggest index:
```bash
java -jar shrine-lucene-indexer-<insert shrine version here>-jar-with-dependencies.jar -a -o demo_ontology/dsv/ -c category_definition.txt
```

Example cmd to create the lucene index with a tab delimiter for the table text dump:
```bash
java -jar shrine-lucene-indexer-<insert shrine version here>-jar-with-dependencies.jar -p '\t' -o demo_ontology/dsv/ -c category_definition.txt
```

For a list of command line arguments (required and optional), run without any arguments:
```bash
java -jar shrine-ontology-lucene-indexer-<insert shrine version here>-jar-with-dependencies.jar
```
 