#!/bin/bash
function usage() {
    echo -e "
NAME delivery docker images.

    With current commit on 'un-safe' branch to build, tag and delivery images to
    target registry server, if need plus files for test to a hope server

NOTE:
    You don't have to use this file. It is a template only for
    'docker push' docker images used by docker-compose-managerapp-vm-prod.yml on BPJ VM. E.g.
    sshpass -p 'admin123'  ssh -o 'StrictHostKeyChecking no' -o 'IdentitiesOnly=yes' admin@167.188.21.37
    You may need to upate this file to adapt to your test cases or
    use your own file or manually deliver docker images.

    Run this bash with common.sh in the same directory.

SYNOPSIS
    $0 [[-t | --test,  -r |--reuse_old_images] | [-h | --help]]

OPTIONS
        -h --help: usage.
        -t --test mode: Also deliery file(s) for test in BPJ VM.
        -r --reuse_old_images: skip the image build/release steps.
                               helpful when no chanages on docker images
"

}

function release_software_check() {
    check_software docker
    check_software docker-compose
}

function hop_access_software_check() {
    check_software sshpass
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

    echo "test files: clean"
    cd $ROOT && rm -rf "${VESION_FILE}"
}

function build_images() {
    echo "images: build"
    cd $ROOT && ./release_docker_version.sh -b
}

function confirm_default_or_input() {
    local confirm_ask="$1"
    local default_value="$2"
    local _result_var="$3"

    local feedback
    echo -e "${confirm_ask}"
    read -p "use ${default_value}?[Y/n]" -r feedback

    if [[ -z "$feedback" || "$feedback" =~ ^([yY][eE][sS]|[yY]).*$ ]]; then
        echo "You select the defaulut value: $default_value"
        eval $_result_var=$default_value
    else
        local new_value
        read -p "Input new value and press [ENTER]: " new_value
        echo "Your input: $new_value"
        eval $_result_var=$new_value
    fi
}

function release() {
    # Assume proj and customized db has the same tag
    local proj_image="${IMAGE_GROUP}/compportal"
    local proj_db_image="${proj_image}_db"
    local proj_img_tag="latest"
    local proj_db_img_tag="latest"

    # ask target registry server and tag
    local registry="${REGISTRY_SERVER_TEST}"
    local tag="latest"

    local confirm_ask="Provide registry hostname.
The hostname must comply with standard DNS rules, but may not contain underscores.
If a hostname is present, it may optionally be followed by a port number in the format :8080.
If not present, the command uses test registry server located at ${registry} by default.\n"

    confirm_default_or_input "${confirm_ask}" "${registry}" registry

    confirm_ask="Provide tag name.
Tag name must be valid ASCII and may contain lowercase and uppercase letters, digits,
underscores, periods and dashes.
A tag name may not start with a period or a dash and may contain a maximum of 128 characters.
If not present, the command uses ${tag} by default.\n"
    confirm_default_or_input "${confirm_ask}" "${tag}" tag

    local target_proj_image="${registry}/${proj_image}:${tag}"
    local target_proj_db_image="${registry}/${proj_db_image}:${tag}"

    echo -e "Image name will be:\n ${target_proj_db_image} \nand\n ${target_proj_image}"

    # docker tag
    docker tag ${proj_db_image}:${proj_db_img_tag} ${target_proj_db_image}
    docker tag ${proj_image}:${proj_img_tag} ${target_proj_image}
    # docker login
    docker login ${registry}
    # docker push
    docker push ${target_proj_db_image}
    docker push ${target_proj_image}
}

function delivery_test_files() {
    echo "$HOME_ON_HOP: clean"
    echo "rm -rf ${HOME_ON_HOP}; mkdir -p ${HOME_ON_HOP}/${LOG_CONFIG_PATH}; ls -l ${HOME_ON_HOP}" |
        sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER}

    echo "delivery files for test: version, log configure file, deploy yml"
    echo "$(git describe --tags --long --always) " >"${ROOT}/${VESION_FILE}"
    cd $ROOT
    sshpass -p ${IM_SERVER_PASS} scp "$YAML" "$YAML_TEST" "${VESION_FILE}" \
        ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}/"

    sshpass -p ${IM_SERVER_PASS} scp "${LOG_CONFIG_PATH}/${LOG_CONFIG_F}" \
        ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}/${LOG_CONFIG_PATH}/${LOG_CONFIG_F}"

    echo " check deliveryed files"
    echo "ls -l ${HOME_ON_HOP} " | sshpass -p ${IM_SERVER_PASS} ssh -it ${IM_SERVER_USER}@${IM_SERVER}
    rm -f "${ROOT}/${VESION_FILE}"
}

## main
set -eux

FROM="$(pwd)"
ROOT=$(git rev-parse --show-toplevel)
I=$(readlink -f $0)
MY_PATH=$(dirname $I)
source "${MY_PATH}"/common.sh

TEST_MODE=0
REUSE=0
while [ $# -ne 0 ]; do
    case $1 in
    -t | --test)
        TEST_MODE=1
        ;;
    -r | --reuse_old_images)
        REUSE=1
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
echo "Start with TEST_MODE: $TEST_MODE, REUSE: $REUSE"
[ $REUSE -eq 0 ] &&
    release_software_check &&
    local_clean &&
    build_images &&
    prepare_using_test_registery_server &&
    release

[ $TEST_MODE -eq 1 ] &&
    hop_access_software_check &&
    check_hop_access &&
    delivery_test_files

cd "$FROM"
