# Nginx Agent

Since version 2.0 nginx-agent began support Marathon API.

In case of using MesosDNS the project is transmitting DNS records (SRV type) to nginx configuration. It can be
useful if there is some container manager system with DNS supporting for example Mesos + MesosDNS and you
want use Nginx as load balancer or some gate for access to nodes.

In another hand, Marathon API can be used as a service discovery. But also this approach has some advantage we can 
configure NGINX directly from marathon json.  

The project was inspired by https://github.com/Xorlev/gatekeeper but the one has quite wide ability since DNS
was used for discovering instead of Apache Zookeeper.

## Getting started

 1. Run https://github.com/mesosphere/playa-mesos by vagrand
 2. Run load balancer by   
 'curl -X POST http://10.141.141.10:8080/v2/apps -d @nginx-balancer-marathon.json -H "Content-type: application/json"'
 3. Run test service with given name "hello-world-service"   
 'curl -X POST http://10.141.141.10:8080/v2/apps -d @hello-world-service-marathon.json -H "Content-type: application/json"'
 4. Check this out: http://hello-world-service.f-proj.com . In this case domain name hello-world-service.f-proj.com is 
 just alias of 10.141.141.10

## Building and setup

 * `git clone git@github.com:vasyaod/nginx-agent.git`
 * `mvn install`
 * Create directory where Nginx Agent will put configuration file for Ngix (see property `nginx-agent.config-path`)
   Default value is `/etc/nginx/services-conf` and the directory can be created by `mkdir /etc/nginx/services-conf`
 * The configuration directory should be included in a Nginx config by the directive `include /etc/nginx/services-conf/*.conf`
 * Create some minimal config for Nginx Agent
 * Run the app by `java -Dconfig.file=nginx-agent.conf -cp "target/lib/*" ru.mobak.nginxagent.App` where `nginx-agent.conf` is
   our configuration for Nginx Agent.

## Building Docker image for Marathon API

 * `docker build -t vasyaod/nginx-balancer .`
 
## Principal

Assume there is some service (in this example the service is called as `battle-dev`) with following configuration of nodes:

```bash
[nginx-agent]$ nslookup -type=SRV _battle-dev._tcp.marathon.mesos
Server:         192.168.100.1
Address:        192.168.100.1#53

_battle-dev._tcp.marathon.mesos service = 0 0 31585 battle-dev-atfbu-s8.marathon.slave.mesos.
_battle-dev._tcp.marathon.mesos service = 0 0 31934 battle-dev-cjq9x-s9.marathon.slave.mesos.
_battle-dev._tcp.marathon.mesos service = 0 0 31270 battle-dev-dy91w-s8.marathon.slave.mesos.
```
Nginx Agent should create following config file:

```bash
[services-conf]# cat /etc/nginx/services-conf/battle-dev.conf
upstream battle-dev {
    server 192.168.100.11:31934;
    server 192.168.100.5:31270;
    server 192.168.100.12:31585;
}

map $args $battle_dev_host {
    ~(.*)nodeId=78eeabce9bdf233fcb6e8b69bfc438a6(.*) 192.168.100.11:31934;
    ~(.*)nodeId=611958bd8b7c318ffb8aa744fca9b2df(.*) 192.168.100.5:31270;
    ~(.*)nodeId=b59102f182b8d9a90cf64d8952ce3a3a(.*) 192.168.100.12:31585;
}
```
That is, how you can see, two objects have been created:
 * upstream `battle-dev`
 * and map for variable `$battle_dev_host`

## Nginx Agent Configuration

### Minimal configuration

### Minimal configuration for MesosDNS

Minimal configuration for Mesos DNS is list of services which should be listened.  Please notice that in case of DNS 
we should obligatory set up list of all services which we want to monitor.

```
nginx-agent {

  resolver = "dns"
  
  services = ["serice1", "service2"]
}
```

### Minimal configuration for Marathon API

```
nginx-agent {

  resolver = "marathon"

  marathon {
    # List of marathon servers
    urls = ["127.0.0.1:8080"]
  }
  
  # In marathon case this parameter is optional since we can mark service from marathon json.
  services = ["serice1", "service2"]
}
```


