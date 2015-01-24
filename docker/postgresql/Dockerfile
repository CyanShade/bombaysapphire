$ http://docs.docker.com/examples/postgresql_service/
FROM ubuntu:latest
MAINTAINER Takami Torao <koiroha@gmail.com>

RUN apt-get install -y wget
RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ trusty-pgdg main" > /etc/apt/sources.list.d/pgdg.list
RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
RUN apt-get update && apt-get install -y postgresql-9.4 postgresql-client-9.4

#RUN chown postgres:postgres /etc/postgresql/9.4/main/postgresql.conf
# Run the rest of the commands as the ``postgres`` user created by the ``postgres-9.4`` package when it was ``apt-get installed``
USER postgres

#ADD postgresql.conf /etc/postgresql/9.4/main/postgresql.conf
ADD postgresql.sql .
#RUN ls -laF /etc/postgresql/9.4/main/

RUN /etc/init.d/postgresql start &&\
    psql --command "alter role postgres with password 'postgres';" &&\
    createdb -O postgres bombaysapphire &&\
    psql --dbname=bombaysapphire --file=postgresql.sql

# Adjust PostgreSQL configuration so that remote connections to the
# database are possible. 
RUN echo "host all  all    0.0.0.0/0  md5" >> /etc/postgresql/9.4/main/pg_hba.conf

# And add ``listen_addresses`` to ``/etc/postgresql/9.4/main/postgresql.conf``
RUN echo "listen_addresses='*'" >> /etc/postgresql/9.4/main/postgresql.conf

# Expose the PostgreSQL port
EXPOSE 5432

# Add VOLUMEs to allow backup of config, logs and databases
VOLUME  ["/etc/postgresql", "/var/log/postgresql", "/var/lib/postgresql"]

# Set the default command to run when starting the container
CMD ["/usr/lib/postgresql/9.4/bin/postgres", "-D", "/var/lib/postgresql/9.4/main", "-c", "config_file=/etc/postgresql/9.4/main/postgresql.conf"]

