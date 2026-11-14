# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN mkdir -p /root/.m2 && printf '%s\n' \
  '<toolchains><toolchain><type>jdk</type><provides><version>21</version></provides>' \
  '<configuration><jdkHome>/opt/java/openjdk</jdkHome></configuration></toolchain></toolchains>' \
  > /root/.m2/toolchains.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2/repository ./mvnw -B -q dependency:go-offline -DskipTests || true
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository ./mvnw -B -q package -DskipTests \
 && mkdir /out && cp target/*.jar /out/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -h /app app \
 && mkdir -p /data/uploads /data/certificates && chown -R app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /out/app.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    UPLOADS_DIR=/data/uploads \
    CERTIFICATES_DIR=/data/certificates \
    APP_ENV=prod
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
