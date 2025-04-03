# Sử dụng base image của OpenJDK
FROM openjdk:21-jdk-slim

# Đặt thư mục làm việc trong container
WORKDIR /app

# Copy file JAR từ thư mục target vào container
COPY target/CodeCareer-0.0.1-SNAPSHOT.jar /app/CodeCareer.jar

# Expose port mà ứng dụng Spring Boot sẽ chạy
EXPOSE 8080

# Lệnh để chạy ứng dụng Spring Boot
ENTRYPOINT ["java", "-jar", "/app/CodeCareer.jar"]
