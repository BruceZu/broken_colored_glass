#!/bin/bash
echo "=============== start monitor event ==============="
inotifywait -r -m -e close_write,move,attrib,delete \
  --format '%w %:e %f %T' \
  --timefmt '%F_%T' /project/src/main \
  --excludei 'messages_[a-z][a-z].js' \
  @/project/src/main/webapp/resources/vue/resources \
  @/project/src/main/java/com/ftnt/fpcs/autoreg |
  xargs -l1 /entrypoint/write-event.sh
