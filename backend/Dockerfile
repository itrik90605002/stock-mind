# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/warehouse-backend-*.jar app.jar
USER app
EXPOSE 8080
# Tuned for small free-tier instances (~512 MB RAM)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
