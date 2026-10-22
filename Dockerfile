# Builds and runs only UserAuthService. MySQL, Kafka and Eureka are not included: point the container at them
# with environment variables (see README, "Run in Docker").
#
#   docker build -t user-auth-service .
#   docker run --rm -p 8001:8001 -e DB_HOST=host.docker.internal -e DB_PORT=3307 \
#       -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 -e EUREKA_CLIENT_ENABLED=false user-auth-service

# --- build stage: compile the jar; dependencies are cached in their own layer, so a source-only change is fast
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

# --- runtime stage: JRE only, non-root
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/target/UserAuthService-0.0.1-SNAPSHOT.jar app.jar
USER app

# Everything is configured through environment variables; localhost inside a container is the container itself,
# so DB_HOST (and the Kafka and Eureka addresses) must be overridden.
#   DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD   MySQL (defaults: localhost 3306 user_auth_service root password)
#   JWT_SECRET JWT_EXPIRATION                         set JWT_SECRET anywhere shared: the committed dev key is public
#   SPRING_KAFKA_BOOTSTRAP_SERVERS                    default localhost:9092
#   EUREKA_CLIENT_SERVICEURL_DEFAULTZONE              default http://localhost:8761/eureka/
#   EUREKA_CLIENT_ENABLED=false                       run without a registry
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8001

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:${SERVER_PORT:-8001}/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
