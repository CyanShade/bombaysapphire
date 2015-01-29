#!/bin/sh

image=torao/bombaysapphire-garuda
if [ `sudo docker images | grep $image | wc -l` -eq 0 ]
then
  sudo docker build -t $image:1.0 garuda
fi
sudo docker run --detach --name bs-garuda $image:1.0

