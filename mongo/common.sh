#!/bin/bash
source ./common_replsets_nodes_ip.sh

readonly FULL_BP="/data/backup"
readonly EC2_BACKUP_F_PATH="/home/ec2-user/backup"
readonly SHELL_F="backup.sh"
declare -ir GREP_NO_FOUND_CODE=1
# Crontab. it is not same as that in ubuntu where its location is
# $ sudo ls -lha  /var/spool/cron/crontabs/<user>
# and it's content can't be updated by command.
readonly EC2_CRON_P="/var/spool/cron"

function run() {
  local command="$1"
  local connect="$2"
  declare -ri run_expected_code=$3
  local _result_var=$4
  #set -x
  local result
  result=$(echo "$command" | ${connect})
  if [ $? -ne "${run_expected_code}" ]; then
    # the last command exit code
    error="\n
     Find: $result.
      Try: echo \"$command\" | ${connect};"
    err "$error"
    return 1
  fi

  if [[ "$_result_var" ]]; then
    eval $_result_var='$result'
  fi
  return 0
  #set +x
}
function rm_backup_cron_job_from_new_primary() {
  local hu=$1
  local hop=$2
  local nu=$3
  local new_pri=$4
  

  local to_new_pri="ssh -t ${hu}@${hop} ssh -o StrictHostKeyChecking=no ${nu}@${new_pri}"
  echo "sudo rm -rf  ${EC2_CRON_P}/${nu}" | ${to_new_pri}
  echo "sudo ls ${EC2_CRON_P}" | ${to_new_pri}
  echo "crontab -l" | ${to_new_pri}
}

function rm_backup_cron_job_from_new_primary() {
  local hu=$1
  local hop=$2
  local nu=$3
  local new_pri=$4

  local to_new_pri="ssh -t ${hu}@${hop} ssh -o StrictHostKeyChecking=no ${nu}@${new_pri}"
  echo "sudo rm -rf  ${EC2_CRON_P}/${nu}" | ${to_new_pri}
  echo "sudo ls ${EC2_CRON_P}" | ${to_new_pri}
  echo "crontab -l" | ${to_new_pri}
}

#############################################################
# Upgrade local mongo instance with binary located under /tmp in an idempotence way.
# It works for upgrading from 3.4->3.6
# Globals:
#   None
# Arguments:
#   binary name. E.g. mongodb-linux-x86_64-amazon-v3.6-latest.tgz
#   binary sha1. E.g. 9fbbaf6d01433e0a8083cf833bb07d2e9a4987f0
#   new_version. E.g. 3.6.8
# See https://www.mongodb.org/dl/linux/x86_64-amazon?_ga=2.107784130.1168728247.1540503645-624720894.1539377059
# Returns:
#   None
############################################################
function upgrade_local_mongo() {
  local binary_name="$1"
  local binary_sha="$2"
  local new_version="$3"

  mongod -version | grep "$new_version"
  if [[ $? -eq 0 ]]; then
    echo -e "\nIt has already been: $new_version"
    exit 0
  fi
  shasum /tmp/${binary_name} | grep ${binary_sha}
  local pip_return_codes=(${PIPESTATUS[*]})
  one_pipe_stop "${pip_return_codes[@]}" "The binary shasum is not right" "Failed to get binary shasum"

  tar -xvzf /tmp/${binary_name} -C /tmp/ --strip-components 1
  stop $? "extract the binary file"

  sudo service mongod stop
  sudo service mongod status | grep "stopped"
  stop $? "stop mongod service"

  sudo cp /tmp/bin/* /usr/bin/
  stop $? "replace old bianry files"

  mongod -version | grep "$new_version"
  stop $? "check upgraded mongod is the expected version $new_version."

  grep "bindIp: 0.0.0.0" /etc/mongod.conf
  if [[ $? -ne 0 ]]; then
    echo "\n backup the configure file under ~/ "
    sudo cp /etc/mongod.conf ~/mongod.conf.backup-"$(date +%s)"
    sudo sed -i '/net:/a \  bindIp: 0.0.0.0' /etc/mongod.conf
    echo -e "\n\n
From 3.6 the configuration need check:
storage:
  journal:
    enabled: true
net:
  bindIp: 0.0.0.0
  \n\n"
    echo "=====start"
    cat /etc/mongod.conf
    echo "=====end"
    cat /etc/mongod.conf | grep "bindIp: 0.0.0.0" -A 3 -B 3
    stop $? "Check: mongod.conf journal and bindIp."
  fi

  sudo service mongod start
  sudo service mongod status | grep "running"
  stop $? "start mongod service"
}

function one_pipe_stop() {
  local input=("$@")
  local return_codes=("${input[@]:0:$#-2}")
  local last_fail="${input[-2]}"
  local first_fail="${input[-1]}"
  if [[ ${return_codes[0]} -eq 0 && ${return_codes[1]} -ne 0 ]]; then
    err "${last_fail}"
    exit 1
  elif [[ ${return_codes[0]} -ne 0 ]]; then
    err "${first_fail}"
    exit 1
  fi
  echo "Pipe commands are all success"
}

function stop() {
  declare -ir code=$1
  local m=$2
  if [[ $code -ne 0 ]]; then
    err "\nFailed: $m"
    exit 1
  else
    echo -e "\nDone: $m"
  fi
}

function is_secondary() {
  echo "TODO(bzu): check a node is secondary or not"
}

# Like spinner
function processing_of() {
  declare -ri PID=$1
  local mess=$2
  local i=1
  local sp="-\|/-"
  printf "$mess"
  echo -n ' '
  while [ -d /proc/$PID ]; do
    sleep 0.25
    printf "\b${sp:i++%${#sp}:1}"
  done
}

function create_d_for_u_with_p() {
  local d=$1
  local u=$2
  local g=$3
  local p=$4
  set -x
  if [[ ! -e "$d" ]]; then
    sudo mkdir -p $d
  fi
  sudo chmod -R $p $d
  sudo chown -R $u:$g $d

  set +x
}

err() {
  echo -e "[$(date +'%Y-%m-%dT%H:%M:%S%z')]: $@" >&2
}

function confirm_or_exit() {
  local ask="$1"
  # As a workaround when `read -p` does not work when run it in remote host
  echo -e "$ask"
  local is_right
  read -p "Right?[Y/n]" is_right
  echo "Answer: [$is_right]"
  if [[ -z "$is_right" || "$is_right" =~ ^([yY][eE][sS]|[yY]).*$ ]]; then
    echo "Continue"
  else
    err "Break"
    exit 1
  fi
}

function dignostic_info() {
  local hu="$1"
  local hop="$2"
  local nu="$3"
  local node="$4"

  echo -e "\n DB version:"
  local js="\"rs.slaveOk(); db.version();\""
  mongo_eval "$js" "${hu}" "${hop}" "${nu}" "${node}"

  echo -e "\n rs config:"
  js="\"rs.slaveOk(); rs.conf();\""
  mongo_eval "$js" "${hu}" "${hop}" "${nu}" "${node}"

  echo -e "\n rs status:"
  js="\"rs.slaveOk(); rs.status();\""
  mongo_eval "$js" "${hu}" "${hop}" "${nu}" "${node}"

  echo -e "\n db.serverStatus().metrics.cursor:"
  js="\"rs.slaveOk(); db.serverStatus().metrics.cursor;\""
  mongo_eval "$js" "${hu}" "${hop}" "${nu}" "${node}"

  echo -e "\n db.serverStatus().metrics.cursor:"
  js="\" rs.slaveOk(); db.serverStatus().wiredTiger.cache; \""
  mongo_eval "$js" "${hu}" "${hop}" "${nu}" "${node}"

}

# zip remote files, fetch to local, and remove it from remote
function zip_d_to_fetch_remove() {
  local to="$1"
  local name="$2"
  local d="$3"
  local hu="$4"
  local hop="$5"
  local nu="$6"
  local node="$7"
  local local_d="$8"
  local_d="${local_d:-$(pwd)}"

  local to_issue_node="ssh -t ${hu}@${hop} ssh -o StrictHostKeyChecking=no ${nu}@${node}"

  echo "sudo zip -19Tvr ${to}/${name} ${d}" | ${to_issue_node}
  scp -o ProxyCommand="ssh -W %h:%p ${hu}@${hop}" ${nu}@${node}:${to}/${name} ${local_d}
  echo "sudo rm -f ${to}/${name}" | ${to_issue_node}
}

function zip_diagnostic_mongodlog() {
  local hu="$1"
  local hop="$2"
  local nu="$3"
  local node="$4"

  local diagnosetic_d="$5"
  local mongodlog_d="$6"
  local zip_d="$7"

  local default_diagnosetic_d="/data/diagnostic.data"
  diagnosetic_d="${diagnosetic_d:-${default_diagnosetic_d}}"

  local default_log_d="/log"
  mongodlog_d="${mongodlog_d:-${default_log_d}}"

  local default_zip_d="/data"
  zip_d="${zip_d:-${default_zip_d}}"

  local diagnosetic_zip_f="diagnostic_data.zip"
  local mongodlog_zip_f="mongodlog.zip"

  zip_d_to_fetch_remove \
    "${zip_d}" "${diagnosetic_zip_f}" "${diagnosetic_d}" "${hu}" "${hop}" "${nu}" "${node}"
  zip_d_to_fetch_remove \
    "${zip_d}" "${mongodlog_zip_f}" "${mongodlog_d}" "${hu}" "${hop}" "${nu}" "${node}"
}

#####################################################################
# last arg `is_arbiter` valid value is 'true' or anything else value
#####################################################################
function add_nodes_to_existing_replset() {
  local input=("$@")
  local nodes=("${input[@]:0:$#-5}")
  local hu=${input[-5]}
  local hop=${input[-4]}
  local nu=${input[-3]}
  local pri=${input[-2]}
  # ${!#}
  local is_arbiter="${@: -1}"

  # Add nodes
  for sec in ${nodes[@]}; do
    local host=$(ssh ${hu}@${hop} ssh -o StrictHostKeyChecking=no ${nu}@${sec} hostname)
    show_replset_status "${hu}" "$hop" "${nu}" "${pri}" | grep "$host"
    local return_codes=(${PIPESTATUS[*]})
    if [[ "${return_codes[0]}" -eq 0 && "${return_codes[1]}" -ne 0 ]]; then
      echo "Now add it"
      local js
      if [[ "$is_arbiter" == "true" ]]; then
        js="\"rs.addArb('$host')\""
      else
        js="\"rs.add('$host') \""
      fi
      mongo_eval "${js}" "${hu}" "${hop}" "${nu}" "${pri}"
    elif [[ "${return_codes[0]}" -ne 0 ]]; then
      err "failed to get current replset status"
      exit 1
    fi
    echo -e "The current node $host has been in the replset\n\n"
  done

  # Check status
  echo -e "\n\nThe current replset status:"
  show_replset_status ${hu} ${hop} ${nu} ${pri}
  echo -e "\n\nThe current replset config:"
  # Check config
  show_replset_config ${hu} ${hop} ${nu} ${pri}
  # DB, collections, documents number.
  echo -e "\n\nThe current replset B, collections, documents number:"
  replset_info_db_coll_docu_number ${nodes[@]} ${hu} $hop ${nu}
}

function mongo_eval() {
  local js="$1"
  local hu="$2"
  local hop="$3"
  local nu="$4"
  local node="$5"
   
  echo "mongo --eval $js" | ssh -t ${hu}@${hop} ssh -o StrictHostKeyChecking=no ${nu}@${node}
    
}

function show_replset_config() {
  local hu=$1
  local hop=$2
  local nu=$3
  local pri=$4
  mongo_eval "\"rs.slaveOk(); rs.config().members.forEach(function(m){
    printjson(m.host + '  ' + m.priority + '  ' + m.arbiterOnly)
    })  \"" "${hu}" "$hop" "${nu}" "${pri}"
}

function show_replset_status() {
  local hu=$1
  local hop=$2
  local nu=$3
  local pri=$4
  mongo_eval "\"rs.slaveOk(); rs.status().members.forEach(function(m){
    printjson(m.name + '  ' + m.stateStr + ' health: ' +m.health)
    })  \"" "${hu}" "$hop" "${nu}" "${pri}"
}

function replset_info_db_coll_docu_number() {
  local input=("$@")
  local hu=${input[-3]}
  local hop=${input[-2]}
  local nu=${input[-1]}
  local nodes=("${input[@]:0:$#-3}")

  for node in ${nodes[@]}; do
    echo "DB, collections, documents number in ${node}  "
    mongo_eval "\"
    rs.slaveOk();
    db.adminCommand('listDatabases').databases.forEach(function(d){
      let mdb = db.getSiblingDB(d.name);
      mdb.getCollectionInfos().forEach(function(c){
        let cc = mdb.getCollection(c.name);
        let stats = cc.stats();
        let storageSize = (stats.storageSize / 1024 / 1024).toFixed(3);
        let size = (stats.size / 1024 / 1024).toFixed(3);
        printjson(mdb + '.' + c.name + ':count ' + cc.count() +',storageSize '+storageSize + 'MB, size ' + size + 'MB'+ ', validate result '+ cc.validate(true).ok);
      });
    });\"" "${hu}" "$hop" "${nu}" "${node}"

  done
}

#######################################################################################################
# Show each replset's expected primary and replset status
# Globals:
# ${ec2_user_name}
# ${sshhop_user_name}
#
# ${dev_both_Ore_hop}
# ${dev_both_Ore_2a_pri}
# ${dev_both_name}
#
# ${pro_swt_Ore_hop}
# ${pro_swt_Ore_pri_2c}
# ${pro_swt_Ore_name}
#
# ${pro_ext_Ore_hop}
# ${pro_ext_Ore_2c_pri}
# ${pro_ext_Ore_name}
#
# ${pro_ext_N_Calif_hop}
# ${pro_ext_N_Calif_1b_pri_new}
# ${pro_ext_N_Calif_name}
#
# Arguments:
#   None
# Returns:
#   None
#######################################################################################################
function each_replsets_primary_and_status() {

  echo -e "\n\n${dev_both_name}: Expected primay is ${dev_both_Ore_2a_pri}. sshhop is ${dev_both_Ore_hop}"
  show_replset_status ${sshhop_user_name} ${dev_both_Ore_hop} ${ec2_user_name} ${dev_both_Ore_2a_pri}

  echo -e "\n\n${pro_swt_Ore_name}: Expected primay is ${pro_swt_Ore_pri_2c}. sshhop is ${pro_swt_Ore_hop}"
  show_replset_status ${sshhop_user_name} ${pro_swt_Ore_hop} ${ec2_user_name} ${pro_swt_Ore_pri_2c}

  echo -e "\n\n${pro_ext_Ore_name}: Expected primay is ${pro_ext_Ore_2c_pri}. sshhop is ${pro_ext_Ore_hop}"
  show_replset_status ${sshhop_user_name} ${pro_ext_Ore_hop} ${ec2_user_name} ${pro_ext_Ore_2c_pri}

  echo -e "\n\n${pro_ext_N_Calif_name}: Expected primay is ${pro_ext_N_Calif_1b_pri_new}. sshhop is ${pro_ext_N_Calif_hop}"
  show_replset_status ${sshhop_user_name} ${pro_ext_N_Calif_hop} ${ec2_user_name} ${pro_ext_N_Calif_1b_pri_new}

  echo "stand alone instance status"
}

function do_business() {
  err "This function is a holder and should be override by caller with really business function"
  exit 1
}

#######################################################################################################
# Check connnection to current node and apply business operation
# Globals:
#   None
# Arguments:
#   ssh hope user
#   ssh hope IP
#   EC2 user
#   EC2 IP
#   replset name
#   Other options input from enduser
# Returns:
#   None
#######################################################################################################
function apply_each_node() {
  local connect="ssh ${1}@${2} ssh -o StrictHostKeyChecking=no ${3}@${4} 2>/dev/null"
  local node="$4"
  local replset_name="$5"
  echo -e "------------------------------\nreplset_name: ${replset_name}\nnode: ${node}\n"
  timeout 5 ${connect} date
  if [[ $? != 0 ]]; then
    err "\n timeout: $connect  \n"
    return
  fi
  # business start
  do_business "$@"
  # business end
}

#######################################################################################################
# Check ssh keys are ready with ssh agent.
# Assume the UNIX-domain socket 'bind_address' is
#   ${HOME}/.ssh/ssh-agent.${HOSTNAME}.socks
#######################################################################################################
function env_check() {
  export SSH_AUTH_SOCK=${HOME}/.ssh/ssh-agent.${HOSTNAME}.sock
  if [ $(ssh-add -l | sed '/^$/d' | wc -l) -lt 3 ]; then
    local mess="$(ssh-add -l) \nTry: ssh-add -l"
    err "$mess"
    echo -e "$mess" | mail -s "==SSH key is not ready" ${mail_receiver}
    exit 1
  fi
}

########################################################################################################
# Apply business to each replset node
# Globals:
#   ${dev_both_Ore_2b} ${dev_both_Ore_2c_new} ${dev_both_Ore_2a_pri}
#   ${dev_single_N_Calif}
#   ${pro_swt_Ore_arbiter} ${pro_swt_Ore_pri_2c} ${pro_swt_Ore_sec_2b} ${pro_swt_Ore_sec_2a}
#   ${pro_ext_Ore_2b} ${pro_ext_Ore_2a} ${pro_ext_Ore_2c_pri}
#   ${pro_ext_N_Calif_1a} ${pro_ext_N_Calif_1a_new} ${pro_ext_N_Calif_1b_pri_new}
# Arguments:
#   None
# Returns:
#   None
#########################################################################################################
function for_each_replst_node() {
  caller 0
  env_check
  for node in ${dev_both_Ore_2b} ${dev_both_Ore_2c_new} ${dev_both_Ore_2a_pri}; do
    apply_each_node "${sshhop_user_name}" "${dev_both_Ore_hop}" "${ec2_user_name}" "${node}" "$dev_both_name" "$@"
  done

  for node in ${dev_single_N_Calif}; do
    apply_each_node "${sshhop_user_name}" "${dev_N_Calif_hop}" "${ec2_user_name}" "${node}" "$dev_single_N_Calif_name" "$@"
  done

  for node in ${pro_swt_n_calif_nodes[@]}; do
    apply_each_node "${sshhop_user_name}" "${pro_swt_n_calif_hop}" "${ec2_user_name}" "${node}" "${pro_swt_rd_single_name}" "$@"
  done

  for node in ${pro_swt_nodes[@]}; do
    apply_each_node "${sshhop_user_name}" "${pro_swt_Ore_hop}" "${ec2_user_name}" "${node}" "$pro_swt_Ore_name" "$@"
  done

  for node in ${pro_ext_Ore_nodes[@]}; do
    apply_each_node "${sshhop_user_name}" "${pro_ext_Ore_hop}" "${ec2_user_name}" "${node}" "$pro_ext_Ore_name" "$@"
  done

  for node in ${pro_ext_N_Calif_1a} ${pro_ext_N_Calif_1a_new} ${pro_ext_N_Calif_1b_pri_new}; do
    apply_each_node "${sshhop_user_name}" "${pro_ext_N_Calif_hop}" "${ec2_user_name}" "${node}" "$pro_ext_N_Calif_name" "$@"
  done
}

function for_2_sec_per_replst() {
  caller 0
  env_check
  # Apply backup to 2 secondary of each replset
  # "Oregon fext-alpha & hardware-a-beta"
  for host in ${dev_both_Ore_2b} ${dev_both_Ore_2c_new}; do
    apply_each_node ${sshhop_user_name} ${dev_both_Ore_hop} ${ec2_user_name} ${host} "${dev_both_name}" "$@"
  done

  # "product - switch - Oregon"
  for host in ${pro_swt_backup_nodes[@]}; do
    apply_each_node ${sshhop_user_name} ${pro_swt_Ore_hop} ${ec2_user_name} ${host} "${pro_swt_Ore_name}" "$@"
  done

  # "product - switch North California RD Mongo standalone instance"
  for host in ${pro_swt_n_calif_backup_nodes[@]}; do
    apply_each_node "${sshhop_user_name}" "${pro_swt_n_calif_hop}" "${ec2_user_name}" "${host}" "${pro_swt_rd_single_name}" "$@"
  done

  # "product - extender - Oregon"
  for host in ${pro_ext_Ore_backup_nodes[@]}; do
    apply_each_node ${sshhop_user_name} ${pro_ext_Ore_hop} ${ec2_user_name} ${host} "${pro_ext_Ore_name}" "$@"
  done

  # "product - extender beta - N. California"
  for host in ${pro_ext_N_Calif_1a} ${pro_ext_N_Calif_1a_new}; do
    apply_each_node ${sshhop_user_name} ${pro_ext_N_Calif_hop} ${ec2_user_name} ${host} "${pro_ext_N_Calif_name}" "$@"
  done
}

function for_master_per_replst() {
  caller 0
  env_check

  for ((i = 0; i < ${#hops[@]}; i++)); do
    apply_each_node "${sshhop_user_name}" "${hops[$i]}" "${ec2_user_name}" "${pris[$i]}" "${names[$i]}" "$@"
  done
}
