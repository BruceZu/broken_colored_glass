Assue working with Linux, if you are working with windows 10.
Please refer "How to Install and Use the Linux Bash Shell on Windows 10"
https://www.howtogeek.com/249966/how-to-install-and-use-the-linux-bash-shell-on-windows-10/

1> Install Docker and Docker Compose: run the shell 'install_docker.sh'

2> Start up:
     ./run_with_docker.sh
    A pop window will appear with the login page
    Google-Chrome and Firfox will be tried in turn.

3  Update HTML/CSS/JS, reload the page to see the effective result.

Note:

- Not support reloading changed class due to issue about Tomcat configuration.
  It can be fixed later and Jetty is an option. At present, If need, please restart docker.

- Limited support of reloading changed configure files under 'project/src/main/resources'.
  If need others we can add more later.
