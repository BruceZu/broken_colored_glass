#!/bin/sh
from=$(pwd)
cd "$(dirname $(readlink -nf "$0"))"
./open-login-page.sh &
docker-compose stop && docker-compose up
cd $from
