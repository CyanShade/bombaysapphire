FROM ubuntu:latest
MAINTAINER Takami Torao <koiroha@gmail.com>
FROM dockerfile/java:oracle-java8

RUN sudo apt-get install wget
RUN wget http://www.scala-lang.org/files/archive/scala-2.11.4.deb
RUN sudo dpkg -i scala-2.11.4.deb
RUN sudo apt-get update
RUN sudo apt-get install scala

COPY lib              /opt/bombaysapphire/garuda/lib
ADD garuda.sh         /opt/bombaysapphire/garuda/garuda.sh
ADD garuda.properties /opt/bombaysapphire/garuda/garuda.properties
EXPOSE 8099
CMD sh /opt/bombaysapphire/garuda/garuda.sh

