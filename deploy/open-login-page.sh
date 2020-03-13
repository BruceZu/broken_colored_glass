#!/bin/bash
source ./docker/entrypoint/common.sh
url="https://localhost:8444/proj/login"
connected_msg="HTTP request sent, awaiting response... 200"

open_login_page() {
  $1 https://localhost:8444/proj/login &
}

try_chrome_firefox() {
  echo "Try google-chrome and firefox in turn ..."
  browser=$(which google-chrome)
  if [ $? != 0 ]; then
    browser=$(which firefox)
    if [ $? != 0 ]; then
      echo "Google Chrome and Firefox are not find" && exit 0
    fi
  fi
  open_login_page $browser
}
SECONDS=0

# Wait building docker image
while [ $(docker-compose images | grep tomcat | wc -l) == 0 ]; do
  sleep 2
done
# Wait starting docker container, maven pacaking ...
sleep 10

while true; do
  if [[ $(docker-compose ps | grep tomcat | awk '{print $3}') != Up ]]; then
    debug_log "The tomcat docker container is not available."
    exit 1
  fi
  wget --spider --no-check-certificate --timeout 30 --tries 1 "$url" 2>&1 | grep "${connected_msg}"
  if [ $? == 0 ]; then
    debug_log "PROJ is up, ready to login."
    duration=$SECONDS
    debug_log "In total used $(($duration / 60)) minutes and $(($duration % 60)) seconds."
    break
  else
    sleep 2
  fi
done

if [ ! -z "$1" ]; then
  browser=$(which $1)
  if [ $? != 0 ]; then
    debug_log "$1 is not installed"
    try_chrome_firefox
  else
    open_login_page $browser
  fi
else
  try_chrome_firefox
fi
