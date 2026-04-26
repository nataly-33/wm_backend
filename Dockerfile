FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/workflow-back-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx400m", \
  "-Xms200m", \
  "-jar", "app.jar"]
