set shell := ["bash", "-eu", "-c"]

red := '\033[31m'
nc := '\033[0m'

# The GitHub <org>:<team-slug> whose members are the app's MapAdmins (write access). One source of
# truth for the auth overlay: exported so both `docker compose` (oauth2-proxy gate) and `quarkusDev`
# (app.auth.map-admin-group) inherit it. Override from the shell to point at a different team.
export GITHUB_ADMIN_TEAM := env_var_or_default("GITHUB_ADMIN_TEAM", "triplea-maps:mapadmins")

# Matches %dev.quarkus.datasource.devservices.db-name in application.properties.
dev_db := "support_db"

# Show available recipes
default:
    @just --list

# Install pre-commit as a pre-push git hook (requires pre-commit to be installed)
setup:
    #!/usr/bin/env bash
    set -euo pipefail
    gradle_properties="$HOME/.gradle/gradle.properties"
    uv tool install pre-commit
    pre-commit install --hook-type pre-push
    test -f "$gradle_properties" || touch "$gradle_properties"
    # Check that required gradle properties are set
    grep -q "triplea_github_username" "$gradle_properties"
    grep -q "triplea_github_access_token" "$gradle_properties"
    if ! grep -qs '^testcontainers.reuse.enable=true' "$HOME/.testcontainers.properties"; then
      echo 'testcontainers.reuse.enable=true' >> "$HOME/.testcontainers.properties"
      echo "Enabled testcontainers reuse in ~/.testcontainers.properties"
    fi

# Print versions of system dependencies (eg: java, docker)
print-versions:
    @echo -e "\n{{red}}### Versions used by Gradle ###{{nc}}"
    @./gradlew --version
    @echo -e "\n{{red}}### Docker Compose Version ###{{nc}}"
    @docker compose version

# Run formatting
format:
    ./gradlew spotlessApply

alias test := check
alias run := up
alias stop := down

# Run all checks used to verify a Pull-Request; fails on unformatted code (fix with `just format`)
check:
    ./gradlew check

# Wipe local state: the Dev Services database (data included) and build artifacts
clean:
    #!/usr/bin/env bash
    set -euo pipefail
    for id in $(just _dev-db); do docker rm -f -v "$id"; done
    ./gradlew clean

# Run with fake auth — no proxy, no GitHub, zero setup (newcomer default). DEV_FAKE_AUTH=anon to test anonymous.
up:
    DEV_FAKE_AUTH=${DEV_FAKE_AUTH:-mapadmin} ./gradlew quarkusDev

# Run behind the real oauth2-proxy/nginx auth overlay (browse http://localhost:8000). Needs .env.auth — see docs/auth.md.
up-auth:
    docker compose -f docker-compose.auth.yml up -d
    ./gradlew quarkusDev

# Stop the oauth2-proxy/nginx auth overlay started by `just up-auth`; the database keeps running
down:
    docker compose -f docker-compose.auth.yml down

# Verify nginx strips spoofed inbound X-Auth-* headers (security check). Brings the proxy up; needs the app running (just up-auth).
verify-auth-headers:
    docker compose -f docker-compose.auth.yml up -d
    ./auth/verify-header-sanitization.sh

# Connect to the Quarkus Dev Services Postgres started by `just up`/`just up-auth`
psql:
    docker exec -it "$(just _dev-db | head -n 1)" psql -U quarkus {{dev_db}}

# Use 'triplea' game-client dependency as built from local disc, useful if working on shared libraries between 'support-server' and 'triplea'
build-with-libs:
    ./gradlew --info --include-build ../triplea compileJava

# Build the Quarkus application artifact
build:
    ./gradlew quarkusBuild

image := "ghcr.io/triplea-game/support-server/server"

# Build the docker image as ':latest', plus ':<tag>' when given (CI passes sha-<commit>)
docker-build tag="": build
    docker build . --tag {{image}}:latest
    if [ -n "{{tag}}" ]; then docker tag {{image}}:latest {{image}}:{{tag}}; fi

# Push the docker image to the github container registry as ':latest', plus ':<tag>' when given
docker-push tag="": (docker-build tag)
    docker push {{image}}:latest
    if [ -n "{{tag}}" ]; then docker push {{image}}:{{tag}}; fi

# Deploy an image tag to prod (CI passes sha-<commit>)
deploy tag="latest":
    ANSIBLE_CONFIG="deploy/ansible.cfg" \
      ansible-playbook \
        -e ansible_user=${SSH_USER:-$USER} \
        -e support_tag={{tag}} \
        --inventory deploy/ansible/inventory.linode.yml \
        deploy/ansible/playbook.yml

# Print the ids of this repo's Dev Services Postgres containers, running or stopped.
_dev-db:
    #!/usr/bin/env bash
    set -euo pipefail
    for id in $(docker ps -aq --filter label=io.quarkus.devservice.launch-mode=DEVELOPMENT); do
      if docker inspect "$id" | grep -q '"POSTGRES_DB={{dev_db}}"'; then echo "$id"; fi
    done
