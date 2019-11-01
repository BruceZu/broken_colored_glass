#!/bin/bash

function usage() {
  echo -e "
NAME  Fetch docker images and start docker containers

NOTE:
    You don't have to use this file. It is a template only for test
    docker-compose-managerapp-vm-prod.yml on BPJ VM. E.g.
     sshpass -p 'admin123'  ssh -o 'StrictHostKeyChecking no' -o 'IdentitiesOnly=yes' admin@167.188.21.37
    You may need to upate this file to adapt to your test cases or
    use your own file or manually test it.

    Run this bash with common.sh in the same directory.
    Test files are fetched from hop machine
    Docker images are fetched from register server.

SYNOPSIS
    $0 [[-n | --no-fetch], [-h | --help]]

OPTIONS
    -h --help: usage.
    -n --no-fetch-test-files: not fetch test files, still use exiting one.
"
}

function prepare_test_env() {
  source "${MY_DIR}"/common.sh
}

function prepare_managerapp_vm_env() {
  for directory in ${TMP} ${PERSISTENT_DATA_DIR}; do
    [ ! -d "$directory" ] && mkdir -p $directory
  done
  # required by Docker in BPJ VM
  export TMPDIR=${TMP}
  echo "prepare_managerapp_vm_env: Done"
}

function fetch_test_files() {
  # clean old files
  [ -d "$TEST_FILES_HOME" ] && rm -rf "${TEST_FILES_HOME}/*"
  [ ! -d "$TEST_FILES_HOME" ] && mkdir -p "$TEST_FILES_HOME"

  # fetch
  sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER} ls &
  wait "$!"
  sshpass -p ${IM_SERVER_PASS} scp -r ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/* "${TEST_FILES_HOME}"/ &
  wait "$!"
  echo "prepare_test_env: Done"
}

function clean() {
  echo "containers: stop"
  if [[ -f ${TEST_FILES_HOME}/${YAML} ]]; then
    docker-compose -f ${TEST_FILES_HOME}/${YAML} -f ${TEST_FILES_HOME}/${YAML_TEST} down
  fi

  local IDs=$(ps | grep -E "${YAML}" | grep -v "grep" | awk '{print $1}')
  if [ ! -z "$IDs" ]; then
    echo "$IDs" | xargs kill -9
  fi

  echo "containers: remove"
  for c in $(docker ps -a | grep -E "${IMAGE_GROUP}" | awk '{print $1}'); do
    docker stop $c
    docker rm $c
  done

  echo "images: clean"
  for i in $(docker images | grep -E "${IMAGE_GROUP}|none" | awk '{print $3}'); do
    if [[ "$i" != "IMAGE" ]]; then
      echo "removing image $i"
      docker rmi $i
    fi
  done

  echo "docker-compose customized temp directory: clean"
  rm -rf ${TMP}/*
  echo "clean: Done"
}

function deploy_with_test_file() {
  echo "# starts up"
  if [[ -f nohup.out ]]; then
    rm nohup.out
  fi
  echo "current PROJ version is: $(cat ${TEST_FILES_HOME}/${VESION_FILE}). check log in nohup"

  # nohup docker-compose -f ${TEST_FILES_HOME}/${YAML} -f ${TEST_FILES_HOME}/${YAML_TEST} up  >/dev/null 2>&1 &
  nohup docker-compose -f ${TEST_FILES_HOME}/${YAML} -f ${TEST_FILES_HOME}/${YAML_TEST} up &
}

# main
set -eux

NO_FETCH=0
while [ $# -ne 0 ]; do
  case $1 in
  -n | --no-fetch)
    NO_FETCH=1
    ;;
  -h | --help)
    usage
    exit
    ;;
  *)
    usage
    exit 1
    ;;
  esac

  shift
done

echo "Start with NO_FETCH: $NO_FETCH"
TMP=/var/tmp/docker_compose_proj
TEST_FILES_HOME=/var/proj_home
MY_DIR=$(dirname $(readlink -f $0))
PERSISTENT_DATA_DIR="/var/docker/compportal/projdb_data"

prepare_test_env
prepare_managerapp_vm_env
prepare_using_test_registery_server
[[ $NO_FETCH -eq 0 ]] &&
  fetch_test_files
clean
deploy_with_test_file
