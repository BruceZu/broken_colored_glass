#!/bin/sh

# install docker: https://docs.docker.com/install/linux/docker-ce/ubuntu/

DOCKER_VERSION=18.06.3~ce~3-0~ubuntu

echo ">>>> uninstall old docker versions"
sudo apt-get -y remove docker docker-engine

echo ">>>> install docker"
sudo apt-get update
sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg-agent \
    software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
sudo apt-get update
sudo apt-get install -y docker-ce=${DOCKER_VERSION} docker-ce-cli=${DOCKER_VERSION} containerd.io
sudo groupadd docker
sudo usermod -aG docker $(whoami)

# install docker-compose: https://docs.docker.com/compose/install/

echo ">>>> install docker-compose"
curl -L https://github.com/docker/compose/releases/download/1.18.0/docker-compose-$(uname -s)-$(uname -m) >./docker-compose
chmod +x ./docker-compose
sudo mv ./docker-compose /usr/local/bin/docker-compose

#
echo
echo "done. please reboot."
