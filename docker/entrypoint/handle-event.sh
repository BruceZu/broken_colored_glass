#!/bin/bash
source /entrypoint/common.sh
mkdir -p /tmp/event
event_root=/tmp/event
UNDER_WRITE=${event_root}/events.txt
UNDER_FILTER=${event_root}/events_under_filter.txt
UNDER_HANDLE=${event_root}/events_under_handle.txt
UNDER_HANDLE_NO_MESSAGE=${event_root}/under_handle_no_message.txt
POM=/project/pom.xml

JAVA_BASE=/project/src/main/java
RESOURCES_BASE=/project/src/main/resources/
RESOURCES_TARGET_PATH=/project/target/proj/WEB-INF/classes/
WEBAPP_BASE=/project/src/main/webapp/
WEBAPP_TARGET_PATH=/project/target/proj/

MESSAGES_CONVERT_PYTHON_SCRIPT=/project/utils/convert_messages.py
MESSAGES_JS_FILES=/project/src/main/webapp/resources/vue/resources/messages_*.js
MESSAGES_JS_FILES_TARGET_PATH=/project/target/proj/resources/vue/resources/

# create message json file for React
MESSAGES_JS_FILES_REACT=/project/src/main/webapp/resources/react/lang/messages_*.json
MESSAGES_JS_FILES_TARGET_PATH_REACT=/project/target/proj/resources/react/lang/

copy() {
  dir=$1
  file=$2
  src_base=$3
  target_base=$4

  tail=${dir:${#src_base}}
  if [[ ! -d "$target_base$tail" ]]; then
    debug_log "mkdir -p $target_base$tail"
    mkdir -p $target_base$tail
  fi
  debug_log "cp $dir$file $target_base$tail"
  cp $dir$file $target_base$tail
}
while true; do
  if [ ! -e "${UNDER_WRITE}" ]; then
    sleep 1 # Todo: configurable
    continue
  fi
  mv ${UNDER_WRITE} ${UNDER_FILTER}

  # Todo: handle "DELETE" and "DELETE:ISDIR" event.
  awk '($2!="DELETE" && $2!="DELETE:ISDIR"){print ($1,$2,$3)};' ${UNDER_FILTER} >${UNDER_HANDLE}
  if [ $(wc -l <${UNDER_HANDLE}) == 0 ]; then
    debug_log "ignore DELETE and DELETE:ISDIR event"
    continue
  fi

  java_changed=false
  mv_happened=false
  grep -s -E $JAVA_BASE ${UNDER_HANDLE}
  if [ $? == 0 ]; then
    java_changed=true
  fi
  if [ "$(grep -s MOVE ${UNDER_HANDLE})" != "" ]; then
    mv_happened=true
  fi

  if [ $java_changed = true ] && [ $mv_happened = false ]; then
    debug_log "event on java file only"
    mvn compiler:compile -Dmaven.compiler.useIncrementalCompilation=false war:exploded -f $POM &&
      cp /tomcat/webapps/proj/WEB-INF/classes/database.properties /project/target/proj/WEB-INF/classes/
    continue
  elif [ $java_changed = false ] && [ $mv_happened = true ]; then
    debug_log "MOVE event on non-java file"
    mvn resources:resources war:exploded -f $POM &&
      cp /tomcat/webapps/proj/WEB-INF/classes/database.properties /project/target/proj/WEB-INF/classes/
    continue
  elif [ $java_changed = true ] && [ $mv_happened = true ]; then
    debug_log "MOVE event and event on java file"
    # no matter java file is moved of not
    mvn compiler:compile -Dmaven.compiler.useIncrementalCompilation=false resources:resources war:exploded -f $POM &&
      cp /tomcat/webapps/proj/WEB-INF/classes/database.properties /project/target/proj/WEB-INF/classes/
    continue
  fi
  # Todo:
  # - apply 'copy' solution later on MOVE event on no-java file
  # - use javac instead.

  # message_<language>.properties files
  message_changed=false
  for file in $(awk '{print $3}' ${UNDER_HANDLE}); do
    if [[ $file =~ messages_[a-z][a-z].properties ]]; then
      debug_log "For message_<language>.properties files ..."
      message_changed=true
      ${MESSAGES_CONVERT_PYTHON_SCRIPT} &&
        cp $MESSAGES_JS_FILES $MESSAGES_JS_FILES_TARGET_PATH &&
        cp $MESSAGES_JS_FILES_REACT $MESSAGES_JS_FILES_TARGET_PATH_REACT &&
        awk '$3 !~ /messages_[a-z][a-z].properties/ ' ${UNDER_HANDLE} >${UNDER_HANDLE_NO_MESSAGE}
      debug_log "cp $MESSAGES_JS_FILES $MESSAGES_JS_FILES_TARGET_PATH"
      debug_log "cp $MESSAGES_JS_FILES_REACT $MESSAGES_JS_FILES_TARGET_PATH_REACT"
      cat ${UNDER_HANDLE_NO_MESSAGE}
      break
    fi
  done
  if [[ ${message_changed} == true ]]; then
    if [ $(wc -l <${UNDER_HANDLE_NO_MESSAGE}) == 0 ]; then
      continue
    fi
  else
    debug_log "No update on message_<language>.properties files ..."
    install ${UNDER_HANDLE} ${UNDER_HANDLE_NO_MESSAGE}
  fi

  # ATTRIB and CLOSE_WRITE event.
  # create or modify file under RESOURCES_BASE or WEBAPP_BASE.
  # modify file attribute or content.
  while IFS=$" " read path event file; do
    if [[ "$event" == "ATTRIB:ISDIR" ]]; then
      continue
    fi
    if [[ $path =~ ^$WEBAPP_BASE ]]; then
      copy $path $file $WEBAPP_BASE $WEBAPP_TARGET_PATH
    fi

    if [[ $path =~ ^$RESOURCES_BASE ]]; then
      copy $path $file $RESOURCES_BASE $RESOURCES_TARGET_PATH
    fi
  done \
    <${UNDER_HANDLE_NO_MESSAGE} |
    sort -k2 -d | uniq
done
