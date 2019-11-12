#!/bin/bash
set -eux
DEV_MODE=$1

if [ $DEV_MODE -eq 0 ]; then
    echo -e "\nNot in DEV_MODE \n"
    rm -rf \
        /project/target/ \
        /project/dbm/target/
    apt-get update
    apt-get install \
        -qq \
        --no-install-recommends \
        apt-utils \
        procps \
        gawk \
        maven \
        python3 \
        python3-pip
    pip3 install lxml gitpython
    ./change_src_for_managerapp_vm.py
    mvn compile \
        -q \
        -Dmaven.test.skip=true \
        -Dcheckstyle.skip=true \
        -ff -f /project/dbm/pom.xml
    mvn compile \
        -q \
        -Dmaven.test.skip=true \
        -Dcheckstyle.skip=true \
        -Djacoco.skip=true \
        war:exploded -ff -f /project/pom.xml
else
    echo -e "\nIn DEV_MODE \n"
fi
