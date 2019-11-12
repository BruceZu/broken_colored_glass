#!/bin/bash
function release_software_check() {
    check_software docker
    check_software docker-compose
}

function local_clean() {
    echo "container: clean"

    for c in $(docker ps -aq); do
        docker stop $c
        docker rm $c
    done

    echo "images: clean"
    for i in $(docker images | grep -E "${IMAGE_GROUP}|none" | awk '{print $3}'); do
        docker rmi -f $i
    done
}

function build_images() {
    echo "images: build"
    cd $ROOT && ./release_docker_version.sh -b
}

## main
set -eu
FROM="$(pwd)"
ROOT=$(git rev-parse --show-toplevel)
I=$(readlink -f $0)
MY_PATH=$(dirname $I)
source "${MY_PATH}"/common.sh

release_software_check
local_clean
build_images
cd "$FROM"
