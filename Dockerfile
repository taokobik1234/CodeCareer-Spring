# Stage 1: Build ứng dụng bằng Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Thiết lập thư mục làm việc
WORKDIR /app

# Copy file pom.xml trước để tận dụng Docker layer caching
COPY pom.xml .

# Copy mã nguồn
COPY src ./src

# Build ứng dụng, bỏ qua unit tests
RUN mvn clean package -DskipTests

# Stage 2: Chạy ứng dụng
FROM openjdk:21-jdk-slim

# Thiết lập thư mục làm việc
WORKDIR /app

# Copy file JAR từ stage build
COPY --from=build /app/target/CodeCareer-0.0.1-SNAPSHOT.jar /app/CodeCareer.jar

# Expose port mà ứng dụng Spring Boot sẽ chạy
EXPOSE 8080

# Lệnh để chạy ứng dụng Spring Boot
ENTRYPOINT ["java", "-jar", "/app/CodeCareer.jar"]