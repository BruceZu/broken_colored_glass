#!/bin/bash

function debug_log() {
  local marker="========="
  echo "$marker $1 $marker"
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
