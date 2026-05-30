FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin earnx

COPY --from=build /workspace/target/EarnX-3-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p /app/uploads && chown -R earnx:earnx /app

USER earnx

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
