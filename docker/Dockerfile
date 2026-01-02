# Generic runtime container for Spring Boot microservices
FROM eclipse-temurin:25-jre
WORKDIR /app

# Add non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy JAR file from service target directory
COPY communication-service/target/*.jar app.jar

# Change ownership and switch to non-root user
RUN chown spring:spring app.jar
USER spring:spring

# Expose port (can be overridden in compose)
EXPOSE 8080

# Health check (can be customized via environment variables)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

# Run the application with JVM optimizations
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
