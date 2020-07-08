#!/bin/bash

# result:
#  0: $1 == $2
# -1: $1 <  $2
#  1: $1 >  $2
function compare_int() {
    _svr=$3
    if [[ $1 -lt $2 ]]; then
        eval $_svr=-1
    elif [[ $1 -gt $2 ]]; then
        eval $_svr=1
    else
        eval $_svr=0
    fi
}

# result:
#  0: $1 == $2
# -1: $1 <  $2
#  1: $1 >  $2
function compare_version() {
    local old_ifs=$IFS
    IFS='.'
    read -ra rva <<<"$1"
    read -ra va <<<"$2"
    IFS=$old_ifs
    _fvr="$3"
    i=0
    while [[ $i -lt ${#va[@]} ]]; do
        compare_int "${rva[$i]}" "${va[$i]}" r
        if [[ $r -lt 0 ]]; then
            eval $_fvr=-1
            return
        elif [[ $r -gt 0 ]]; then
            eval $_fvr=1
            return
        fi
        i=$(($i + 1))
    done
    eval $_fvr=0
}

function join_by() {
    local IFS="$1"
    shift
    echo "$*"
}

function read_version() {
    local v_f=$1
    local k=("${!2}")
    local fs=$3
    local _r=$4

    local i=0
    while [[ $i -lt ${#k[@]} ]]; do
        tmp=$(grep ${k[$i]} $v_f | awk -F"$fs" '{print $2}')
        tmp=$(echo $tmp)
        eval "$_r+=($tmp)"
        i=$(($i + 1))
    done

}

function get_version_str() {
    local b=$(echo "$1")
    local ref=$(echo "$2")
    local remote=$(echo "$3")
    local _str="$4"

    local version_f="project/outservice/server/CM_VERSION"
    git archive -f zip -9 --remote=$remote ${ref:-$b} $version_f >v.zip && unzip -o v.zip && cat $version_f
    local v=()
    local ks=("MAJOR_NUM" "MINOR_NUM" "PATCH_NUM")
    read_version "project/outservice/server/CM_VERSION" ks[@] "=" v
    eval $_str=$(join_by "." "${v[@]}")
}

# 'result' is
# 1: JAIL 5.7.2 : proj version >=5.2
# 0: JAIL 5.5   : proj version < 5.2
function select_tool_chain() {
    local vstr=$(echo "$1")
    local _r="$2"
    local latest_version="5.7.2"
    local old_version="5.5"
    local proj_version="5.2"
    echo "Current branch version: $vstr"
    compare_version "$vstr" "$proj_version" use_latest
    if [[ $use_latest -lt 0 ]]; then
        eval $_r=0
        echo "==== Use toolchain $old_version ===="
    else
        eval $_r=1
        echo "==== Use toolchain $latest_version ===="

    fi
}

function trim_parameter() {
    if [[ ! -z "${GERRIT_REFSPEC:-}" ]]; then
        GERRIT_REFSPEC=$(echo ${GERRIT_REFSPEC})
    fi
    GERRIT_BRANCH=$(echo ${GERRIT_BRANCH})
}

function decide_jail() {
    get_version_str "${GERRIT_BRANCH}" "${GERRIT_REFSPEC:-}" "$REPO" vstr
    select_tool_chain "$vstr" r

    JAIL="${TC_LATEST}"
    if [[ $r -eq 0 ]]; then
        JAIL="${TC_OLD}"
    fi
    echo "==== use toolchain ${JAIL}  ===="
}

function show_dev_head() {
    if [[ $(git rev-parse --is-inside-work-tree) == "true" ]]; then
        echo "==== in toolchain dev environment ===="
        echo "==== Toolchain version:  ===="
        cd ${TC_ROOT}
        git log -1
        cd ${WORKSPACE}
    else
        echo "==== Not in toolchain dev environment ===="
    fi
}

function prepare_home() {
    local home="$1"
    echo "==== prepare a writatble home    ===="
    if [[ ! -d "$home" ]]; then
        mkdir -p "$home"
    else
        ls -al "$home"
        sudo chmod -R o+wr "$home"
        ls -al "$home"
    fi

}
down_src() {
    local my_ws=$1
    local checkouts=(project docker build_in_docker.sh)

    cd $my_ws
    echo "==== clean old src ... ===="
    sudo rm -rf .git
    for i in ${checkouts[@]}; do
        sudo rm -rf "$i"
    done

    echo "==== init  ... ===="
    whoami
    git init .
    git config remote.origin.url "${REPO}"
    git config --add remote.origin.fetch +refs/heads/*:refs/remotes/origin/*
    git config core.sparsecheckout true

    echo "${checkouts[0]}" >.git/info/sparse-checkout
    local i=1
    while [[ $i -lt ${#checkouts[@]} ]]; do
        echo "${checkouts[$i]}" >>.git/info/sparse-checkout
        i=$(($i + 1))
    done

    echo "==== fetch  ... ===="
    GERRIT_REFSPEC=${GERRIT_REFSPEC:-${GERRIT_BRANCH}}
    echo "==== fetch change: https://${GERRIT}/#/c/${GERRIT_REFSPEC:16} ... ===="
    echo "==== If the GERRIT_REFSPEC is not provided, then use the HEAD of the branch... ===="
    git fetch --depth=2 -p -n origin ${GERRIT_REFSPEC}
    git checkout FETCH_HEAD

    echo "==== clean current HEAD ... ===="
    git reset --hard FETCH_HEAD
    echo "==== check status: ===="
    git status
    git branch -av
    echo "==== The Change to build image: ===="
    git log FETCH_HEAD -1
    ls
}

function decide_commit_path() {
    local my_ws="$1"
    local commit_path="$2"
    cd $my_ws

    commit=$(git rev-list FETCH_HEAD -1)
    eval "$commit_path=$(echo $commit | awk '{print substr($0,0,8)}')"
}

function prepare_lable() {
    # map-pacakge generation need lable.conf during compile phrase.
    # provided a mocked one used by docker-gui tool.
    local my_ws=$1
    cd $my_ws

    cp ./docker/tomcat/util/proj/label.conf ./label.conf
}

function src_build() {
    # assume the docker is installed in the Jenkins agent
    local my_ws=$1
    local build_bash="build_in_docker.sh"
    cd $my_ws
    if [[ -f ./${build_bash} ]]; then
        echo "src build start in docker "
        sudo ./${build_bash}
    fi
}

function image_build() {
    local my_ws="$1"
    local image_path="$2"
    echo "==== start  imge  build ...===="
    cd $my_ws && sudo chroot . <run_build.sh
    echo "==== build success ===="
    # show result
    echo "in $image_path, created images:"
    ls -lht $image_path
}

function scp_ftp() {
    # transfer portal image to ftp server
    local created_image_path=$1
    local items=("${!2}")
    local ftp_server="$3"
    local user="$4"
    local pass="$5"
    local server_image_root="$6"

    # server side
    local server_image_curren_path="${server_image_root}/$GERRIT_BRANCH/$COMMIT_PATH"
    local server_image_latest_path="${server_image_root}/LatestBuild"

    # prepare ftp server target dirs
    sshpass -p"$pass" ssh ${user}@${ftp_server} "mkdir -p $server_image_curren_path; rm -rf $server_image_curren_path; mkdir -p $server_image_curren_path"
    # clear   ftp serverlatest images
    sshpass -p"$pass" ssh ${user}@${ftp_server} "rm -rf ${server_image_latest_path}/*"

    # scp
    echo "==== scp portal images to FTP server, other images are discarded ... ===="
    for i in ${items[@]}; do
        sshpass -p "$pass" scp "$created_image_path/$i" "${user}@${ftp_server}:${server_image_curren_path}/"
        sshpass -p "$pass" scp "$created_image_path/$i" "${user}@${ftp_server}:${server_image_latest_path}/"
    done
}
######################### main #########################
set -eu

WORKSPACE=$(pwd)
TC_ROOT=$(readlink -f ./projdev-chroot)
TC_LATEST=${TC_ROOT}/projdev-5.7.2
TC_OLD=${TC_ROOT}/projdev-5.5
# default: latest
JAIL="${TC_LATEST}"

GERRIT="dops-gerrit.compnet-us.com"
REPO="ssh://Jenkins-proj@${GERRIT}:29418/compPortal"

trim_parameter
if [[ "${GERRIT_BRANCH}" == "" ]]; then
    echo "==== GERRIT_BRANCH is required ===="
    exit -1
fi

decide_jail
show_dev_head
prepare_home "${JAIL}/home"
down_src "${JAIL}/home"
decide_commit_path "${JAIL}/home" COMMIT_PATH
prepare_lable "${JAIL}/home"

src_build "${JAIL}/home"
image_build "${JAIL}" "${JAIL}/home/project/target"

images=(projvm64imagePortal.out.ovf.zip projvm64image-Portal.out)
ftp_server="172.30.71.161"
scp_ftp "$JAIL/home/project/target" images[@] $ftp_server "proj" "proj" "/home/proj/compPortal"

echo "==== Images is ready: ftp://$ftp_server/compPortal/$GERRIT_BRANCH/$COMMIT_PATH ===="
echo "==== Created by: https://$GERRIT/#/c/${GERRIT_REFSPEC:16} ===="
echo
