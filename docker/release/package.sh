#!/bin/bash
set -x
DEV_MODE=$1

if [ $DEV_MODE -eq 0 ]; then
    echo -e "\nNot in DEV_MODE \n"
    rm -rf \
        /project/target/ \
        /project/dbm/target/ &&
        apt-get update &&
        apt-get install \
            -y --no-install-recommends \
            apt-utils \
            procps \
            gawk \
            maven \
            python &&
        mvn compile \
            -Dmaven.test.skip=true \
            -Dcheckstyle.skip=true \
            -ff -f /project/dbm/pom.xml &&
        mvn compile \
            -Dmaven.test.skip=true \
            -Dcheckstyle.skip=true \
            -Djacoco.skip=true \
            war:exploded -ff -f /project/pom.xml
else
    echo -e "\nIn DEV_MODE \n"
fi
