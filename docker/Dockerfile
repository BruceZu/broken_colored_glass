FROM tomcat:9.0.12-jre8
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    apt-utils \
    maven \
    procps \
    gawk \
    python \
    inotify-tools \
    inotify-hookable  \
    openjdk-8-jdk && javac -version

COPY ./docker/maven/toolchains.xml /root/.m2/
COPY ./docker/tomcat/conf/server.xml $CATALINA_HOME/conf/server.xml
COPY ./docker/tomcat/conf/tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
COPY ./docker/tomcat/conf/tomcat.keystore $CATALINA_HOME/conf/tomcat.keystore
COPY ./docker/entrypoint/entry.sh /entrypoint/entry.sh
COPY ./docker/tomcat/webapps /tomcat/webapps
COPY ./docker/entrypoint/packing.sh /entrypoint/packing.sh
CMD ["/entrypoint/entry.sh"]
