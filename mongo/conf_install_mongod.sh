#!/bin/bash
# set -x
source ./common.sh

function printUsage() {
  cat <<EOF
Usage: $0 <replset name>
EOF
}
#########################################################################################
# Assume
# EC2 instances are ready for PRIMARY or SECONDARY member
# OS:  Amazon Linux AMI  EBS-backed,
# nodes are located in different zone; able to access internet via subnet
# Auto-assign public IP (Disable)
# Protect accident termination
# Added 3 extra volumes for Mongo data, journal and log in order
# restricted Security group inbound
# Selected right ssh key
#########################################################################################
main() {
  local replset_n=$1
  # 1
  # Install mongo community version
  #　Install mongo, see https://docs.mongodb.com/v3.6/tutorial/install-mongodb-on-amazon/
  # 　Fixway of Issue of http://paste.openstack.org/show/726312/
  #  1 create a tmp security group and elastic IP
  #  2 change EC2 security group to the tmp one without inbound restriction
  #  3 create a tmp elastic IP and associate it to the EC2
  #  4 run yum command
  #  5 detach tmp elastic IP and release it.  detach tmp security group and delete it.
  #  6 assgin only old security group with inbound restrication: only local member IP
  # Best way is
  # install EC2 in the subnet which has a route table where there is an endpoint
  # see https://mylearningsonline.com/aws-services/aws-vpc-endpoints/
  # "VPC Endpoint does not require a public IP address, access over the Internet"
  # 'pl-68a54001 (com.amazonaws.us-west-2.s3)' is a gateway
  # also see https://docs.aws.amazon.com/glue/latest/dg/vpc-endpoints-s3.html
  # " use private IP addresses to access Amazon S3 with no exposure to the public internet"

  cat /etc/passwd | grep mongod
  if [[ $? == 0 && -f /etc/mongod.conf ]]; then
    err "mongo has been installed"
  else
    echo "not installed, now intall:"
    sudo rm /etc/yum.repos.d/mongodb-org-3.4.repo
    echo "[mongodb-org-3.6]
name=MongoDB Repository
baseurl=https://repo.mongodb.org/yum/amazon/2013.03/mongodb-org/3.6/x86_64/
gpgcheck=1
enabled=1
gpgkey=https://www.mongodb.org/static/pgp/server-3.6.asc" | sudo tee /etc/yum.repos.d/mongodb-org-3.6.repo
    sudo yum install -y mongodb-org
  fi
  # verify
  echo "Verify the mongo software installation  =============1==========="
  cat /etc/passwd | grep mongod
  if [[ $? == 0 && -f /etc/mongod.conf ]]; then
    echo "installed "
  else
    err "Install failed"
  fi
  echo "================================================================"
  # ２
  # Storage
  # Issues: The device name is not same as the volume name. see
  # https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ebs-using-volumes.html
  # http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/device_naming.html
  # http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-booting-from-wrong-volume.html

  echo "Storage for replset member. Not for arbitor "
  for d in /data /journal /log; do
    create_d_for_u_with_p $d mongod mongod 755
  done

  declare -i eturn_codes
  for d in /dev/sdb /dev/sdc /dev/sdd; do
    lsblk --f $d | grep ext4
    return_codes=(${PIPESTATUS[*]})
    if [[ "${return_codes[0]}" -eq 0 && "${return_codes[1]}" -ne 0 ]]; then
      sudo mkfs.ext4 $d
    elif [[ "${return_codes[0]}" -ne 0 ]]; then
      err "failed to lsblk"
      exit 1
    fi
  done

  cat /etc/fstab | grep journal
  if [[ $? != 0 ]]; then
    echo '/dev/sdb /data    ext4 defaults,auto,noatime,noexec 0 0
/dev/sdc /journal ext4 defaults,auto,noatime,noexec 0 0
/dev/sdd /log     ext4 defaults,auto,noatime,noexec 0 0' | sudo tee -a /etc/fstab
  else
    err "fstab has been configured"
  fi

  for d in /data /journal /log; do
    mountpoint $d
    if [[ $? != 0 ]]; then
      sudo mount $d
      echo "mounted $d"
    fi
    create_d_for_u_with_p $d mongod mongod 755
  done

  if [[ ! -L /data/journal ]]; then
    sudo ln -s /journal /data/journal
    echo "soft link journal Done"
  else
    echo "soft link journal has already been Done"
    ls -l /data
  fi

  sudo touch /log/mongod.log &&
    sudo chmod 755 /log/mongod.log &&
    sudo chown mongod:mongod /log/mongod.log

  sudo e2label /dev/xvdd /log-mongo &&
    sudo e2label /dev/xvdc /jou-mongo &&
    sudo e2label /dev/xvdb /dat-mongo

  # verify
  echo "Verify the following info  ============2============"
  ls -hl /data
  df -h
  cat /etc/fstab
  lsblk -f /dev/sdb /dev/sdc /dev/sdd
  echo "===================================================="
  # 3
  #　ulimit setting Reference https://access.redhat.com/solutions/199993
  echo '* soft nofile 64000
* hard nofile 64000
* soft nproc 64000
* hard nproc 64000' | sudo tee /etc/security/limits.d/90-mongodb.conf

  # verify
  echo "Verify the ulimit setting    =============3==========="
  cat /etc/security/limits.d/90-mongodb.conf
  echo "======================================================"
  # ４
  #　read ahead settings　see Reference https://www.percona.com/blog/2016/08/12/tuning-linux-for-mongodb/

  sudo blockdev --setra 32 /dev/sdb &&
    sudo blockdev --setra 32 /dev/sdc &&
    sudo blockdev --setra 32 /dev/sdd &&
    if [[ ! -e /etc/udev/rules.d/85-ebs.rules ]]; then
      echo 'ACTION=="add|change", KERNEL=="sdb", ATTR{bdi/read_ahead_kb}="16"' | sudo tee -a /etc/udev/rules.d/85-ebs.rules &&
        echo 'ACTION=="add|change", KERNEL=="sdc", ATTR{bdi/read_ahead_kb}="16"' | sudo tee -a /etc/udev/rules.d/85-ebs.rules &&
        echo 'ACTION=="add|change", KERNEL=="sdd", ATTR{bdi/read_ahead_kb}="16"' | sudo tee -a /etc/udev/rules.d/85-ebs.rules
    fi
  # verify
  echo "Verify the read ahead settings   =============4==========="
  cat /etc/udev/rules.d/85-ebs.rules
  echo "=========================================================="
  # 5
  # alive settings and set hostname
  #update keep alive
  local service=keep_alive_settings_and_set_hostname
  local d=/etc/init.d/$service
  if [[ ! -e $d ]]; then
    echo '#!/bin/sh
### BEGIN INIT INFO
# Provides:          keep_alive_settings_and_set_hostname
# X-Start-Before:    mongod mongodb-mms-automation-agent
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
### END INIT INFO
sudo sysctl -w net.ipv4.tcp_keepalive_time=3000
echo `hostname -A`  | sudo tee -a /etc/hostname && sudo hostname -F /etc/hostname  
' | sudo tee $d
  fi

  sudo chmod 755 $d &&
    sudo chkconfig --add $service

  sudo sysctl -w net.ipv4.tcp_keepalive_time=3000 &&
    echo $(hostname -A) | sudo tee /etc/hostname && sudo hostname -F /etc/hostname

  # verify
  echo "Verify alive settings and set hostname    =============5==========="
  sudo sysctl -a | grep net.ipv4.tcp.keepalive_time
  hostname
  echo "==================================================================="
  # 6
  # Disable Transparent Huge Pages
  service="disable-transparent-hugepages"
  d="/etc/init.d/${service}"
  if [[ ! -e $d ]]; then
    echo '#!/bin/sh
### BEGIN INIT INFO
# Provides:          disable-transparent-hugepages
# Required-Start:    $local_fs
# Required-Stop:
# X-Start-Before:    mongod mongodb-mms-automation-agent
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Disable Linux transparent huge pages
# Description:       Disable Linux transparent huge pages, to improve
#                    database performance.
### END INIT INFO
case $1 in
  start)
    if [ -d /sys/kernel/mm/transparent_hugepage ]; then
      thp_path=/sys/kernel/mm/transparent_hugepage
    elif [ -d /sys/kernel/mm/redhat_transparent_hugepage ]; then
      thp_path=/sys/kernel/mm/redhat_transparent_hugepage
    else
      return 0
    fi

    echo 'never' > ${thp_path}/enabled
    echo 'never' > ${thp_path}/defrag

    unset thp_path
    ;;
