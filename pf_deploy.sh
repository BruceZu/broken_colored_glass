#!/bin/bash
## start/stop FortinetCloudPlatform in FMG VM.
#
##  Run this bash with pf_common.sh in the same directory somewhere in destination FMG VM.
# E.g. /var/deploy/pf
#
##  Currently it is using the mocked docker images repostiory to fetch docker images:
#   sshpass -p 'admin123'  ssh -o "StrictHostKeyChecking no" -o "IdentitiesOnly=yes" admin@10.106.6.53
#   images is fetched from ${IM_SERVER_USER}@${IM_SERVER}.
#   TODO: fetch them from offical docker iamges repository once it is ready.
#

set -eux

function deploy() {
    local I=$(readlink -f $0)
    local MY_PATH=$(dirname $I)
    source "${MY_PATH}"/pf_common.sh

    local PF_HOME=/var/pf_home

    echo "# set persistant tmp path used by docker compose in FMG VM"
    local TMP=/var/tmp/docker_compose_pf
    rm -rf ${TMP}/*
    mkdir -p ${TMP}
    export TMPDIR=${TMP}

    echo "# prepare the persistent storage sub directory"
    mkdir -p /var/docker/fortifsw/{portal_data,mysql_data,mongodb_data,elasticsearch_data}

    echo "# prepare the max_map_count for elasticsearch"
    echo 262144 >/proc/sys/vm/max_map_count

    echo "# old process: kill"

    if [[ -f ${PF_HOME}/${YAML_VM} ]]; then
        docker-compose -f ${PF_HOME}/${YAML_VM} down
    fi
    local IDs=$(ps | grep -E "${YAML_VM}" | grep -v "grep" | awk '{print $1}')
    if [ ! -z "$IDs" ]; then
        echo "$IDs" | xargs kill -9
    fi

    local parameter="$(echo -e "${1:-}" | tr -d '[:space:]')"
    if [[ "$parameter" == "-s" || "$parameter" == "--stop" ]]; then
        echo "down."
        exit
    fi

    echo "# container: cleaning "
    containers=$(docker ps -a | grep -E "$A|$B|$C|$E" | awk '{print $1}')
    for c in ${containers}; do
        docker stop $c
        docker rm $c
    done

    if [[ "$parameter" != "-no" && "$parameter" != "--no-fetch" ]]; then
        echo "# images: cleaning"
        for i in $(docker images | grep -E "$A|$B|$C|$E" | awk '{print $3}'); do
            if [[ "$i" != "IMAGE" ]]; then
                echo "removing image $i"
                docker rmi $i
            fi
        done

        # network and volume

        echo "# cleaning local tar balls"
        if [[ -d ${PF_HOME} ]]; then
            rm -rf "${PF_HOME}"
        fi
        mkdir -p "$PF_HOME"

        echo "# fetching docker images"
        # create knowhost
        sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER} ls -lh ${HOME_ON_HOP} &
        wait "$!"
        echo "will fetch the listed image .... need wait with patient."
        sshpass -p ${IM_SERVER_PASS} scp ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/* "${PF_HOME}"/ &
        wait "$!"
        echo -e "# verify integratioin:\n## original md5sum"
        cat "${PF_HOME}/${md5sums}"
        echo "## after fetch, local md5sum:"
        md5sum "${PF_HOME}"/saved*.tar "${PF_HOME}"/"${FILES_TAR}"
        echo "# load and tag images"
        tar -xzf "$PF_HOME/$FILES_TAR" -C "$PF_HOME"/
        local IM_ID
        for ((i = 0; i < ${#SAVED[@]}; i++)); do
            docker load -i "${PF_HOME}"/${SAVED[i]}
            IM_ID=$(docker images | grep none | awk '{ print $3 }')
            if [ ! -z $IM_ID ]; then
                docker tag $IM_ID ${IM[i]}:${TAG[i]}
            fi
        done &
        wait "$!"
    else
        echo "# not fetch images"
    fi
    echo "# starts up"
    if [[ -f nohup.out ]]; then
        rm nohup.out
    fi

    echo "Deployed with version $(cat ${PF_HOME}/$version). check log in nohup"

    # nohup docker-compose -f ${PF_HOME}/${YAML_VM} up >/dev/null 2>&1 &
    nohup docker-compose -f ${PF_HOME}/${YAML_VM} up &
}

function usage() {
    echo -e "$0 [[-no | --no-fetch] | [-h | --help] | [-s | --stop]]
    default: fetch Platform docker images latest version and start Platform
        -h --help: usage.
        -n --no-fetch: not fetch images, still use exiting one.
        -s --stop: stop running apps of cloud platform.
    "
}

# main
if [[ $# -gt 1 ]]; then
    echo "Too many arguments"
    exit 1
elif [[ $# -eq 0 ]]; then
    deploy

else
    arg=$(echo $1)
    echo "$0 $@"
    case $arg in
    -h | --help)
        usage
        exit
        ;;
    -s | --stop)
        deploy $1
        exit
        ;;
    -no | --no-fetch)
        deploy $1
        exit
        ;;
    *)
        usage
        exit
        ;;
    esac
fi
