# 网关镜像:JDK17 JRE + 可执行 jar;JAVA_OPTS 运行时可由 compose 覆盖
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xmx384m"
COPY target/ticket-gateway-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
