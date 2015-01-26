#!/bin/sh

database=/var/lib/bombaysapphire/pgsql
if [ ! -d $database ]
then
  sudo mkdir -p $database
fi

image=torao/bombaysapphire-postgresql
if [ `sudo docker images | grep $image | wc -l` -eq 0 ]
then
  sudo docker build -t $image:1.0 postgresql
fi
sudo docker run --detach --volume=/var/lib/postgresql/9.4/main:$database --name bs-pgsql $image:1.0

