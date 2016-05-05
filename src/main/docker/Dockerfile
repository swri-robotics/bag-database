FROM tomcat:8-jre8
EXPOSE 8080
RUN rm -rf /usr/local/tomcat/webapps/
ADD *.war /usr/local/tomcat/webapps/ROOT.war
ADD entrypoint.sh /
ADD server.xml /usr/local/tomcat/conf/server.xml
VOLUME ["/bags", "/root/.ros-bag-database/indexes", "/usr/local/tomcat/logs"]
CMD ["/entrypoint.sh"]
