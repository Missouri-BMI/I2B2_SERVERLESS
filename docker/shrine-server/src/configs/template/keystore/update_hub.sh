#!/bin/bash
# TEMPLATE for src/keystore/<env>/<project>/update_hub.sh (git-ignored per env).
# Re-imports a rotated hub HTTPS cert into the built keystore under output/.
# Replace every {{PLACEHOLDER}}:
#   {{KEYSTORE_ALIAS}}        signing alias (e.g. 1)
#   {{CENTER_CERTIFICATE}}    this node's cert basename, minus .crt
#   {{KEYSTORE_PASSWORD}}     JKS password
#   {{KEYSTORE_FILE}}         built JKS path (e.g. output/shrine.jks)
#   {{HUB_CA_CERT_ALIAS}}     hub CA cert basename/alias
#   {{HUB_HTTPS_CERT_ALIAS}}  hub HTTPS cert basename/alias

KEYSTORE_ALIAS={{KEYSTORE_ALIAS}}
CENTER_CERTIFICATE={{CENTER_CERTIFICATE}}
KEYSTORE_PASSWORD={{KEYSTORE_PASSWORD}}
KEYSTORE_FILE={{KEYSTORE_FILE}}
HUB_CA_CERT_ALIAS={{HUB_CA_CERT_ALIAS}}
HUB_HTTPS_CERT_ALIAS={{HUB_HTTPS_CERT_ALIAS}}


echo 'Deleting previous certs.......\n'

keytool -delete -alias $HUB_HTTPS_CERT_ALIAS  -keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD

echo 'Importing certs.......\n'

keytool -import -v \
-alias $HUB_HTTPS_CERT_ALIAS \
-file $HUB_HTTPS_CERT_ALIAS.crt \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD
