FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY coordinator/pom.xml .
RUN mvn dependency:go-offline -q
COPY coordinator/src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/OrhestraV2-2.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
