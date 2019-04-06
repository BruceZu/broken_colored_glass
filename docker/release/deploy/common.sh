#!/bin/bash
PROJ_IM_REP="proj_release"
DB_IM_REP="proj_mysql"

PROJ_IM_TAG="latest"
DB_IM_TAG="latest"

SAVED_PROJ="saved_proj.tar"
SAVED_DB="saved_mysql.tar"
YAML=docker-compose-release-proj.yml

HOME_ON_HOP=/tmp/proj

VOLUME_PROJ_MYSQL_KEY=proj_mysql_data_3307

IM_SERVER=188.199.34.21
IM_SERVER_USER=v
IM_SERVER_PASS='thispassword'
