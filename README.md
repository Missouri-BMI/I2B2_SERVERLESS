# i2b2 + SHRINE on AWS ECS — build & deploy runbook

**Per-variant container images with configuration baked in at build.** No secret store, no
KMS, no runtime config fetch, no entrypoint config step.

## How it works (read this first)

- **Per-variant images** — one image per (component, variant), selected by build args and
  encoded in the tag: `i2b2-wildfly:{mu,shrine,shrine-washu}-v1.8.3`,
  `i2b2-web:{mu,mu-shrine,washu-shrine}-v1.8.3`, `shrine-server:{mu,washu}-v1.8.3`.
- **Config lives in the component folders** and is COPY'd into the image **at build** (the
  `ENVIRONMENT` / `TARGET` / `PROJECT` build args pick the variant):
  - `docker/i2b2-server/configuration/<env>/` — `commands.cli` + `rsa_key.p8`
  - `docker/i2b2-web/Configuration/<env>/<target>/` — `apache/*.conf` + `shibboleth/*`
  - `docker/shrine-server/src/configs/<env>/<project>/` + `src/keystore/<env>/<project>/output/`
- **i2b2-server bakes its datasources at build** — the Dockerfile runs `build-config.sh`
  during the build so `standalone.xml` ships already configured; the container just starts
  WildFly (no entrypoint). i2b2-web/shrine-server likewise bake config into final paths and
  start the service directly.
- **CloudFormation** (all under `deployments/`): optional shared **network**
  (`infrastructure/cloudformation.yaml`), per-env **platform** (`platform.yaml` — ECR +
  task-execution role + log group, no secret store), per-app **network edge**
  (`infrastructure/{i2b2,shrine-mu,shrine-washu}.yaml` — ALB + target group + security groups),
  per-app **compute** (`app/app-i2b2.yaml`, `app/app-shrine.yaml`). The app stacks take the
  **target group + service SG as parameters** (resolved from the net stack at deploy time).

The three apps, each its own ALB + ECS service:

| App | Containers | Image tags |
|-----|------------|------------|
| `i2b2` | `i2b2-web` + `i2b2-wildfly` | `i2b2-web:mu`, `i2b2-wildfly:mu` |
| `shrine-mu` | `i2b2-wildfly` + `shrine-server` + `shrine-reverse-proxy` | `i2b2-wildfly:shrine`, `shrine-server:mu`, `i2b2-web:mu-shrine` |
| `shrine-washu` | (same, washu) | `i2b2-wildfly:shrine-washu`, `shrine-server:washu`, `i2b2-web:washu-shrine` |

---

## Prerequisites

- **Tools:** AWS CLI v2, Docker, `make`, `git`, `python3`.
- **Clone with submodules:** `git clone --recurse-submodules <repo-url>`
  Branches: `i2b2-core-server` `release/mu-1.8.3`, `i2b2-webclient` `release/mu-1.8.3`,
  `i2b2-webclient-classic` `release/mu` (+ `release/mu-admin`, `release/washu-admin`),
  `i2b2-data` `snowflake/release-1.8.3`.
- **AWS:** permission to create the CFN stacks; an existing VPC + ECS cluster + public/private
  subnets (or use the optional greenfield network stack).

---

## Run locally (no AWS needed)

```sh
cd docker && docker compose build && docker compose up
```
Compose builds each image with the `local` build args (`ENVIRONMENT=local`, `TARGET=mu`,
`PROJECT=mu`) so the `local` config is baked in, then runs them + a local MariaDB. Needs a
prebuilt SHRINE WAR at `shrine-server/src/shrine-4.4.0/apps/shrine-api-war/target/shrine-api.war`.

---

## Deploy to AWS — from zero to a running environment

The walkthrough uses **dev**; `-prod` targets are identical against the prod account.
Set your AWS SSO profiles at the top of the `Makefile` (`AWS_PROFILE_DEV` / `AWS_PROFILE_PROD`).

### 1. Authenticate (starting with no local credentials)
```sh
make aws-login-dev        # aws sso login --profile <AWS_PROFILE_DEV>
make docker-login-dev     # ECR docker login
```

