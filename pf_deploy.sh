#!/bin/bash
# Run this bash with pf_common.sh in the same directory somewhere at destination VM.
# Test with:
#   sshpass -p 'admin123'  ssh -o "StrictHostKeyChecking no" -o "IdentitiesOnly=yes" admin@167.188.21.37
#   images is fetche from ${IM_SERVER_USER}@${IM_SERVER}. TODO: fetch them from registeryserver.
set -x

function deploy() {
    local I=$(readlink -f $0)
    local MY_PATH=$(dirname $I)
    source "${MY_PATH}"/pf_common.sh

    local PF_HOME=/var/pf_home

    # persistant tmp path used by docker compose
    local TMP=/var/tmp/docker_compose_pf
    rm -rf ${TMP}/*
    mkdir -p ${TMP}
    export TMPDIR=${TMP}

    # old process: kill
    docker-compose -f ${PF_HOME}/${YAML} -f ${PF_HOME}/${YAML_VM} down
    local IDs=$(ps | grep -E "${YAML_VM}" | grep -v "grep" | awk '{print $1}')
    if [ ! -z "$IDs" ]; then
        echo "$IDs" | xargs kill -9
    fi

    local parameter="$(echo -e "$2" | tr -d '[:space:]')"
    if [[ "$parameter" == "-s" || "$parameter" == "--stop" ]]; then
        echo "down."
        exit
    fi

    if [[ "$parameter" == "-n" || "$parameter" == "--no-fetch" ]]; then
        echo "Fetch images ..."
        # container : cleanps
        containers=$(docker ps -a | grep -E "$PREFIX" | awk '{print $1}')
        for c in ${containers}; do
            docker stop $c
            docker rm $c
        done

        # images: clean
        docker images | grep -E "$A|$B|$C|$D|$E" | awk '{print $3}' | xargs docker rmi

        # network and volume

        # clean local
        rm -rf "${PF_HOME}"
        mkdir -p "$PF_HOME"

        # scp
        # create knowhost
        sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER} ls &
        wait &&
            sshpass -p ${IM_SERVER_PASS} scp ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/* "${PF_HOME}"/ &
        wait
        md5sum "${PF_HOME}"/saved*.tar "${PF_HOME}"/"${FILES_TAR}" &&
            tar xvzf "$PF_HOME/$FILES_TAR" -C "$PF_HOME"/ &&
            local IM_ID &&
            for ((i = 0; i < ${#SAVED[@]}; i++)); do
                docker load -i "${PF_HOME}"/${SAVED[i]}
                IM_ID=$(docker images | grep none | awk '{ print $3 }')
                if [ ! -z $IM_ID ]; then
                    docker tag $IM_ID ${IM[i]}:${TAG[i]}
                fi
            done &
        wait
    fi
    # starts up
    rm nohup.out
    nohup docker-compose -f ${PF_HOME}/${YAML} -f ${PF_HOME}/${YAML_VM} up >/dev/null 2>&1 &
}

function usage() {
    echo -e "$0 [[-n | --no-fetch] | [-h | --help] | [-s | --stop]]
    default: fetch Platform docker images latest version and start Platform
        -h --help: usage.
        -n --no-fetch: not fetch images, still use exiting one.
        -s --stop: stop running apps of cloud .
    "
}

# main

if [[ $# -eq 0 ]]; then
    deploy $@
else
    while [ "$1" != "" ]; do
        case $1 in
        -n | --no-fetch | -s | --stop)
            deploy "$0" "$1"
            exit
            ;;
        -h | --help)
            usage
            exit
            ;;
        *)
            usage
            exit
            ;;
        esac
    done
fi
