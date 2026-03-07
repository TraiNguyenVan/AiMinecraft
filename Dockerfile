# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package

# Output Stage
FROM alpine:latest
WORKDIR /output
COPY --from=build /app/target/AiMinecraft-1.0.jar .
CMD ["ls", "-la"]
