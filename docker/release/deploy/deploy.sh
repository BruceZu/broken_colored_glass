#!/bin/bash
# Run this bash with common.sh in the same directory somewhere at destination VM.
# Test with:
#   sshpass -p 'admin123'  ssh -o "StrictHostKeyChecking no" -o "IdentitiesOnly=yes" admin@167.188.21.37
#   images is fetche from ${IM_SERVER_USER}@${IM_SERVER}. TODO: fetch them from compGuard.
set -eux

function deploy() {
  local I=$(readlink -f $0)
  local MY_PATH=$(dirname $I)
  source "${MY_PATH}"/common.sh

  local PROJ_HOME=/var/proj_home
  local TMP=/var/tmp/docker_compose_proj
  mkdir -p ${TMP}
  export TMPDIR=${TMP}
  # old process: kill
  if [[ -f ${PROJ_HOME}/${YAML} ]]; then
    docker-compose -f ${PROJ_HOME}/${YAML} down
  fi

  local IDs=$(ps | grep -E "${YAML}" | grep -v "grep" | awk '{print $1}')
  if [ ! -z "$IDs" ]; then
    echo "$IDs" | xargs kill -9
  fi

  # /tmp/_MEI*
  rm -rf ${TMP}/*

  if [[ -z "${2:-}" ]]; then
    echo "Fetch images ..."
    # container : clean up
    containers=$(docker ps -a | grep -E "proj" | awk '{print $1}')
    for c in ${containers}; do
      docker stop $c
      docker rm $c
    done

    # images: clean
    for i in $(docker images | grep -E "proj_release|proj_mysql|none" | awk '{print $3}'); do
      if [[ "$i" != "IMAGE" ]]; then
        echo "removing image $i"
        docker rmi $i
      fi
    done

    # network and volume
    # docker volume rm -f $VOLUME_PROJ_MYSQL

    # clean local
    rm -rf "${PROJ_HOME}"
    mkdir -p "$PROJ_HOME"

    # scp
    # create knowhost
    sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER} ls &
    wait "$!"
    sshpass -p ${IM_SERVER_PASS} scp -r ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/* "${PROJ_HOME}"/ &
    wait "$!"
    md5sum "${PROJ_HOME}"/saved*.tar
    local IM_ID
    local KEY=(${SAVED_PROJ} ${SAVED_DB})
    local REP=(${PROJ_IM_REP} ${DB_IM_REP})
    local TAG=(${PROJ_IM_TAG} ${DB_IM_TAG})
    for ((i = 0; i < ${#KEY[@]}; i++)); do
      docker load -i "${PROJ_HOME}"/${KEY[i]}
      IM_ID=$(docker images | grep none | awk '{ print $3 }')
      if [ ! -z $IM_ID ]; then
        docker tag $IM_ID ${REP[i]}:${TAG[i]}
      fi
    done &
    wait "$!"

  fi
  # starts up
  echo "current PROJ version is: $(cat ${PROJ_HOME}/${VESION_FILE})"
  nohup docker-compose -f ${PROJ_HOME}/${YAML} up &
}

function usage() {
  echo -e "$0 [[-no | --no-fetch] | [-h | --help]]
    fetch PROJ docker images latest version and start PROJ
        -h --help: usage.
        -no --no-fetch: not fetch images, still use exiting one.
    "
}

# main

if [[ $# -eq 0 ]]; then
  deploy $@
else
  while [ "$1" != "" ]; do
    case $1 in
    -no | --no-fetch)
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
