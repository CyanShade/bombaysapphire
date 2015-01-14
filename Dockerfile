FROM ubuntu:latest
FROM postgres:9.4
FROM dockerfile/java:oracle-java8
RUN apt-get update && apt-get install -y wget fonts-mplus libxslt1.1 xvfb postgresql-9.4
RUN wget http://dl.bintray.com/sbt/debian/sbt-0.13.6.deb
RUN dpkg -i sbt-0.13.6.deb 
RUN apt-get update
RUN apt-get install -y sbt
COPY . /opt/bombaysapphire/
RUN cd /opt/bombaysapphire/; sbt package
VOLUME [ "/var/lib/pgsql/data" ]
MAINTAINER Takami Torao <koiroha@gmail.com>
