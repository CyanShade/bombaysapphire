#!/bin/bash

dir=`dirname $0`
base=`basename $0`

function help(){
  echo "USAGE: $base [container] {build|run|start|stop|restart|rm|rmi}"
  exit 0
}

container=
cmd=
while [ "$1" != "" ]
do
  case "$1" in
  --help)
    help
    ;;
  *)
    if [ -z "$container" ]
    then
      container="$1"
    elif [ -z "$cmd" ]
    then
      cmd="$1"
    else
      echo "ERROR: unknown option: $1"
      exit 1
    fi
    ;;
  esac
  shift
done

if [ -z "$container" ]
then
  echo "ERROR: container required"
  help
elif [ ! -f "$dir/$container/Dockerfile" ]
then
  echo "ERROR: $container is not defined"
  help
elif [ -z "$cmd" ]
then
  echo "ERROR: command required"
  help
fi

version=1.0
image=torao/bombaysapphire-$container
name=bs-$container

case "$cmd" in
build)
  if [ `sudo docker images | grep $image | wc -l` -eq 0 ]
  then
    sudo docker build -t $image:$version $container
  else
    echo "docker image already exists: $image:$version"
  fi
  ;;
run)
  if [ `sudo docker ps -a | grep $name | wc -l` -eq 0 ]
  then
    case "$container" in
    postgresql)
      database=/var/lib/bombaysapphire/pgsql
      if [ ! -d $database ]
      then
        sudo mkdir -p $database
      fi
      sudo docker run --detach --volume=/var/lib/postgresql/9.4/main:$database --name $name $image:$version
      ;;
    *)
      sudo docker run --detach --name $name $image:$version
      ;;
    esac
  else
    echo "docker container already running: $name"
  fi
  ;;
start)
  sudo docker start $name
  ;;
stop)
  sudo docker stop $name
  ;;
restart)
  sudo docker restart $name
  ;;
rm)
  sudo docker rm $name
  ;;
rmi)
  sudo docker rmi $image:$version
  ;;
esac

