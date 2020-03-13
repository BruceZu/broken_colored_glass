#!/bin/bash
set -u

function remap() {
  local i=$(whoami)
  local id=$(id -u $i)
  local gid=$(id -g $i)
  local docker_gid=$(getent group docker | cut -d: -f3)

  local u_f=/etc/subuid
  local g_f=/etc/subgid
  local entry="\"userns-remap\": \"${i}:${i}\""
  local daemon_f=/etc/docker/daemon.json
  warn=\
"----------------------------------------------------------------------\n
WARN:This may affect any other docker container running on\n
current machine. In that case try \"--userns=host\" see\n
https://docs.docker.com/engine/security/userns-remap/\n\n
Disable \"userns-remap\":\n
 delete the \`$entry\` from $daemon_f\n
---------------------------------------------------------------------\n\n"
  echo -e $warn

  echo "prepare remap configure ... "
  # for migration to version with remap feature
  local react_output=./project/src/main/webapp/resources/react
  local mvn_proj_output=./project/target
  local mvn_dgm_output=./project/dbm/target
  local lang_vue_output=./project/src/main/webapp/resources/vue/resources
  local lang_react_ouput=./project/src/main/webapp/resources/react/lang
  local event_exposed=/tmp/proj_docker_event

  for out in "${react_output}" \
    "${mvn_proj_output}" \
    "${mvn_dgm_output}" \
    "${lang_vue_output}" \
    "${lang_react_ouput}" \
    "${event_exposed}"; do
    if [[ -d $out ]]; then
      sudo rm -rf $out
    fi
  done

  # enable remap between host current user and root in docker container

  expected=${i}:${id}:65536
  grep "^${expected}" $u_f
  if [[ $? -ne 0 ]]; then
    echo "backup and configure $u_f"
    sudo sed -i "s/$i.*/#&/" $u_f
    sudo sed -i "1i${expected}" $u_f
  else
    echo "$u_f has already ready "
  fi

  expected=${i}:${docker_gid}:65536
  grep "^${expected}" $g_f
  if [[ $? -ne 0 ]]; then
    echo "backup and configure $g_f"
    sudo sed -i "s/$i.*/#&/" $g_f
    sudo sed -i "1i${expected}" $g_f
  else
    echo "$g_f has already ready "
  fi

  sudo chmod o+rx /etc/docker

  if [[ ! -f "$daemon_f" ]]; then
    echo "create new $daemon_f"
    cat >/tmp/daemon.json <<END
{
 $entry
}
END
    sudo mv /tmp/daemon.json /etc/docker/

  else
    # daemon configure exists
    grep "$entry" "$daemon_f"
    if [[ $? -ne 0 ]]; then
      ## add entry
      echo "Error: need to add $entry to $daemon_f. Then try again"
      exit 1
    else
      echo "$daemon_f has already configured"
    fi

  fi

  sudo chmod o-rx /etc/docker
  sudo service docker restart
  if [[ $? -ne 0 ]]; then
    echo "Failed to start dockerd, check $g_f and $u_f"
    exit 1
  fi

  while true; do
    systemctl show --property ActiveState docker | grep inactive
    if [[ $? -eq 1 ]]; then
      break
    fi
    sleep 1
  done
}

function help() {
  echo -e "-r Enable docker userns-remap. Run once is enough.\n-h Usage"
}

cur=$(pwd)
cd ${0%/*}/

option=${1:-""}
if [[ $# -gt 1 ]]; then
  help
  exit
elif [[ $# -eq 1 ]]; then
  if [[ "${option}" == "-r" || "${option}" == "--remap" ]]; then
    remap
  else
    help
    exit
  fi
fi

docker-compose down
./open-login-page.sh &
docker-compose up

cd $cur
