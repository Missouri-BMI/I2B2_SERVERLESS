#!/usr/bin/env bash

openssl req -new -x509 -config shibboleth/certificate.cnf -text -out shibboleth/sp-cert.pem -days 3652 -nodes

