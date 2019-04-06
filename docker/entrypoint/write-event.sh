#!/bin/bash
# If using 'echo' command to write the event in monitor-event.sh,
# after the events.txt is renamed, echo will not create a new one.
# with the '-m' option the inotifywait will execute indefinitely without exit.
source /entrypoint/common.sh
debug_log "Event happen: $@"
echo "$@" >>/tmp/events.txt
