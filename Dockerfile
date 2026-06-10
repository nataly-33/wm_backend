# Etapa de construcción
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Descargar dependencias para que se cacheen
RUN mvn dependency:go-offline
# Copiar código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa de ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/workflow-back-0.0.1-SNAPSHOT.jar app.jar
# (Opcional) Copiar carpeta secrets si existe (para firebase, aunque mejor usar variable env o montar volumen)
# COPY secrets ./secrets
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx512m", \
  "-Xms256m", \
  "-jar", "app.jar"]
