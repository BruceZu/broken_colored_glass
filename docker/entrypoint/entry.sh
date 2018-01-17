#!/bin/bash
/entrypoint/monitor-event.sh &
/entrypoint/handle-event.sh &
/entrypoint/start-tomcat.sh
