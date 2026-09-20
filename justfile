set shell := ["bash", "-eu", "-c"]

red := '\033[31m'
nc := '\033[0m'

# The GitHub <org>:<team-slug> whose members are the app's MapAdmins (write access). One source of
# truth for the auth overlay: exported so both `docker compose` (oauth2-proxy gate) and `quarkusDev`
# (app.auth.map-admin-group) inherit it. Override from the shell to point at a different team.
export GITHUB_ADMIN_TEAM := env_var_or_default("GITHUB_ADMIN_TEAM", "triplea-maps:mapadmins")

# Show this help text
help:
    @just --list

# Run formatting and all checks
all: format check

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

# Run all checks used to verify a Pull-Request
check:
    ./gradlew spotlessApply check

# Remove build artifacts and stop docker containers and remove docker volumes
clean:
    ./gradlew clean

# Run with fake auth — no proxy, no GitHub, zero setup (newcomer default). DEV_FAKE_AUTH=anon to test anonymous.
dev:
    DEV_FAKE_AUTH=${DEV_FAKE_AUTH:-mapadmin} ./gradlew quarkusDev

alias up := run

# Run behind the real oauth2-proxy/nginx auth overlay (browse http://localhost:8000). Needs .env.auth — see docs/auth.md.
run:
    docker compose -f docker-compose.auth.yml up -d
    ./gradlew quarkusDev

# Stop the oauth2-proxy/nginx auth overlay started by `just run`
run-stop:
    docker compose -f docker-compose.auth.yml down

# Verify nginx strips spoofed inbound X-Auth-* headers (security check). Brings the proxy up; needs the app running (just run).
verify-auth-headers:
    docker compose -f docker-compose.auth.yml up -d
    ./auth/verify-header-sanitization.sh

# Connect to the Quarkus Dev Services Postgres started by `just run`/`just dev`
psql:
    docker exec -it "$(docker ps -q --filter label=io.quarkus.devservice.launch-mode=DEVELOPMENT)" psql -U quarkus quarkus

# Use 'triplea' game-client dependency as built from local disc, useful if working on shared libraries between 'support-server' and 'triplea'
local:
    ./gradlew --info --include-build ../triplea compileJava

# Build the Quarkus application artifact
build:
    ./gradlew quarkusBuild

# Create 'docker container' build artifacts
docker-build: build
    docker build . --tag ghcr.io/triplea-game/support-server/server:latest

# Push 'docker container' build artifacts to github docker container registry
docker-push: docker-build
    docker push ghcr.io/triplea-game/support-server/server:latest

# Trigger prod to pull latest docker and restart services
deploy:
    ANSIBLE_CONFIG="deploy/ansible.cfg" \
      ansible-playbook \
        -e ansible_user=${SSH_USER:-$USER} \
        --inventory deploy/ansible/inventory.linode.yml \
        deploy/ansible/playbook.yml
