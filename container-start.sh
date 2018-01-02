#!/bin/bash

cd /nginx-agent
nohup java -cp "./lib/*" ru.mobak.nginxagent.App &

# Run NGINX after agent
exec nginx -g 'daemon off;'
