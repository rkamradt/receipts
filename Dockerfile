# Stage 1: build the React frontend
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY receipts-frontend/package*.json ./
RUN npm ci
COPY receipts-frontend/ .
RUN npm run build

# Stage 2: build the Spring Boot backend (with frontend bundled in)
FROM maven:3.9-eclipse-temurin-21-alpine AS backend
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
# Bundle the React build output as Spring Boot static resources
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -B -q

# Stage 3: minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
# JVM flags tuned for a single-node K3s cluster (low-overhead GC)
CMD ["java", "-XX:+UseSerialGC", "-Xmx384m", "-jar", "app.jar"]
