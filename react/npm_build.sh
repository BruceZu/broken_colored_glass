#!/usr/bin/env bash
set -eu
cur=$(pwd)
cd ${0%/*}/../

ERROR_COLOR='\033[0;31m'
INFO_COLOR='\033[0m' # No Color
ERROR_MSG="${ERROR_COLOR}ERROR${INFO_COLOR}"
INFO_MSG="${INFO_COLOR}INFO"

# Enable nvm
export NVM_DIR="$HOME/.nvm"
# https://www.tldp.org/LDP/abs/html/fto.html -s : test file is not zero size
if [[ -f "$NVM_DIR/nvm.sh" ]] && [[ -s "$NVM_DIR/nvm.sh" ]]; then
  # if ~/.nvm/nvm.sh exists and is regular file, source it, loads nvm
  source "$NVM_DIR/nvm.sh"
fi
if [[ -f "$NVM_DIR/bash_completion" ]] && [[ -s "$NVM_DIR/bash_completion" ]]; then
  # if ~/.nvm/nvm.sh exists and is regular file, source it, loads nvm
  source "$NVM_DIR/bash_completion"
fi

if [[ $# -eq 0 ]]; then
  npm run
  cd $cur
  exit
fi

echo "$@"
echo "======= config ========="
build_options=()
for i in $@; do
  if [[ "$i" == "config:use-mirror" ]]; then
    echo "there is config mirror option $i"
    # default not use company’s npm mirror registry as project default registrys
    echo "use company’s npm mirror registry as project default registrys"
    npm config set registry https://dops-nexus.compnet-us.com:8443/repository/fins-npm-proxy/
  else
    build_options+=("$i")
  fi
done
echo "other build parameters: ${build_options[@]}"

echo "======= install ========="
for i in ${build_options[@]}; do
  if [[ $i =~ ^build.* ]]; then
    echo "there is build, so install, but only once"
    npm install --prefer-offline --unsafe-perm
    # npm audit fix
    break
  fi
done
echo "======== run ========"
for i in ${build_options[@]}; do
  echo "start: npm run $i"
  time npm run $i
done

cd $cur
