#!/bin/bash
# Test with:
#   delivery images to ${IM_SERVER_USER}@${IM_SERVER}. TODO: to registeryserver.
#'-k' option:
#   is helpful to skip the image build step for some corner case.
#   e.g.: the images registry server disk is full.

set -eux
P_ROOT=$(git rev-parse --show-toplevel)
COMMON_FILE=${P_ROOT}/pf_common.sh
source ${COMMON_FILE}

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
    make managerapp_vm
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
cp "$YAML" "$YAML_VM" "ENV" "$tmp_path/"
files=(
    nodes/elasticsearch/files/usr/share/elasticsearch/config/elasticsearch.yml
    nodes/kibana/files/usr/share/kibana/config/*
)
for f in ${files[@]}; do
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
