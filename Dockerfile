FROM eclipse-temurin:25-jre

EXPOSE 8080

# Quarkus fast-jar layout: quarkus-run.jar delegates to lib/ and app/
COPY build/quarkus-app/lib/ /app/lib/
COPY build/quarkus-app/*.jar /app/
COPY build/quarkus-app/app/ /app/app/
COPY build/quarkus-app/quarkus/ /app/quarkus/

# Exec form makes java PID 1, so 'docker stop' SIGTERM reaches Quarkus for a graceful shutdown.
CMD ["java", "-jar", "/app/quarkus-run.jar"]
