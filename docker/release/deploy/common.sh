#!/bin/bash
function check_software() {
    name=$1
    which $1
    if [[ $? == 1 ]]; then
        echo "ERRO: need install $1"
        exit 1
    fi
}

function manage_docker_as_a_non-root_user() {
    if [[ ! $(getent group docker) ]]; then
        sudo groupadd docker
    fi
    sudo usermod -aG docker $USER
    newgrp docker
}

function prepare_using_test_registery_server() {
    local crt_file=domain.crt
    local crt_path=registry-server-certs
    local regis_server_ip="188.199.34.21"
    local with_sudo=" "
    if [[ "$(echo $(whoami) | tr -d '[:space:]')" != "root" ]]; then
        with_sudo="sudo "
    fi

    # host for registery test server
    local c=0
    c=$(sed -n "/${REGISTRY_SERVER_TEST_HOST}/p" /etc/hosts | wc -l)
    [[ $c -eq 0 ]] && echo "${regis_server_ip} ${REGISTRY_SERVER_TEST_HOST}" | $with_sudo tee -a /etc/hosts
    # prepare the certificate for registry server.
    local registry_test_server_cert_dir="/etc/docker/certs.d/${REGISTRY_SERVER_TEST}"
    [ ! -d "$registry_test_server_cert_dir" ] && $with_sudo mkdir -p "${registry_test_server_cert_dir}"
    # Current the registery test server is same as hop server 'IM_SERVER'
    [ ! -f "$registry_test_server_cert_dir/domain.crt" ] &&
        sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER} ls -lh ${HOME_ON_HOP} &
    wait "$!" &&
        sshpass -p ${IM_SERVER_PASS} scp ${IM_SERVER_USER}@${IM_SERVER}:${crt_path}/${crt_file} /tmp/ &&
        $with_sudo mv /tmp/${crt_file} "${registry_test_server_cert_dir}/"
    echo "login ${REGISTRY_SERVER_TEST}"
    docker login ${REGISTRY_SERVER_TEST}
}

function check_hop_access() {
    local hop_info_file="/tmp/registry.txt"
    # only for test
    if [[ -f "$hop_info_file" ]]; then
        source $hop_info_file

    else
        echo "provide hop machin info like:
    cat >"${hop_info_file} "<<END
IM_SERVER=188.199.34.21
IM_SERVER_USER=v
IM_SERVER_PASS='thispassword'
END"
        exit 1
    fi
}

## Main
set -eux
# files used for test in BPJ VM
YAML=docker-compose-managerapp-vm-deploy-prod.yml
YAML_TEST=docker-compose-managerapp-vm-deploy-test.yml
VESION_FILE=version.txt
LOG_CONFIG_PATH=project/src/main/resources
LOG_CONFIG_F=log4j2.xml

# image name component: group
IMAGE_GROUP="compportal"

# hop machine info
HOME_ON_HOP=/tmp/proj_home

# registry test server info
REGISTRY_SERVER_TEST_HOST="registry-test"
REGISTRY_SERVER_TEST="${REGISTRY_SERVER_TEST_HOST}:443"

