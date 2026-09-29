#!/bin/bash
# TEMPLATE for src/keystore/<env>/<project>/view_certs.sh (git-ignored per env).
# Lists the entries in the built keystore under output/.
# Replace every {{PLACEHOLDER}}:
#   {{KEYSTORE_ALIAS}}     signing alias (e.g. 1)
#   {{KEYSTORE_PASSWORD}}  JKS password
#   {{KEYSTORE_FILE}}      built JKS path (e.g. output/shrine.jks)

KEYSTORE_ALIAS={{KEYSTORE_ALIAS}}
KEYSTORE_PASSWORD={{KEYSTORE_PASSWORD}}
KEYSTORE_FILE={{KEYSTORE_FILE}}


echo 'Retrieving list of keys.......\n'
keytool -list \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD
