#!/bin/bash

function wait_mysql_up() {
    local mysql_service=compportaldb
    local mysql_port=3306
    local check_command="mysqladmin status -h${mysql_service} -P${mysql_port} -uroot -proot"
    eval "$check_command"
    while [ $? -ne 0 ]; do
        echo "MariaDB is not up, wait 3s and try again"
        sleep 3s
        eval "$check_command"
    done
}

function persistent_log_files() {
    proj_mount_point="/data"
    mig_check_log_file="dbm_check.log"
    mig_log_file="coustomer_dbm.log"
    proj_log_file="coustomer_proj.log"
    log_files=($mig_check_log_file $mig_log_file $proj_log_file)

    # db migration logs hard code with PROJ VM environment, mock it here
    mig_check_log_root="${CATALINA_HOME}/util/dbm"
    mkdir -p "$mig_check_log_root"
    mig_log_root="/var/tomcat/util"
    proj_log_root="${CATALINA_HOME}/util"
    log_roots=($mig_check_log_root $mig_log_root $proj_log_root)

    for ((i = 0; i < ${#log_files[@]}; i++)); do
        touch ${proj_mount_point}/${log_files[$i]}

        if [ ! -L "${log_roots[$i]}/${log_files[$i]}" ]; then
            ln -s "${proj_mount_point}/${log_files[$i]}" "${log_roots[$i]}/${log_files[$i]}"
        fi
    done
}

persistent_log_files
# waiting till mysql is up
wait_mysql_up
# start
MIGRATION_ROOT=/dbm
cd ${MIGRATION_ROOT} &&
    java -jar ./proj-dbm-jar-with-dependencies.jar \
        ./dbm_check.log \
        ./forDocker.config.properties &&
    cd $CATALINA_HOME && catalina.sh run
