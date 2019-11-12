FROM tomcat:9.0.22-jdk8-openjdk
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && apt-get install -qq  --no-install-recommends \
    apt-utils \
    maven \
    procps \
    gawk \
    python \
    inotify-tools \
    inotify-hookable \
    lynx \
    apache2
COPY ./docker/apache2_httpd/etc/apache2/apache2.conf /etc/apache2/apache2.conf
COPY ./docker/apache2_httpd/etc/apache2/sites-available/default-ssl.conf /etc/apache2/sites-available/default-ssl.conf
COPY ./docker/apache2_httpd/etc/apache2/mods-available/mpm_event.conf /etc/apache2/mods-available/mpm_event.conf
RUN a2enmod ssl && a2enmod proxy && a2enmod proxy_http && a2enmod  headers && a2enmod proxy_wstunnel && a2enmod proxy_connect && a2ensite default-ssl.conf

COPY ./docker/maven/toolchains.xml /root/.m2/
COPY ./docker/tomcat/conf/server.xml $CATALINA_HOME/conf/server.xml
COPY ./docker/tomcat/conf/tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
COPY ./docker/tomcat/conf/tomcat.keystore $CATALINA_HOME/conf/tomcat.keystore
COPY ./docker/entrypoint/entry.sh /entrypoint/entry.sh
COPY ./docker/tomcat/webapps /tomcat/webapps
COPY ./docker/entrypoint/packing.sh /entrypoint/packing.sh
CMD ["/entrypoint/entry.sh"]
