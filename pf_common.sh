#!/bin/bash
PREFIX="compnet_cloud_"
A="${PREFIX}sockstunnel"
B="${PREFIX}portal"
C="${PREFIX}worker"
D="compcloud_cloud_base"
E="${PREFIX}logstash"
IM=("$A" "$B" "$C" "$D" "$E")

TAG_A="latest"
TAG_B="latest"
TAG_C="latest"
TAG_D="latest"
TAG_E="latest"
TAG=("${TAG_A}" "${TAG_B}" "${TAG_C}" "${TAG_D}" "${TAG_E}")

SAVED_A="saved_$A.tar"
SAVED_B="saved_$B.tar"
SAVED_C="saved_$C.tar"
SAVED_D="saved_$D.tar"
SAVED_E="saved_$E.tar"
SAVED=("$SAVED_A" "$SAVED_B" "$SAVED_C" "$SAVED_D" "$SAVED_E")

FILES_TAR="files.tar.gz"
YAML=docker-compose.yml
YAML_VM=docker-compose-managerapp-vm.yml

md5sums=md5sum.txt
version=version.txt

HOME_ON_HOP=/tmp/cloud_home

registry=/tmp/registry.txt
if [[ -f $registry ]]; then
    source $registry
else
    echo -e "pleas provide the registry server info in $registry which looks like\n
IM_SERVER=10.106.6.37\n
IM_SERVER_USER=tom\n
IM_SERVER_PASS='password'"
    exit 1
fi
