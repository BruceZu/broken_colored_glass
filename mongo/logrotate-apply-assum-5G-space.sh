#!/bin/bash

#################################################################################################
# Assume the log volume is 5G (In fact it will be full with 4.6G )
# see
# Linux logrotate https://linode.com/docs/uptime/logs/use-logrotate-to-manage-log-files/
# Mongo log rotate
# https://docs.mongodb.com/v3.6/reference/configuration-options/#systemlog-options
# https://docs.mongodb.com/v3.6/tutorial/rotate-log-files/#overview
# https://docs.mongodb.com/manual/reference/configuration-options/#systemLog.logRotate
# https://docs.mongodb.com/manual/tutorial/rotate-log-files/#forcing-a-log-rotation-with-sigusr1
#################################################################################################

source ./common.sh
readonly MONGO_CONFIG_F="/etc/mongod.conf"
main() {
  grep logRotate ${MONGO_CONFIG_F}
  if [[ $? != 0 ]]; then
    echo "\n\n======= Not found mongod log rotation option in mongod.conf. Now add it"
    sudo sed -i '/systemLog/a \  logRotate: reopen' ${MONGO_CONFIG_F} &&
      sudo service mongod restart
  fi
  echo -e "\n\n======= Double check the mongod log rotation option: "
  cat ${MONGO_CONFIG_F}

  # local ask="Please check the udpated Mongo config"
  # confirm_or_exit "${ask}"

  echo -e "\n\n======= Make the logrotate configuration for mongod.log =="
  echo '/log/mongod.log {
    maxsize 400M 
    rotate 7
    missingok
    delaycompress
    dateext
    dateformat -%Y-%m-%d_%s
    ifempty
    postrotate
        /bin/kill -SIGUSR1 $(cat /data/mongod.lock)
    endscript
}' | sudo tee /etc/logrotate.d/mongodlog

  if [[ ! -e /etc/cron.hourly/logrotate ]]; then
    sudo mv /etc/cron.daily/logrotate /etc/cron.hourly/
  fi

  echo -e "\n\n======= Kick it out, and check mongod.log =="
  /usr/sbin/logrotate -v -f -d /etc/logrotate.d/mongodlog &&
    ls -lah /log
  echo -e "\n\n======= Check logrotate status: the time of logrotate for mongod.log =="
  sort -k2 /var/lib/logrotate.status | grep mongod
}
main "$@"
