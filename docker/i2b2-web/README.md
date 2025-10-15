# i2b2 Web Docker Container

## Overview
This Docker container runs i2b2 Web client and i2b2 webclient classic with Apache HTTP, Shibboleth, and PHP managed by supervisord. All logs are directed to stdout for Docker compatibility.

## Configuration Structure

### Common Configuration
Shared configuration files are stored in `Configuration/Common`.

### Target-Specific Configuration
Credential and secret files must be created for each target environment (dev, prod, local) using `template-target` as a base.

**Setup Steps:**

1. Copy `template-target` to your target environment directory [dev,prod,local]
2. Update the following configuration files:

**Apache Configuration:**
- `apache/ajp.conf` - Configure secrets
- `apache/i2b2.conf` - Set server name and directives

**Shibboleth Configuration:**
- `shibboleth/shibboleth2.xml` - Configure entity-id
- `shibboleth/attribute-map.xml` - Use default settings

### Generating Shibboleth Certificates

Create a certificate configuration file at `shibboleth/certificate.cnf`:

```
[req]
prompt=no
default_bits=2048
encrypt_key=no
default_md=sha1
distinguished_name=dn
# PrintableStrings only
string_mask=MASK:0002
x509_extensions=ext

default_keyfile=shibboleth/sp-key.pem

[dn]
CN=i2b2-dev.nextgenbmi.umsystem.edu

[ext]
subjectAltName= DNS.1:i2b2-dev.nextgenbmi.umsystem.edu, \
                URI.1:https://i2b2-dev.nextgenbmi.umsystem.edu/shibboleth
subjectKeyIdentifier=hash
```

Then, execute `certificate.sh`. It will create `sp-cert.pem` and `sp-key.pem` in shibboleth directory

## Building and Running

Use the Makefile for build and deployment tasks. Run `make help` for available commands.