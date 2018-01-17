#!/bin/bash
url="https://localhost:8444/fpc/login"
connected_msg="HTTP request sent, awaiting response... 200 OK"

open_login_page() {
  $1 https://localhost:8444/fpc/login &
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

while true; do
  wget --spider --no-check-certificate --timeout 30 --tries 1 "$url" 2>&1 | grep "${connected_msg}"
  if [ $? == 0 ]; then
    break
  else
    sleep 2
  fi
done

if [ ! -z "$1" ]; then
  browser=$(which $1)
  if [ $? != 0 ]; then
    echo "$1 is not installed"
    try_chrome_firefox
  else
    open_login_page $browser
  fi
else
  try_chrome_firefox
fi
