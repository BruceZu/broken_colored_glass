#!/bin/bash
PROJ_IM_REP="proj_release"
DB_IM_REP="proj_mysql"

PROJ_IM_TAG="latest"
DB_IM_TAG="latest"

SAVED_PROJ="saved_proj.tar"
SAVED_DB="saved_mysql.tar"
YAML=docker-compose-managerapp_vm_build.yml
LOG_CONFIG_PATH=project/src/main/resources
LOG_CONFIG_F=log4j2.xml
VESION_FILE=version.txt

HOME_ON_HOP=/tmp/proj

VOLUME_PROJ_MYSQL_KEY=proj_mysql_data_3307

registry_file="/tmp/registry.txt"
# Registry server info which is not keped in Git
if [[ -f "$registry_file" ]]; then
    source $registry_file
else
    echo -e "please provide docker image registry server info in the /tmp/registry.txt. Example:\n
 IM_SERVER=188.199.34.21\n
 IM_SERVER_USER=tom\n
    IM_SERVER_PASS='1234qqq'"
    exit 1
fi


check_software(){
    name=$1
    which $1
    if [[ $? == 1 ]]; then
        echo "ERRO: need install $1"
        exit 1
    fi
}

manage_docker_as_a_non-root_user(){
    if [[ ! $(getent group docker) ]] ; then
        sudo groupadd docker
    fi
    sudo usermod -aG docker $USER
    newgrp docker
}