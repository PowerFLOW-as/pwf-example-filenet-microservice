FROM docker.powerflow.cloud/base/openjdk:11

EXPOSE 8080

ADD ./target/app-*.jar app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]