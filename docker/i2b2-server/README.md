- Use tempate/commands-temp.cli and copy to 'env'/commands.cli and keep snowflake rsa_key.p8
to generate wildfly DB datasources.

- Use make file to build target image
  - `make local` builds `i2b2-wildfly:local` from configuration/local (no push; same image docker-compose uses)
  - `make run-local` builds and runs it on http://localhost:8080/i2b2/ (`make logs-local`, `make stop-local`)
  - `make mu-dev | shrine-dev | washu-dev | *-prod` build and push the per-variant images to ECR