### 2. Put the configuration in the component folders
Edit/create each variant's config directly in the folders (this is the source — it gets
baked into the image). Real DB credentials and keystores live here too:
- `docker/i2b2-server/configuration/<env>/commands.cli` (+ `rsa_key.p8`)
- `docker/i2b2-web/Configuration/<env>/<target>/{apache,shibboleth}/…`
  (Shibboleth SP keypair via the `certificate.sh` in that dir)
- `docker/shrine-server/src/configs/<env>/<project>/{context.xml,server.xml,shrine.conf,password.conf}`
  and `docker/shrine-server/src/keystore/<env>/<project>/output/*.jks|*.p12`

These files are gitignored (they hold credentials) — keep them safe out of band. The
Dockerfiles COPY the variant selected by the `ENVIRONMENT`/`TARGET`/`PROJECT` build args.

### 3. Build & push the images (config is baked in per variant)
```sh
make images-dev           # build + push every variant tag (mu / shrine / shrine-washu / …) to dev ECR
```

### 4. Deploy the CloudFormation stacks
```sh
IDS='VPC_ID=vpc-xxx PUBLIC_SUBNETS=subnet-a,subnet-b PRIVATE_SUBNETS=subnet-c,subnet-d CLUSTER_NAME=my-cluster'

make deploy-network-dev              # OPTIONAL greenfield VPC + NAT + cluster (skip if they exist)
make deploy-platform-dev             # ECR + task-execution role + log group
make deploy-nets-dev     $IDS        # per-app ALB + target group + security groups
make deploy-apps-dev     $IDS        # the 3 app services (target group + SG resolved from the net stacks)
```
Redeploy the services after pushing new images: `make deploy-dev $IDS`.

### 5. Verify
```sh
aws cloudformation describe-stacks --stack-name i2b2-app \
  --query "Stacks[0].Outputs" --profile <AWS_PROFILE_DEV> --region us-east-2
# open the ALB URL; container logs are in the /ecs/i2b2-<env> log group
```

---

## Adding a NEW environment `<env>`

1. **Create the config folders** for the new env, copying an existing one as a starting point:
   ```sh
   cp -r docker/i2b2-server/configuration/dev            docker/i2b2-server/configuration/<env>
   cp -r docker/i2b2-web/Configuration/dev               docker/i2b2-web/Configuration/<env>
   cp -r docker/shrine-server/src/configs/dev            docker/shrine-server/src/configs/<env>
   cp -r docker/shrine-server/src/keystore/dev           docker/shrine-server/src/keystore/<env>
   ```
   Edit the copies for the new env (hostnames, DB URLs + credentials, `entityID`/`ServerName`,
   `nodeKey`, keystore, …).
2. **Build** — add build targets (or run `docker build --build-arg ENVIRONMENT=<env> …`) so the
   new env's config is baked into its image tag.
3. **Deploy** the stacks with `Environment=<env>`, passing the network edge as parameters,
   e.g.:
   ```sh
   aws cloudformation deploy --template-file deployments/app/app-i2b2.yaml --stack-name i2b2-app-<env> \
     --capabilities CAPABILITY_NAMED_IAM --parameter-overrides Environment=<env> \
     ClusterName=… PrivateSubnetIds=… PlatformStackName=i2b2-platform-<env> \
     ServiceSecurityGroupId=sg-… TargetGroupArn=arn:…:targetgroup/… AlbDnsName=… \
     --profile <profile> --region us-east-2
   ```
   (or add `deploy-*-<env>` targets mirroring the dev ones, which resolve those from the net stack).

> To change config/creds later: edit the files in the component folders → `make images-<env>`
> → `make deploy-<env>`.

---

## Layout
```
docker/
  i2b2-server|i2b2-web|shrine-server/   Dockerfile + per-variant config
                                        (configuration|Configuration|src/{configs,keystore}/**)
                                        + static assets (WAR, webclients, ontology data, drivers)
  docker-compose.yml                    local stack (baked with the `local` build args)
deployments/
  infrastructure/  cloudformation.yaml (network) + {i2b2,shrine-mu,shrine-washu}.yaml (ALB/TG/SG)
  platform.yaml    ECR + task-execution role + log group
  app/             app-i2b2.yaml + app-shrine.yaml (Project=mu|washu)
Makefile           aws/docker login, images-* (per variant), deploy-*
```

