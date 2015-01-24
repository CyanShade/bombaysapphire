#!/bin/bash

base=`dirname $0`
classpath=
for f in $base/lib/*.jar; do classpath=$classpath:$f; done
scala -classpath $classpath org.koiroha.bombaysapphire.garuda.Garuda $base/garuda.properties

