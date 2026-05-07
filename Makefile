MAKEFLAGS += --always-make --warn-undefined-variables
SHELL=/bin/bash
.SHELLFLAGS = -eu -c
red=\033[31m
nc=\033[0m
SSH_USER ?= $${USER}

help: ## Show this help text
	grep -h -E '^[a-z]+.*:' $(MAKEFILE_LIST) | \
		awk -F ":|#+" '{printf "\033[31m%s $(nc) \n   %s $(nc)\n    \033[3;37mDepends On: $(nc) [ %s ]\n", $$1, $$3, $$2}'

all: format check

gradleProperties=$$HOME/.gradle/gradle.properties

setup: ## Installs pre-commit as a pre-push git hook (requires pre-commit to be installed)
	set -e
	uv tool install pre-commit
	pre-commit install --hook-type pre-push
	test -f $(gradleProperties) || touch $(gradleProperties)
	# Check that required gradle properties are set
	grep -q "triplea_github_username" $(gradleProperties)
	grep -q "triplea_github_access_token" $(gradleProperties)

print-versions: ## Prints versions of system dependencies (EG: java, docker)
	@echo -e "\n$(red)### Versions used by Gradle ###$(nc)"
	@./gradlew --version
	@echo -e "\n$(red)### Docker Compose Version ###$(nc)"
	@docker compose version

format: ## Runs formatting
	./gradlew spotlessApply

test check: ## Runs all checks used to verify a Pull-Request
	./gradlew spotlessApply check

clean: ## Removes build artifacts and stops docker containers and removes docker volumes
	./gradlew clean

database-up: ## Launches database
	docker compose build flyway
	DATABASE_PORT=5432 docker compose up database flyway

up: ## Build & run server in dev mode (Quarkus Dev Services starts Postgres automatically)
	./gradlew quarkusDev

psql: ## Connects to locally running docker database
	docker exec -u postgres -it support-server-database-1 psql support_db

logs: ## Util command to print the server logs (when running via docker)
	docker logs support-server-server-1

local: ## Uses 'triplea' game-client dependency as built from local disc, useful if working on shared libraries between 'support-server' and 'triplea'
	./gradlew --info --include-build ../triplea compileJava

build:
	./gradlew quarkusBuild

docker-build: build ## Creates 'docker container' build artifacts
	docker build database -f database/flyway.Dockerfile --tag ghcr.io/triplea-game/support-server/flyway:latest
	docker build . --tag ghcr.io/triplea-game/support-server/server:latest

docker-push: docker-build ## Pushes 'docker container' build artifacts to github docker container registry
	docker push ghcr.io/triplea-game/support-server/flyway:latest
	docker push ghcr.io/triplea-game/support-server/server:latest

# TODO: the ansible related stuff needs cleanup
vaultPassword=@echo "${TRIPLEA_ANSIBLE_VAULT_PASSWORD}" > deploy/vault-password; trap 'rm -f "deploy/vault-password"' EXIT
runAnsible=$(vaultPassword); ANSIBLE_CONFIG="deploy/ansible.cfg" ansible-playbook --vault-password-file deploy/vault-password -e ansible_user=$(SSH_USER)
testInventory=--inventory deploy/ansible/inventory/test.inventory
prodInventory=--inventory deploy/ansible/inventory/prod.inventory
playbook=deploy/ansible/deploy-playbook.yml

ansible-galaxy-install:
	ansible-galaxy collection install -r deploy/ansible/requirements.yml --force

diff-test: ansible-galaxy-install
	$(runAnsible) --check --diff $(testInventory) $(playbook)

deploy-test: ansible-galaxy-install
	$(runAnsible) $(testInventory) $(playbook)

diff-prod: ansible-galaxy-install
	$(runAnsible) --check --diff $(prodInventory) $(playbook)

deploy-prod: ansible-galaxy-install
	$(runAnsible) $(prodInventory) $(playbook)
