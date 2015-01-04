FROM postgres:9.4
FROM dockerfile/java:oracle-java8
RUN apt-get install -y wget
RUN wget http://dl.bintray.com/sbt/debian/sbt-0.13.6.deb
RUN dpkg -i sbt-0.13.6.deb 
RUN apt-get update
RUN apt-get install -y sbt
COPY . /opt/bombaysapphire/
RUN cd /opt/bombaysapphire/; sbt package
VOLUME [ "/var/lib/pgsql/data" ]
MAINTAINER Takami Torao <koiroha@gmail.com>
