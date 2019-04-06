#!/bin/sh
SCRIPT=$(perl -e 'use Cwd "abs_path"; print abs_path(shift)' $0)
from=$(pwd)
cd "$(dirname $SCRIPT)"
./open-login-page.sh &
docker-compose down && docker-compose up
cd $from
