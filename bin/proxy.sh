#!/bin/bash

dir=`dirname $0`/..

sbt package collect-jars
ret=$?
if [ $ret -eq 0 ]
then
  classpath=$dir/target/scala-2.11/bombaysapphire_2.11-1.0.0-SNAPSHOT.jar
  for f in target/lib/*.jar; do classpath=$classpath:$f; done
  sudo java -classpath $classpath org.koiroha.bombaysapphire.ProxyServer $*
fi