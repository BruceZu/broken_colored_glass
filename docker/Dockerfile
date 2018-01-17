FROM tomcat:8.0-jre8
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends apt-utils && \
    apt-get install -y maven procps gawk python inotify-tools inotify-hookable && \
    apt-get install -y \
		openjdk-8-jdk="$JAVA_DEBIAN_VERSION" \
		ca-certificates-java="$CA_CERTIFICATES_JAVA_VERSION"
COPY ./docker/maven/toolchains.xml /root/.m2/
COPY ./docker/tomcat/conf/server.xml $CATALINA_HOME/conf/server.xml
COPY ./docker/tomcat/conf/context.xml $CATALINA_HOME/conf/context.xml
COPY ./docker/tomcat/conf/tomcat.keystore $CATALINA_HOME/conf/tomcat.keystore
COPY ./docker/entrypoint/entry.sh /entrypoint/entry.sh
COPY ./docker/tomcat/webapps /tomcat/webapps
COPY ./docker/entrypoint/packing.sh /entrypoint/packing.sh
CMD ["/entrypoint/entry.sh"]
