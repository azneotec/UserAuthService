# Loads DB_* / JWT_SECRET etc. from a gitignored .env into the environment of every command below,
# if one exists. Without it the defaults in application.properties apply (MySQL on localhost:3306,
# root/password, dev JWT key). Spring resolves the ${...} placeholders at runtime, so the jar itself
# never contains the values.
#
# Recognised variables: DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD, JWT_SECRET, SERVER_PORT.

JAR        := target/UserAuthService-0.0.1-SNAPSHOT.jar
SKIP_TESTS ?= true

# Export .env for one recipe line when present. Sourced by the shell (not `include`) so quoted values work.
WITH_ENV = set -a; [ ! -f ./.env ] || . ./.env; set +a;

.PHONY: jar run test clean

## Build the jar with .env loaded (tests skipped by default; use SKIP_TESTS=false to run them)
jar:
	@$(WITH_ENV) ./mvnw clean package -DskipTests=$(SKIP_TESTS)
	@echo "Built $(JAR)"

## Run the built jar with .env loaded (SERVER_PORT=9001 make run starts a second instance)
run:
	@test -f $(JAR) || { echo "$(JAR) not found - run 'make jar' first"; exit 1; }
	@$(WITH_ENV) java -jar $(JAR)

## Run the tests (unit + web-slice; no database needed)
test:
	@./mvnw test

clean:
	@./mvnw clean
