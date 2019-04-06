#!/bin/bash
source /entrypoint/common.sh
debug_log "Packaging ..."
mvn compile -Dmaven.test.skip=true -Dcheckstyle.skip=true -ff -f /project/dbm/pom.xml &&
    mvn compile -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true war:exploded -ff -f /project/pom.xml &&
    cp /tomcat/webapps/proj/WEB-INF/classes/database.properties /project/target/proj/WEB-INF/classes/ &&
    debug_log "Package is done."
