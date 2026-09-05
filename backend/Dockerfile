# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx1024m -Dfile.encoding=UTF-8"
COPY --from=build /build/target/rag-agent-backend.jar app.jar
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
