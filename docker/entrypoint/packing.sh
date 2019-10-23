#!/bin/bash
set -ux
mode=${1:-"dev"}
war_option=${2:-"exploded"}

dbconfig=/tomcat/webapps/proj/WEB-INF/classes/database.properties

source /entrypoint/common.sh
debug_log "Packaging ..."
mvn compile -Dmaven.test.skip=true -Dcheckstyle.skip=true -ff -f /project/dbm/pom.xml
mvn compile -Dmaven.test.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true -Dfrontend.build.mode=${mode} war:${war_option} -ff -f /project/pom.xml
if [[ -f "$dbconfig" ]]; then
  cp $dbconfig /project/target/proj/WEB-INF/classes/
fi
# else it is build in docker for product, need not provide db configure file which will be handled in toochain

debug_log "Package is done."
