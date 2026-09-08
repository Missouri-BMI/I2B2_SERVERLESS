wget https://repo.open.catalyst.harvard.edu/nexus/content/groups/public/net/shrine/shrine-api-war/4.4.1/shrine-api-war-4.4.1.war -O shrine-api.war

https://harvardcatalyst.atlassian.net/wiki/spaces/SHRINE/pages/556630191/SHRINE+4.4.1+Installation+Guide

https://harvardcatalyst.atlassian.net/wiki/spaces/SHRINE/pages/556630829/SHRINE+4.4.1+Chapter+10.1+-+Install+the+Lucene+Index+Files

### Docker implementation of i2b2 shrine
```sh
# change directory to shrine-server
$ cd ./docker/shrine-server/

# copy config files 
./src/configs/env/mu/context.xml
./src/configs/env/mu/password.conf
./src/configs/env/mu/server.xml
./src/configs/env/mu/shrine.conf

# copy lucene_index, suggest_index, adaptermappings
./src/data/act-4.1/lucene_index
./src/data/act-4.1/suggest_index
./src/data/act-4.1/adapter_mapping.csv
./src/data/act-4.1/logo.png

# place keystore
src/keystore/${PROJECT}/output/ # eg .jks/.p12

```

Use makefile to build desired target in environment e.g. dev/mu, prod/washu

### Generating Keystore
handle_jks.sh
```
#!/bin/bash
KEYSTORE_ALIAS=
KEYSTORE_PASSWORD=
KEYSTORE_FILE=shrine-dev-nextgenbmi.jks


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

```
import_cert.sh
```
#!/bin/bash

KEYSTORE_ALIAS=
CENTER_CERTIFICATE=UMissouri-test
KEYSTORE_PASSWORD=
KEYSTORE_FILE=shrine-dev-nextgenbmi.jks
HUB_CA_CERT_ALIAS=shrine-act-test-ca
HUB_HTTPS_CERT_ALIAS=shrine-act-test.hms.harvard.edu

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
```
update-hub.sh
```
#!/bin/bash

KEYSTORE_ALIAS=
CENTER_CERTIFICATE=
KEYSTORE_PASSWORD=
KEYSTORE_FILE=output/shrine-dev-nextgenbmi.jks
HUB_CA_CERT_ALIAS=shrine-act-test-ca
HUB_HTTPS_CERT_ALIAS=shrine-act-test.hms.harvard.edu


echo 'Deleting previous certs.......\n'

keytool -delete -alias $HUB_HTTPS_CERT_ALIAS  -keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD

echo 'Importing certs.......\n'

keytool -import -v \
-alias $HUB_HTTPS_CERT_ALIAS \
-file $HUB_HTTPS_CERT_ALIAS.crt \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD
```
view_certs.sh
```
#!/bin/bash

KEYSTORE_ALIAS=
KEYSTORE_PASSWORD=
KEYSTORE_FILE=output/shrine-dev-nextgenbmi.jks


echo 'Retrieving list of keys.......\n'
keytool -list \
-keystore $KEYSTORE_FILE \
-storepass $KEYSTORE_PASSWORD
```