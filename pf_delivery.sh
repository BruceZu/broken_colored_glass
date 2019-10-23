#!/bin/bash
##  with current commit to build image/tag image/delivery the following files to target docker image repository.
# - 4 temporary docker images. After download to BPJ VM, they will be installed in the local docker repository maintained by the docker itself.
# - 1 docker-compose-managerapp-vm YAML file
# - 1 version.txt file(For debug: map git commit to current docker images)
# - 1 md5sum.txt(verify integration before using docker image registry server)
#
## Test repository is :
#   delivery images to ${IM_SERVER_USER}@${IM_SERVER}. TODO: delivery to docker image repository once it is available.
## '-k' option:
#   is helpful to skip the image build step for some corner case.
#   e.g.: the images registry server disk is full. or no chanages to docker image

function tag() {
    local root_dir=$1
    local project_name="$($root_dir/docker_cli get-project-name)"
    # need not all docker images.
    local deploy_id="hardware-a_dev_managerapp"
    for service in $($root_dir/find_services_to_apply.py $deploy_id); do
        docker tag ${project_name}_$service:latest $PREFIX$service:latest
    done
}

set -eux
FROM="$(pwd)"
I=$(readlink -f $0)
MY_PATH=$(dirname $I)
source "${MY_PATH}"/pf_common.sh

P_ROOT="$(git rev-parse --show-toplevel)"

echo "container: clean"
for c in $(docker ps -a | grep $PREFIX | awk '{print $1}'); do
    docker stop $c
    docker rm $c
done

if [[ $# -gt 0 && "$1" == "-k" ]]; then
    echo "skip build docker images... "

else
    echo "images: clean"
    for i in $(docker images | grep -E "$A|$B|$C|$D|$E" | awk '{print $3}'); do
        if [[ "$i" != "IMAGE" ]]; then
            echo "removing image $i"
            docker rmi $i
        fi
    done

    echo "saved images tar: clean"
    cd $P_ROOT
    for i in ${SAVED[@]}; do
        if [[ -f $i ]]; then
            cd $P_ROOT && rm -rf $i
        fi
    done
    echo "images: build and save -> tar files"
    cd $P_ROOT
    echo "build docker images "
    cd $P_ROOT
    ./deploy/nodejs/install_nodejs.sh
    make image DEPLOY_ID=hardware-a_dev_managerapp
    tag $P_ROOT
fi

for ((i = 0; i < ${#SAVED[@]}; i++)); do
    docker save -o ${SAVED[$i]} ${IM[$i]}:${TAG[$i]}
done

# images under HOME_ON_HOP : clean
echo "rm -rf ${HOME_ON_HOP};  mkdir -p ${HOME_ON_HOP}; ls -l ${HOME_ON_HOP}" |
    sshpass -p ${IM_SERVER_PASS} ssh -o "StrictHostKeyChecking no" ${IM_SERVER_USER}@${IM_SERVER}

# SCP tar
ls -lh *.tar
echo "delivering images, it may need half an hour or more, depends on the size and bandwidth ..."
for ((i = 0; i < ${#SAVED[@]}; i++)); do
    echo "delivering ${SAVED[$i]} ... "
    sshpass -p ${IM_SERVER_PASS} scp "${SAVED[$i]}" \
        ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/
done

# SCP yml and files (under project root) needed to start containers
tmp_path="./tmp"
mkdir -p "$tmp_path/"
cp "$YAML_VM" "$tmp_path/"
config_files=(
    # nodes/elasticsearch/files/usr/share/elasticsearch/config/elasticsearch.yml
)
for f in ${config_files[@]}; do
    base="$tmp_path/${f%/*}"
    mkdir -p "${base}"
    cp -r "$P_ROOT/$f" "${base}/"
done

## record the release version
echo "$(git describe --tags --long --always) " >$tmp_path/$version
tar -czvf "${FILES_TAR}" -C "$tmp_path" .
tar -tf "${FILES_TAR}"

echo "delivering ${FILES_TAR}... "
sshpass -p ${IM_SERVER_PASS} scp -r "${FILES_TAR}" \
    ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/

# tar file: md5sum in local

md5sum saved*.tar "${FILES_TAR}" >./${md5sums}
cat ./${md5sums}
sshpass -p ${IM_SERVER_PASS} scp -r ./${md5sums} \
    ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/
rm ./${md5sums}

# tar file under hop /tmp: md5sum
echo "md5sum ${HOME_ON_HOP}/saved*.tar ${HOME_ON_HOP}/${FILES_TAR}; ls -lh ${HOME_ON_HOP} " |
    sshpass -p ${IM_SERVER_PASS} ssh -it ${IM_SERVER_USER}@${IM_SERVER}

# clean
for t in saved*.tar "${FILES_TAR}"; do
    rm -f "$t"
done

rm -rf "$tmp_path"
echo "Success"
cd $FROM
