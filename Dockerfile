FROM eclipse-temurin:21-jre

EXPOSE 8080

# Quarkus fast-jar layout: quarkus-run.jar delegates to lib/ and app/
COPY build/quarkus-app/lib/ /app/lib/
COPY build/quarkus-app/*.jar /app/
COPY build/quarkus-app/app/ /app/app/
COPY build/quarkus-app/quarkus/ /app/quarkus/

CMD java -jar /app/quarkus-run.jar
