#!/bin/bash
# TEMPLATE for src/keystore/<env>/<project>/import_cert.sh (git-ignored per env).
# Imports the hub CA + hub HTTPS certs and this node's center cert into the JKS.
# Replace every {{PLACEHOLDER}}:
#   {{KEYSTORE_ALIAS}}        signing alias (e.g. 1)
#   {{CENTER_CERTIFICATE}}    this node's cert basename, minus .crt (e.g. site-node)
#   {{KEYSTORE_PASSWORD}}     JKS password
#   {{KEYSTORE_FILE}}         JKS filename (e.g. shrine.jks)
#   {{HUB_CA_CERT_ALIAS}}     hub CA cert basename/alias (e.g. hub-ca)
#   {{HUB_HTTPS_CERT_ALIAS}}  hub HTTPS cert basename/alias (e.g. hub.example.org)

KEYSTORE_ALIAS={{KEYSTORE_ALIAS}}
CENTER_CERTIFICATE={{CENTER_CERTIFICATE}}
KEYSTORE_PASSWORD={{KEYSTORE_PASSWORD}}
KEYSTORE_FILE={{KEYSTORE_FILE}}
HUB_CA_CERT_ALIAS={{HUB_CA_CERT_ALIAS}}
HUB_HTTPS_CERT_ALIAS={{HUB_HTTPS_CERT_ALIAS}}

echo 'Importing certs.......\n'
keytool -import -v \
-alias $HUB_CA_CERT_ALIAS \
-file $HUB_CA_CERT_ALIAS.crt \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD

keytool -import -v \
-alias $HUB_HTTPS_CERT_ALIAS \
-file $HUB_HTTPS_CERT_ALIAS.crt \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD

keytool -import -v \
-alias $KEYSTORE_ALIAS \
-file $CENTER_CERTIFICATE.crt \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD \
-keypass $KEYSTORE_PASSWORD \
-trustcacerts


echo 'Retrieving list of keys.......\n'
keytool -list \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD
