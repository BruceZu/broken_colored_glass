#!/bin/bash
source ./common_replsets_nodes_ip.sh
readonly FULL_BP="/data/backup"

function run() {
  local command=$1
  local connenect=$2
  declare -ri run_expected_code=$3
  local _result_var=$4
  # set -x
  local result
  result=$(echo "$command" | ${connect})
  if [ $? -ne "${run_expected_code}" ]; then
    error="\n\nFind: $result.\n Try: echo \"$command\" | ${connect};"
    echo -e "$error" >&2
    return 1
  fi

  if [[ "$_result_var" ]]; then
    eval $_result_var='$result'
  fi
  return 0
  #  set +x
}

function stop() {
  declare -ir code=$1
  local m=$2
  if [[ $code -ne 0 ]]; then
    echo -e "\nFailed: $m" >&2
    exit 1
  else
    echo -e "\nDone: $m"
  fi
}

function create_d_for_u_with_p() {
  local d=$1
  local u=$2
  local g=$3
  local p=$4

  if [[ ! -e "$d" ]]; then
    sudo mkdir -p $d
  fi
  sudo chmod -R $p $d
  sudo chown -R $u:$g $d
}

err() {
  echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')]: $@" >&2
}

function confirm_or_exit() {
  local ask=$1
  # As a workaround when `read -p` does not work when run it in remote host
  echo -e "$ask"
  read -p "Right?[Y/n]" is_right
  echo "Answer: [$is_right]"
  if [[ -z "$is_right" || "$is_right" =~ ^([yY][eE][sS]|[yY]).*$ ]]; then
    echo "Continue"
  else
    err "Break"
    exit 1
  fi
}

function show_replset_config() {
  local hu=$1
  local hop=$2
  local nu=$3
  local pri=$4
  echo "mongo --eval \"
  rs.config().members.forEach(function(m){
    printjson(m.host + '  ' + m.priority + '  ' + m.arbiterOnly)
    })  \"" | ssh -t ${hu}@$hop ssh ${nu}@$pri
}

function show_replset_status() {
  local hu=$1
  local hop=$2
  local nu=$3
  local pri=$4
  echo "mongo --eval \"
  rs.status().members.forEach(function(m){
    printjson(m.name + '  ' + m.stateStr + ' health: ' +m.health)
    })  \"" | ssh -t ${hu}@$hop ssh ${nu}@$pri
}

function replset_info_db_coll_docu_number() {
  local input=("$@")
  local hu=${input[-3]}
  local hop=${input[-2]}
  local nu=${input[-1]}
  local nodes=("${input[@]:0:$#-3}")

  for node in ${nodes[@]}; do
    echo "DB, collections, documents number in ${node}  "
    echo "mongo --eval \"
    rs.slaveOk();
    db.adminCommand('listDatabases').databases.forEach(function(d){
      let mdb = db.getSiblingDB(d.name);
      mdb.getCollectionInfos().forEach(function(c){
        let cc = mdb.getCollection(c.name);
        printjson(  mdb + '.' + c.name + ': ' + cc.count() + ' validate result: '+ cc.validate(true).ok);
      });
    });\"" | ssh -t ${hu}@$hop ssh ${nu}@${node}
    echo "-----------------------------"
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
# ${pro_swt_Ore_pri_2a}
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
  echo "${dev_both_name}: Expected primay is ${dev_both_Ore_2a_pri}"
  show_replset_status ${sshhop_user_name} ${dev_both_Ore_hop} ${ec2_user_name} ${dev_both_Ore_2a_pri}

  echo "${pro_swt_Ore_name}: Expected primay is ${pro_swt_Ore_pri_2a}"
  show_replset_status ${sshhop_user_name} ${pro_swt_Ore_hop} ${ec2_user_name} ${pro_swt_Ore_pri_2a}

  echo "${pro_ext_Ore_name}: Expected primay is ${pro_ext_Ore_2c_pri}"
  show_replset_status ${sshhop_user_name} ${pro_ext_Ore_hop} ${ec2_user_name} ${pro_ext_Ore_2c_pri}

  echo "${pro_ext_N_Calif_name}: Expected primay is ${pro_ext_N_Calif_1b_pri_new}"
  show_replset_status ${sshhop_user_name} ${pro_ext_N_Calif_hop} ${ec2_user_name} ${pro_ext_N_Calif_1b_pri_new}
}

function do_business() {
  echo "This function is a holder and should be override by caller with really business function" >&2
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
  echo -e "------------------------------\n${replset_name}\n${node}:\n"
  timeout 5 ${connect} date
  if [[ $? != 0 ]]; then
    echo -e "\n timeout: $connect  \n" >&2
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
    echo -e "$mess" >&2
    echo -e "$mess" | mail -s "==SSH key is not ready" ${mail_receiver}
    exit 1
  fi
}

########################################################################################################
# Apply business to each replset node
# Globals:
#   ${dev_both_Ore_2b} ${dev_both_Ore_2c_new} ${dev_both_Ore_2a_pri}
#   ${dev_single_N_Calif}
#   ${pro_swt_Ore_arbiter} ${pro_swt_Ore_sec_2c} ${pro_swt_Ore_sec_2b} ${pro_swt_Ore_pri_2a}
#   ${pro_ext_Ore_arbiter} ${pro_ext_Ore_2b} ${pro_ext_Ore_2a} ${pro_ext_Ore_2c_pri}
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

  for node in ${pro_swt_Ore_arbiter} ${pro_swt_Ore_sec_2c} ${pro_swt_Ore_sec_2b} ${pro_swt_Ore_pri_2a}; do
    apply_each_node "${sshhop_user_name}" "${pro_swt_Ore_hop}" "${ec2_user_name}" "${node}" "$pro_swt_Ore_name" "$@"
  done

  for node in ${pro_ext_Ore_arbiter} ${pro_ext_Ore_2b} ${pro_ext_Ore_2a} ${pro_ext_Ore_2c_pri}; do
    apply_each_node "${sshhop_user_name}" "${pro_ext_Ore_hop}" "${ec2_user_name}" "${node}" "$pro_ext_Ore_name" "$@"
  done

  for node in ${pro_ext_N_Calif_1a} ${pro_ext_N_Calif_1a_new} ${pro_ext_N_Calif_1b_pri_new}; do
    apply_each_node "${sshhop_user_name}" "${pro_ext_N_Calif_hop}" "${ec2_user_name}" "${node}" "$pro_ext_N_Calif_name"
  done
}

function for_2_sec_per_replst() {
  caller 0
  env_check
  # Apply backup to 2 secondary of each replset
  # "Oregon fext-alpha & fsw-beta"
  for host in ${dev_both_Ore_2b} ${dev_both_Ore_2c_new}; do
    apply_each_node ${sshhop_user_name} ${dev_both_Ore_hop} ${ec2_user_name} ${host} "${dev_both_name}" "$@"
  done

  # "product - switch - Oregon"
  for host in ${pro_swt_Ore_sec_2c} ${pro_swt_Ore_sec_2b}; do
    apply_each_node ${sshhop_user_name} ${pro_swt_Ore_hop} ${ec2_user_name} ${host} "${pro_swt_Ore_name}" "$@"
  done

  # "product - extender - Oregon"
  for host in ${pro_ext_Ore_2b} ${pro_ext_Ore_2a}; do
    apply_each_node ${sshhop_user_name} ${pro_ext_Ore_hop} ${ec2_user_name} ${host} "${pro_ext_Ore_name}" "$@"
  done

  # "product - extender beta - N. California"
  for host in ${pro_ext_N_Calif_1a} ${pro_ext_N_Calif_1a_new}; do
    apply_each_node ${sshhop_user_name} ${pro_ext_N_Calif_hop} ${ec2_user_name} ${host} "${pro_ext_N_Calif_name}" "$@"
  done
}
