#!/bin/bash
MIGRATION_ROOT=/dbm
LOG=${MIGRATION_ROOT}/dbm_check.log

function wait_mysql_up() {
    local mysql_service=mysql
    local mysql_port=3306
    local check_command="mysqladmin status -h${mysql_service} -P${mysql_port} -uroot -proot"
    eval "$check_command"
    while [ $? -ne 0 ]; do
        echo "MariaDB is not up, wait 3s and try again"
        sleep 3s
        eval "$check_command"
    done
}

mkdir -p /usr/local/tomcat/util/dbm/
touch ${LOG}
# dbm only know the VM environment, mock it here
mock_path="/usr/local/tomcat/util${LOG}"
if [ ! -L "${mock_path}" ]; then
    ln -s "${LOG}" "${mock_path}"
fi

# waiting till mysql is up
wait_mysql_up
# start
cd ${MIGRATION_ROOT} &&
    java -jar ./proj-dbm-jar-with-dependencies.jar \
        ./dbm_check.log \
        ./forDocker.config.properties &&
    cd $CATALINA_HOME && catalina.sh run
