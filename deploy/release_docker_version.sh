#!/bin/bash
function usage() {
    echo -e "usage: $0 [[-f | --force_build] | [-b | --only_build] | [-h | --help]]\n
default is run \n
-f | --force_build \n
    build then run \n
-b | --only_build without cache \n
    only build without run \n
"
}
YAML=docker-compose-release-proj.yml
## main
while [ "$1" != "" ]; do
    case $1 in
    -b | --only_build)
        echo "build images without cache"
        docker-compose -f ./"${YAML}" build --no-cache
        exit
        ;;
    -f | --force_build)
        echo "build images and start containers"
        docker-compose build --force-rm
        docker-compose -f ./"${YAML}" up --build
        exit
        ;;
    -h | --help)
        usage
        exit
        ;;
    *)
        usage
        exit 1
        ;;
    esac

    shift
done

echo "start containers"
docker-compose -f ./"${YAML}" up
