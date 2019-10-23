#!/bin/bash

function debug_log() {
  local marker="========="
  echo "$marker $(date +"%T.%3N") $1 $marker"
}

function wait_mysql_up() {
  local mysql_service=mysql
  local mysql_port=3306
  local mysql_up_message="awaiting response... 200"
  local check_command="wget -o - -T 2 http://${mysql_service}:${mysql_port} 2>&1 | grep -o \"${mysql_up_message}\""

  eval "$check_command"
  while [ $? -ne 0 ]; do
    debug_log "waiting till mysql is up"
    sleep 1
    eval "$check_command"
  done
}

function wait_tomcat_up_in_docker() {
  url="https://localhost:8443/proj/login"
  connected_msg="HTTP request sent, awaiting response... 200"
  while true; do
    wget --spider --no-check-certificate --timeout 30 --tries 1 "$url" 2>&1 | grep "${connected_msg}"
    if [ $? == 0 ]; then
      debug_log "PROJ is up"
      break
    else
      # debug_log "Wait PROJ to up"
      sleep 5
    fi
  done
}
