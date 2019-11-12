ARG TOMCAT_CONFIG_PATH=./docker/tomcat/conf
ARG DBMIG_CONFIG_PATH=./project/dbm/src/main/resources/db_migration2
ARG DEV_MODE=0
FROM openjdk:8u181-jdk-stretch as package
ARG DEV_MODE

COPY ./project /project
COPY ./docker/maven/toolchains.xml /root/.m2/toolchains.xml
COPY ./docker/release/package.sh ./package.sh
COPY ./docker/release/change_src_for_managerapp_vm.py ./change_src_for_managerapp_vm.py
RUN ./package.sh $DEV_MODE

FROM tomcat:9.0.12-jre8 as release
ARG TOMCAT_CONFIG_PATH
ARG DBMIG_CONFIG_PATH

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get install -qq --no-install-recommends \
    apt-utils \
    procps \
    mysql-client \
    gawk
COPY ${TOMCAT_CONFIG_PATH}/server.xml $CATALINA_HOME/conf/server.xml
COPY ${TOMCAT_CONFIG_PATH}/tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
COPY ${TOMCAT_CONFIG_PATH}/tomcat.keystore $CATALINA_HOME/conf/tomcat.keystore

COPY --from=package /project/target/proj $CATALINA_HOME/webapps/proj
COPY ./docker/release/tomcat/webapps/proj/WEB-INF/classes/database.properties $CATALINA_HOME/webapps/proj/WEB-INF/classes/database.properties

COPY ${DBMIG_CONFIG_PATH}/global_init /dbm/global_init
COPY ${DBMIG_CONFIG_PATH}/global /dbm/global
COPY ${DBMIG_CONFIG_PATH}/customer /dbm/customer
COPY --from=package /project/dbm/target/proj-dbm-jar-with-dependencies.jar /dbm/proj-dbm-jar-with-dependencies.jar
COPY ./docker/tomcat/util/proj/label.conf /var/tomcat/util/proj/label.conf
COPY ./docker/release/dbm/forDocker.config.properties /dbm/forDocker.config.properties

COPY docker/release/entrypoint/start-tomcat.sh /entrypoint/start-tomcat.sh
CMD ["/entrypoint/start-tomcat.sh"]
