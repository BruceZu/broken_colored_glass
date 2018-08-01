#!/bin/bash
mongo --quiet /home/ec2-user/backup/backup_script.js &>/data/backup/week_$(date +%u)/backup.log
