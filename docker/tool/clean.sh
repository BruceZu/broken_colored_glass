#!/bin/bash
echo "Clean containers and data in Mysql"
docker rm $(docker ps -a -q)
docker volume rm compportal_mysql_data_3307
