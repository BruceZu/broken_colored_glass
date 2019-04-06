#!/bin/bash
source /entrypoint/common.sh
debug_log "Start monitor event"
inotifywait -r -m -e close_write,move,attrib,delete \
  --format '%w %:e %f %T' \
  --timefmt '%F_%T' /project/src/main \
  --excludei 'messages_[a-z][a-z].js' \
  @/project/src/main/webapp/react \
  @/project/src/main/webapp/resources/vue/resources \
  @/project/src/main/java/com/coustomer/projs/autoreg \
  @/project/src/main/java/com/coustomer/projs/backup |
  xargs -l1 /entrypoint/write-event.sh
