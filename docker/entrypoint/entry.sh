#!/bin/bash
source /entrypoint/common.sh

/entrypoint/monitor-event.sh &
/entrypoint/handle-event.sh &
/etc/init.d/apache2 start &
/entrypoint/start-tomcat.sh &
wait_tomcat_up_in_docker &&
  /project/scripts/npm_build.sh watch
