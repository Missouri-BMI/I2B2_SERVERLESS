#!/bin/bash
#create Certificate Authority(CA), certificates, and shrine.keystore

keystoreDir="/usr/local/shrine/conf/shrine-keystores"

mkdir -p /tmp/demoCA
mkdir -p /tmp/demoCA/certs
mkdir -p /tmp/demoCA/crl
mkdir -p /tmp/demoCA/newcerts
mkdir -p /tmp/demoCA/private
touch /tmp/demoCA/index.txt
mkdir -p /tmp/demoCA/private
mkdir -p $keystoreDir

cd /tmp

echo "========1. Creating Certificate Authority========"

echo "Running openssl req" #needs an index.txt file to exist
openssl req  -new -keyout /tmp/demoCA/private/cakey.pem -out /tmp/demoCA/careq.pem -subj "/C=US/ST=MA/L=Boston/O=Harvard/OU=SHRINE/CN=shrine-webclient-ca" -passout pass:password

echo "Running openssl ca cmd"
openssl ca  -create_serial -out /tmp/demoCA/cacert.pem -days 1095 -key password -batch -keyfile /tmp/demoCA/private/cakey.pem -selfsign -extensions v3_ca  -infiles /tmp/demoCA/careq.pem


for commonName in "$@"
do
    mkdir -p $keystoreDir/$commonName
    keystoreFile=shrine.keystore
    echo "========2. Generating a Certificate Signing Request (CSR)========"
    echo "Running openssl req"
    openssl req  -new  -keyout newkey.pem -out newreq.pem -subj "/C=US/ST=MA/L=Boston/O=Harvard/OU=SHRINE/CN=$commonName" -passout pass:password


    echo "========3. Signing the Certificate Signing Request (CSR)========"
    openssl ca  -policy policy_anything -out newcert.pem -batch -key password -infiles newreq.pem


    echo "========4. Creating shrine.keystore========"
    echo "Running openss1 pkcs12"
    openssl pkcs12 -export -in newcert.pem -inkey newkey.pem -out shrine-client.p12  -passout pass:password -passin pass:password -password pass:password -name "$commonName"

    echo "Running keytool -importkeystore"
    keytool -importkeystore -srckeystore shrine-client.p12 -srcstoretype pkcs12 -srcstorepass password -deststorepass password -destkeystore $keystoreDir/$commonName/$keystoreFile -deststoretype JKS

    echo "Running keytool -import"
    keytool -import -v -alias shrine-webclient-ca -srcstorepass password -deststorepass password -noprompt -file demoCA/cacert.pem -keystore $keystoreDir/$commonName/$keystoreFile
    keytool -list -keystore $keystoreDir/$commonName/$keystoreFile
done

