#!/bin/bash
set -eu
ERROR_COLOR='\033[0;31m'
WARNING_COLOR='\033[33m'
INFO_COLOR='\033[0m' # No Color
ERROR_MSG="${ERROR_COLOR}ERROR${INFO_COLOR}"
WARNING_MSG="${WARNING_COLOR}WARNING${INFO_COLOR}"
INFO_MSG="${INFO_COLOR}INFO"
REQUIRED_NODE_VERSION="10.19.0"
REQUIRED_NPM_VERSION="6.14.2"

# https://stackoverflow.com/questions/4023830/how-to-compare-two-strings-in-dot-separated-version-format-in-bash
# compare version
#
# first param: current version
# second param: expected version
# third param: compared result:
#     0 : =
#     1 : >
#    -1 : <
version_compare() {
    local current="$1"
    local expected="$2"
    local _result="$3"
    if [[ $current == $expected ]]; then
        eval $_result=0
        return
    fi
    local IFS=.
    local i ver1=($current) ver2=($expected)
    # fill empty fields in ver1 with zeros
    for ((i = ${#ver1[@]}; i < ${#ver2[@]}; i++)); do
        ver1[i]=0
    done
    for ((i = 0; i < ${#ver1[@]}; i++)); do
        if [[ -z ${ver2[i]} ]]; then
            # fill empty fields in ver2 with zeros
            ver2[i]=0 # means "="
        fi
        if ((10#${ver1[i]} > 10#${ver2[i]})); then
            eval $_result=1
            return
        fi
        if ((10#${ver1[i]} < 10#${ver2[i]})); then
            eval $_result=-1
            return
        fi
    done
}

if [[ -z "$(command -v nvm)" ]]; then
  if [[ ! -s "$HOME/.nvm/nvm.sh" ]] && [[ ! -s "$HOME/.nvm/bash_completion" ]]; then
    # build environment is expected to have access to network, so it is okay to wget!
    if [[ -n "$(command -v wget)" ]]; then
      wget --no-check-certificate -qO- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
    elif [[ -n "$(command -v curl)" ]]; then
      echo -e "$(basename $0) ${INFO_MSG} : wget command is not installed, going to use curl to fetch nvm script."
      curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
    else
      echo -e "$(basename $0) ${ERROR_MSG} : Neither wget nor curl is installed on this instance. Going to abort execution."
      exit 5
    fi
  fi
  # following two commands guarantee that nvm is available to this bash session
  [[ -s "$HOME/.nvm/nvm.sh" ]] && source "$HOME/.nvm/nvm.sh"                   # This loads nvm
  [[ -s "$HOME/.nvm/bash_completion" ]] && source "$HOME/.nvm/bash_completion" # This loads nvm bash_completion
fi

# Check if Node.js & npm installed
if [[ -z "$(command -v node)" || -z "$(command -v npm)" ]]; then
  echo -e "$(basename $0) ${INFO_MSG} : Node.js & npm not found"
  echo -e "$(basename $0) ${INFO_MSG} : Install Node.js & npm now, going to execute: nvm install ${REQUIRED_NODE_VERSION}"
  nvm install ${REQUIRED_NODE_VERSION}
else
  node_version="$(node -v | sed 's/v//g')"
  echo -e "$(basename $0) ${INFO_MSG} : node.js version is ${node_version}"

  version_compare ${node_version} ${REQUIRED_NODE_VERSION} node_version_compared_result
  if [[ $node_version_compared_result -eq -1 ]]; then
    echo -e "$(basename $0) ${WARNING_MSG} : Node.js version is smaller than required version: ${REQUIRED_NODE_VERSION}."
    echo -e "$(basename $0) ${WARNING_MSG} : Going to install required version."
    nvm install ${REQUIRED_NODE_VERSION}
    nvm use ${REQUIRED_NODE_VERSION}
    nvm alias default ${REQUIRED_NODE_VERSION}
  fi

  npm_version="$(npm -v)"
  echo -e "$(basename $0) ${INFO_MSG} : npm version is ${npm_version}"
  version_compare ${npm_version} ${REQUIRED_NPM_VERSION} npm_version_compared_result
  if [[ $npm_version_compared_result -eq -1 ]]; then
    echo -e "$(basename $0) ${ERROR_MSG} : npm version is smaller than required version: ${REQUIRED_NPM_VERSION}."
    echo -e "$(basename $0) ${ERROR_MSG} : upgrading NPM to required version."
    npm install -g npm@${REQUIRED_NPM_VERSION}
  fi
  echo -e "$(basename $0) ${INFO_MSG} : node and npm are found in PATH, and their versions are satisfying."
fi
