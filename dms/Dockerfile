# Multi-stage build: Spring Boot fat jar on a slim JRE runtime. The PDF/A
# conversion toolchain and the AI extraction live in the two Python services
# (services/conversion, services/extraction) — this image needs neither.
# Runs as one container in a HF Docker Space (port 7860) or anywhere else.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/dms.jar ./dms.jar
# run as non-root with uid 1000: HF Spaces mounts persistent storage at
# /data writable by uid 1000 only, so the app user must have that uid.
# The Ubuntu base image already owns uid 1000 ('ubuntu') — remove it first.
RUN existing="$(getent passwd 1000 | cut -d: -f1)" \
 && if [ -n "$existing" ]; then userdel "$existing"; fi \
 && useradd --create-home --uid 1000 dms \
 && mkdir -p /data && chown -R dms:dms /data /app
USER dms
EXPOSE 7860
ENV SERVER_PORT=7860
ENV DMS_DATA_DIR=/data
# bound the heap to the container's memory, not the host's (uploads,
# conversions and backups are streamed, but the JVM default of 25% is too
# small for the remaining in-memory work on small containers)
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "dms.jar"]
