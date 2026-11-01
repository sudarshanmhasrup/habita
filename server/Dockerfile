# Runtime image only. CI builds the JAR first with ./gradlew :server:buildFatJar, because the
# Gradle build needs the Android SDK, which does not belong in a server image.
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.source="https://github.com/sudarshanmhasrup/habita"
LABEL org.opencontainers.image.description="Habita server"

RUN useradd --system --uid 10001 --no-create-home habita \
    && mkdir /data \
    && chown habita:habita /data

WORKDIR /app
COPY .build/server/libs/server-all.jar server.jar
COPY program/PROGRAM.md program/PROGRAM.md

ENV PORT=8080 \
    HABITA_DATABASE=/data/habita.db \
    HABITA_PROGRAM=/app/program/PROGRAM.md

USER habita
EXPOSE 8080
VOLUME /data

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "server.jar"]