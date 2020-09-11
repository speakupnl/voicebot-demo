FROM openjdk:14-jdk-alpine

VOLUME /tmp

ENV JAVA_OPTS=""

COPY app/*.jar /app.jar

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app.jar"]