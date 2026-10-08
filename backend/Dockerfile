FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY core/build.gradle.kts core/
COPY site/build.gradle.kts site/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :core:dependencies :site:dependencies > /dev/null
COPY core/src core/src
COPY site/src site/src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :site:bootJar \
    && cp "$(find site/build/libs -name '*.jar' ! -name '*-plain.jar')" /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --home /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
