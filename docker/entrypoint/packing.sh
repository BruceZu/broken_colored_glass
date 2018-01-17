#!/bin/bash
echo "=============== packaging ...  ==============="
mvn compile -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true war:exploded -ff -f /project/pom.xml &&
  cp /tomcat/webapps/fpc/WEB-INF/classes/database.properties /project/target/fpc/WEB-INF/classes/ &&
  cp /tomcat/webapps/fpc/WEB-INF/classes/database.properties /project/target/fpc/WEB-INF/classes/spring/ &&
  echo "=============== package is done ==============="
