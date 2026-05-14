# ---- Stage 1: Build Frontend ----
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend

# Copy package files first (for layer caching)
COPY frontend/package.json frontend/package-lock.json ./

# Install dependencies
RUN npm ci

# Copy frontend source and build
COPY frontend/ .
RUN npm run build

# ---- Stage 2: Build Backend ----
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app

# Copy Maven wrapper and pom.xml first (for layer caching)
COPY backend/mvnw .
COPY backend/.mvn .mvn
COPY backend/pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy backend source
COPY backend/src src

# Copy frontend build output into Spring Boot static resources
COPY --from=frontend-build /app/frontend/dist src/main/resources/static

# Build the JAR
RUN ./mvnw package -Dmaven.test.skip=true -B

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=backend-build /app/target/*.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
