# ---- Build Stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
RUN ./mvnw package -Dmaven.test.skip=true -B

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
