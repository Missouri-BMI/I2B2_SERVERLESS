# i2b2 Web Docker Container

## Overview
This Docker container runs i2b2 Web client and i2b2 webclient classic with Apache HTTP, Shibboleth, and PHP managed by supervisord. All logs are directed to stdout for Docker compatibility.

## Configuration Structure

### Common Configuration
Shared configuration files are stored in `Configuration/Common`.

### Target-Specific Configuration
Credential and secret files must be created for each target environment (dev, prod, local) using `template-target` as a base. eg dev/mu; prod/mu-shrine etc.

**Setup Steps:**

1. Copy `template-target` to your target environment directory [dev,prod,local]
2. Update the following configuration files:

**Apache Configuration:**
- `apache/ajp.conf` - Configure secrets
- `apache/i2b2.conf` - Set server name and directives
- `apache/sp.conf` - Shibboleth apache side configuration

**Shibboleth Configuration:**
- `shibboleth/shibboleth2.xml` - Configure entity-id
- `shibboleth/attribute-map.xml` - Use default settings
- `shibboleth/certificate.cnf` - Use to generate shibboleth pub/private keys
It will create `sp-cert.pem` and `sp-key.pem` in shibboleth directory

## Building and Running

Use the Makefile for build and deployment tasks. 