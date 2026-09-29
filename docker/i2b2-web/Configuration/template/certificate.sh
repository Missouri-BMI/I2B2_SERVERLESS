#!/usr/bin/env bash
# TEMPLATE for Configuration/<env>/<project>/certificate.sh (git-ignored per env).
# No per-env values to fill in; copy as-is. Generates the Shibboleth SP
# self-signed keypair (sp-cert.pem / sp-key.pem) from shibboleth/certificate.cnf.

openssl req -new -x509 -config shibboleth/certificate.cnf -text -out shibboleth/sp-cert.pem -days 3652 -nodes
