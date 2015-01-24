FROM ubuntu:latest
MAINTAINER Takami Torao <koiroha@gmail.com>
FROM dockerfile/java:oracle-java8
COPY lib /opt/bombaysapphire/garuda/lib
ADD garuda.sh /opt/bombaysappire/garuda
EXPOSE 8099
CMD /opt/bombaysappire/garuda/garuda.sh
