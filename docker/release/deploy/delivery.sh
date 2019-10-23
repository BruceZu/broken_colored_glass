#!/bin/bash
# Test with:
#   delivery images to ${IM_SERVER_USER}@${IM_SERVER}. TODO: to compGuard.
set -x
ROOT=$(git rev-parse --show-toplevel)
COMMON_FILE=${ROOT}/docker/release/deploy/common.sh
source ${COMMON_FILE}
echo "$IM_SERVER  $IM_SERVER_USER $IM_SERVER_PASS "

check_software sshpass
check_software docker
check_software docker-compose
# manage_docker_as_a_non-root_user

# container: clean
docker ps -aq | xargs docker stop
docker ps -aq | xargs docker rm

# images: clean
docker images | grep -E "proj_release|proj_mysql|none" | awk '{print $3}' | xargs docker rmi

# saved images tar: clean
cd $ROOT && rm -rf saved*.tar "${VESION_FILE}"

# images: build and save -> tar files
cd $ROOT && ./release_docker_version.sh -b &&
docker save -o ${SAVED_PROJ} ${PROJ_IM_REP}:${PROJ_IM_TAG} &&
docker save -o ${SAVED_DB} ${DB_IM_REP}:${DB_IM_TAG}

# tar file: md5sum in local
md5sum saved*.tar

# images under HOME_ON_HOP : clean
echo "rm -rf ${HOME_ON_HOP}; mkdir -p ${HOME_ON_HOP}; mkdir -p ${HOME_ON_HOP}/${LOG_CONFIG_PATH}; ls -l ${HOME_ON_HOP}" |
sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER}

# SCPQ
echo "$(git describe --tags --long --always) " >${ROOT}/${VESION_FILE}
sshpass -p ${IM_SERVER_PASS} scp "${SAVED_PROJ}" "${SAVED_DB}" "$YAML" "${VESION_FILE}" \
${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}/"

sshpass -p ${IM_SERVER_PASS} scp "${LOG_CONFIG_PATH}/${LOG_CONFIG_F}" \
${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}/${LOG_CONFIG_PATH}/${LOG_CONFIG_F}"

# tar file under hop /tmp: md5sum
echo "md5sum ${HOME_ON_HOP}/saved*.tar; ls -l ${HOME_ON_HOP} " |
sshpass -p ${IM_SERVER_PASS} ssh -it ${IM_SERVER_USER}@${IM_SERVER}
