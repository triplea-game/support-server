FROM eclipse-temurin:21-jdk-focal

EXPOSE 8080
ADD configuration.yml /
ADD build/libs/support-server.jar /
CMD java -jar support-server.jar server /configuration.yml