### Default configuration

```
nginx-agent {

  # Command which refreshs configuration to Nginx.
  # There are two variants:
  #  - nginx -s reload
  #  - kill -HUP {PID}
  reload-command="nginx -s reload"

  # Path to file that contains PID of Nginx. This option needs for refreshing configuration of Nginx in runtime by
  # sending signal.
  nginx-pid-file="/var/run/nginx.pid"

  # Path to where generated configs are
  config-path = "/etc/nginx/services-conf"

  # URL to a template that will be used for generation of configs in "config-path" directory.
  #
  # Examples of urls:
  #  - "file:example-template.mustache" - template in file system
  #  - "https://raw.githubusercontent.com/vasyaod/nginx-agent/master/example-template.mustache" - template in file system
  template-url = "default-template.mustache"

  # If there is no any node for a service in DNS then default server and port will be set. In one hand nginx can not work
  # with empty upstreams, in another hand it allow to us show some stub page if the service is down.
  default-server = "127.0.0.1"
  default-port = 80

  # Hash algorithm for node ID generation. Two type of hash function is supported:
  #  * "java" - standart java hash code
  #  * "md5"
  hash-type = "md5"

  # Secret key which is necessary for hash generation of nodeId.
  secret-key = ""

  # Resolve method:
  # - marathon
  # - dns
  resolver = "dns"

  dns {
    # Suffix of domain, for example, if we have record _service1._tcp.marathon.mesos that a suffix is "marathon.mesos"
    domain-suffix = "marathon.mesos"

    # Period (seconds) between requests to a DNS server.
    refresh-period = 5
  }

  # Configuration for marathon resolver
  marathon = {
    # List of marathon nodes.
    urls = "marathon.mesos:8080,127.0.0.1:8080"

    # Period (seconds) between requests to marathon API.
    refresh-period = 5
    
    # Balancer ID.
    balancer-id = "primary"    
  }

  services = [ ]
}
```

## Nginx Configuration

### Load Balancing

For balancing of load to nodes upstreams can be used which is contained within `/etc/nginx/services-conf/*.conf`

```
server {
    location / {
        proxy_pass         http://battle-dev;
    }
}
```

### Routing to single node by nodeId

There is a approach to have access to a single node by a **nodeId** using special URL parameter _nodeId_
(for example: `http://battle-dev.domain.com?nodeId=b59102f182b8d9a90cf64d8952ce3a3a`)

Parameter **nodeId** is hash function (default: md5) by string concatenation: _"192.168.100.12" +
"31934" + "some-secret-key"_. Default secret key is empty see property `nginx-agent.secret-key`

```
server {
    server_name   battle-dev.domain.com;

    location / {
        proxy_pass         http://$battle_dev_host;
    }
}
```
**Caveat:** if we going to use "access to concrete node", please, change secret-key to our value otherwise it will be possible
            to research our inner-network structure by busting of typical ip addresses.

## Default template

Mustache template is used for creation of Nginx configuration files. Default template is contained inside of the jar,
it is possible to change a path to the file by property `nginx-agent.template-path` but usual there is no necessary
to edit this:

```
upstream {{SERVICE_NAME}} {
{{#NODES}}
    server {{HOST}}:{{PORT}};
{{/NODES}}
}

map $args ${{SERVICE_NAME_UNDERSCORE}}_host {
{{#NODES}}
    ~(.*)nodeId={{NODE_ID}}(.*) {{HOST}}:{{PORT}};
{{/NODES}}
}

{{#SERVER_CONFIG}}
# This part of config is relevant only for Marathon API resolver.
server {
    listen 0.0.0.0:80;
    server_name {{SERVER_NAME}};

    location / {
        proxy_pass http://{{SERVICE_NAME}};
    }
}
{{/SERVER_CONFIG}}
```
## Contributors

 * Vasiliy Vazhesov (vasiliy.vazhesov@gmail.com)

## License

The MIT License

Copyright (c) 2016-present Vasiliy Vazhesov (vasiliy.vazhesov@gmail.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.