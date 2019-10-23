#### Assume working with Linux
If working with windows 10. Please refer [How to Install and Use the Linux Bash Shell on Windows 10](https://www.howtogeek.com/249966/how-to-install-and-use-the-linux-bash-shell-on-windows-10/)

# How to use it:

## Install Docker:

### For Mac OS user:

**Docker for Mac** already include Compose, so Mac users do not need to install Compose separately. Docker install instructions for these are here [Get Docker for Mac](https://docs.docker.com/docker-for-mac/install/)

### For Ubuntu user:

```bash
path/to/compPortal/docker$ ./install_docker.sh
```

### For other Linux distributions user:

Please follow the official instructions, e.g. [Get Docker CE for CentOS](https://docs.docker.com/install/linux/docker-ce/centos/)

## Start up PROJ:

```bash
path/to/compPortal$ ./run_with_docker.sh
```
Hints: The first time starting up the PROJ is slow. Because it need download
all PROJ dependencies. These dependencies are stored in the host filesystem
which is managed by Docker. So from the second time, it will be faster to start up PROJ.

Once the PROJ is up a pop window will appear with the login page
    'https://localhost:8444/proj/login'
Login with admin user name: spuser and password: test123
Google-Chrome and Firfox will be tried in turn.

##  Update HTML/CSS/JS file, reload the page to see the effective result.

Note:
1. Does not monitor change under `/project/src/main/java/com/coustomer/projs/autoreg`. If need, please restart docker.

2. Not support reloading changed class due to issue about Tomcat configuration. It can be fixed later and `Jetty` is an option. At present, If need, please restart docker.

3. Limited support of reloading changed configure files under `project/src/main/resources`. If need others we can add more later. Supported configure files as:
   - web.xml
   - log4j2.xml
   - ehcache.xml
   - spring/applicationContext.xml
   - spring/hibernateContext.xml
   - spring/applicationSecurity.xml
   - messages_<language>.properties

## Tips for cleaning dirtied libs cached in host volume.

Delete the dirtied lib. E.g. `schema_evolver`.

For Linux user:
```bash
  $ path=$(docker inspect --type volume compportal_maven_repository -f "{{ .Mountpoint }}") &&  \
  sudo find $path -name schema_evolver -type d -exec rm -rf {} +
```

For Mac OS user:
  Connect to the Docker VM with the command and then login as `root`, then from there find and delete the dirtied lib. refer [Docker for Mac Commands](https://www.bretfisher.com/docker-for-mac-commands-for-getting-into-local-docker-vm/)

```bash
  $screen ~/Library/Containers/com.docker.docker/Data/com.docker.driver.amd64-linux/tty
```

- Delete the volume `compportal_maven_repository` completely when there are more dirtied libs

```bash
  $ docker-compose ps -q | xargs docker rm
  $ docker volume rm  compportal_maven_repository
```
