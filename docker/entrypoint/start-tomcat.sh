#!/bin/bash
cd /project &&
  mvn clean &&
  /entrypoint/packing.sh &&
  rm -rf $CATALINA_HOME/webapps/* &&
  ln -sf /project/target/fpc $CATALINA_HOME/webapps/fpc &&
  cd $CATALINA_HOME &&
  catalina.sh run
