FROM ubuntu:latest
FROM dockerfile/java:oracle-java8
RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ trusty-pgdg main" > /etc/apt/sources.list.d/pgdg.list
RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
RUN apt-get update && apt-get install -y wget fonts-mplus libxslt1.1 xvfb postgresql-9.4
# RUN wget http://dl.bintray.com/sbt/debian/sbt-0.13.6.deb
# RUN dpkg -i sbt-0.13.6.deb 
# RUN apt-get update
# RUN apt-get install -y sbt
# COPY sentinel/target/lib /opt/bombaysapphire/sentinel/lib
ADD postgresql.conf /etc/postgresql/9.4/main/postgresql.conf
COPY garuda/target/lib   /opt/bombaysapphire/garuda/lib
COPY ruli                /opt/bombaysapphire/ruli
VOLUME [ "/var/lib/postgresql/9.4/main" ]
EXPOSE 8099 5433
CMD /etc/init.d/postgresql start
MAINTAINER Takami Torao <koiroha@gmail.com>
