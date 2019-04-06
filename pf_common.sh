#!/bin/bash
PREFIX="compcloud_"
A="${PREFIX}sockstunnel"
B="${PREFIX}portal"
C="${PREFIX}worker"
D="${PREFIX}cloud_base"
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

HOME_ON_HOP=/tmp/cloud_home

IM_SERVER=188.199.34.21
IM_SERVER_USER=v
IM_SERVER_PASS='thispassword'
