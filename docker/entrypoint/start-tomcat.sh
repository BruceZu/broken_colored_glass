#!/bin/bash
source /entrypoint/common.sh
MIGRATION_ROOT=/project/dbm

mvn clean -f /project/pom.xml &&
  /entrypoint/packing.sh &&
  wait_mysql_up &&
  cd ${MIGRATION_ROOT} && java -jar ./target/proj-dbm-jar-with-dependencies.jar \
  /usr/local/tomcat/util/dbm/dbm_check.log \
  ./src/main/resources/forDocker.config.properties &&
  rm -rf $CATALINA_HOME/webapps/proj &&
  ln -sf /project/target/proj $CATALINA_HOME/webapps/proj &&
  cd $CATALINA_HOME && catalina.sh run
