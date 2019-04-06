#!/bin/bash
# Test with:
#   delivery images to ${IM_SERVER_USER}@${IM_SERVER}. TODO: to registeryserver.
# set -x
P_ROOT=$(git rev-parse --show-toplevel)
COMMON_FILE=${P_ROOT}/pf_common.sh
source ${COMMON_FILE}

# container: clean
for c in $(docker ps -aq); do
    docker stop $c
    docker rm $c
done

# images: clean
docker images | grep -E "$A|$B|$C|$D|$E|none" | awk '{print $3}' | xargs docker rmi

# saved images tar: clean
cd $P_ROOT
for i in ${SAVED[@]}; do
    cd $P_ROOT && rm -rf $i
done
# images: build and save -> tar files

cd $P_ROOT && ./docker_cli build &&
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
cp "$YAML" "$YAML_VM" "$tmp_path/"
files=(
    apps/cloudinfra/*
    apps/portal/*
    apps/sockstunnel-py/*
    nodes/elasticsearch/files/usr/share/elasticsearch/config/elasticsearch.yml
    nodes/kibana/files/usr/share/kibana/config/*
    nodes/logstash/files/usr/share/logstash/config/logstash.yml
    nodes/logstash/files/usr/share/logstash/pipeline/*
    nodes/logstash/files/usr/share/logstash/template/*
    nodes/portal/files/etc/nginx/conf.d
)
for f in ${files[@]}; do
    base="$tmp_path/${f%/*}"
    mkdir -p "${base}"
    cp -r "$P_ROOT/$f" "${base}/"
done

tar -czvf "${FILES_TAR}" -C "$tmp_path" .
echo "delivering ${FILES_TAR}... "
sshpass -p ${IM_SERVER_PASS} scp -r "${FILES_TAR}" \
    ${IM_SERVER_USER}@${IM_SERVER}:"${HOME_ON_HOP}"/

# tar file: md5sum in local
md5sum saved*.tar "${FILES_TAR}"

# tar file under hop /tmp: md5sum
echo "md5sum ${HOME_ON_HOP}/saved*.tar ${HOME_ON_HOP}/${FILES_TAR}; ls -lh ${HOME_ON_HOP} " |
    sshpass -p ${IM_SERVER_PASS} ssh -it ${IM_SERVER_USER}@${IM_SERVER}

# clean
for t in saved*.tar "${FILES_TAR}"; do
    rm -f "$t"
done

rm -rf "$tmp_path"
