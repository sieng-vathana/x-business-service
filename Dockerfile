FROM eclipse-temurin:17-jre

WORKDIR /app
RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring spring

ARG JAR_FILE=target/*-SNAPSHOT.jar
COPY --chown=spring:spring ${JAR_FILE} application.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
USER spring:spring
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
