#!/bin/sh
cd share
sbt publishLocal
cd ../garuda
sbt collect-jars
cd ..
