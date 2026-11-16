# Imagen de producción de MentIA-api (Railway u otro host con Docker).
# Los tests no corren acá: necesitan Docker (Testcontainers) y se corren con ./mvnw verify antes de mergear.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Primero solo el pom: las dependencias quedan en caché mientras no cambie.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/mentia-api-*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 mentia
COPY --from=build /app/app.jar app.jar
USER mentia
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Railway define PORT; application.yml lo usa (8080 si no está).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
