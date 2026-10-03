FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar \
    && cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar')" /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --home /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
