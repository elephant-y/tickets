# 用户服务镜像:JDK17 JRE + 可执行 jar;JAVA_OPTS 运行时可由 compose 覆盖
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xmx384m"
COPY target/user-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
