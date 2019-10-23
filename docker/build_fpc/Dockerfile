FROM openjdk:8-jdk
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get install -y  --no-install-recommends --fix-missing \
    apt-utils \
    maven \
    procps \
    gawk \
    python
CMD tail -f /dev/null