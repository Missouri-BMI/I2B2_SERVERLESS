#!/bin/bash
# TEMPLATE for src/keystore/<env>/<project>/handle_jks.sh (git-ignored per env).
# Lists the keystore and generates a code-signing request (CSR) for the alias.
# Replace every {{PLACEHOLDER}}:
#   {{KEYSTORE_ALIAS}}     signing alias in the JKS (e.g. 1)
#   {{KEYSTORE_PASSWORD}}  JKS password (matches creds.conf shrine.keystore.password)
#   {{KEYSTORE_FILE}}      JKS filename (e.g. shrine.jks)
KEYSTORE_ALIAS={{KEYSTORE_ALIAS}}
KEYSTORE_PASSWORD={{KEYSTORE_PASSWORD}}
KEYSTORE_FILE={{KEYSTORE_FILE}}


echo 'Retrieving list of keys.......\n'
keytool -list \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD

echo 'Generating code signing request......\n'
keytool -certreq \
-alias $KEYSTORE_ALIAS \
-keyalg RSA \
-file $KEYSTORE_ALIAS.csr \
-keypass $KEYSTORE_PASSWORD \
-storepass $KEYSTORE_PASSWORD \
-keystore $KEYSTORE_FILE

echo 'Previewing code signing request......\n'
openssl req \
-in $KEYSTORE_ALIAS.csr \
-subject -noout
