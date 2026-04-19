FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /app

COPY . .
RUN ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:24-jre
WORKDIR /app

COPY --from=build /app/target/zynee-0.0.1-SNAPSHOT.jar /app/app.jar

ENV SERVER_ADDRESS=0.0.0.0
ENV SERVER_PORT=10000

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.address=${SERVER_ADDRESS} -Dserver.port=${PORT:-${SERVER_PORT}} -jar /app/app.jar"]