esac' | sudo tee $d
  fi
  sudo chmod 755 $d &&
    sudo chkconfig --add $service
  d=/sys/kernel/mm/transparent_hugepage
  echo 'never' | sudo tee ${d}/enabled && echo 'never' | sudo tee ${d}/defrag

  # Verify
  echo "Verify Disable Transparent Huge Pages  =============6==========="
  for f in $d/enabled $d/defrag; do
    cat $f | grep "\[never\]"
    if [ $? == 0 ]; then echo "done"; else "failed "; fi
  done
  echo "================================================================"
  # 7
  # config mongo YAML file; YAML does not support tab characters for indentation: use spaces instead.
  # see https://docs.mongodb.com/v3.6/reference/configuration-options/#yaml-json
  # storage(datd journal log), bingIp, replset
  # from 3.6 the default bingIP is not 0.0.0.0 anymore, need configure explicitly
  # see https://docs.mongodb.com/v3.6/reference/configuration-options/#net.bindIp
  #
  local bt=$(date +%s)
  sudo cp /etc/mongod.conf /etc/mongod.conf.${bt}

  echo '
# where to write logging data.
systemLog:
  destination: file
  logAppend: true
  path: "/log/mongod.log"
# Where and how to store data.
storage:
  dbPath: /data
  journal:
    enabled: true
# how the process runs
processManagement:
  fork: true  # fork and run in background
  pidFilePath: /var/run/mongodb/mongod.pid  # location of pidfile
  timeZoneInfo: /usr/share/zoneinfo
net:
  port: 27017
  bindIp: 0.0.0.0
replication:
  replSetName: "'${replset_n}'"
' | sudo tee /etc/mongod.conf
  # Verify
  echo "Verify mongo YAML file  =============7==========="
  cat /etc/mongod.conf
  echo "================================================="

  # 8
  # start mongod
  sudo service mongod status | grep running
  if [[ $? -eq 0 ]]; then
    echo "running"
  else
    sudo service mongod start
  fi

  # Verify
  echo "Verify start mongod   =============8 the last step==========="
  sudo service mongod status
  sudo netstat -plnt | egrep mongod
  echo "============================================================="
}

if [[ -z "$@" ]]; then
  printUsage
  exit 0
fi
main "$@"
