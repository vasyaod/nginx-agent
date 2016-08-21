# Nginx Agent

The single goal of the project is transmitting DNS records (SRV type) to nginx configuration. It can be
useful if there is some container manager system with DNS supporting for example Mesos + MesosDNS and you
want use Nginx as load balancer or some gate for access to nodes.

The project was inspired by https://github.com/Xorlev/gatekeeper but the one has quite wide ability since DNS
was used for discovering instead of Apache Zookeeper.

##Building and setup

 * `git clone git@github.com:vasyaod/nginx-agent.git`
 * `mvn install`
 * Create directory where Nginx Agent will put configuration file for Ngix (see property `nginx-agent.config-path`)
   Default value is `/etc/nginx/services-conf` and the directory can be created by `mkdir /etc/nginx/services-conf`
 * The configuration directory should be included in a Nginx config by the directive `include /etc/nginx/services-conf/*.conf`
 * Create some minimal config for Nginx Agent
 * Run the app by `java -Dconfig.file=nginx-agent.conf -cp "./lib/*" ru.mobak.nginxagent.App` where `nginx-agent.conf` is
   our configuration for Nginx Agent.

##Configuration

### Minimal configuration

Minimal configuration is list of services which should be listened.

```javastript
nginx-agent {

  services = ["serice1", "service2"]
}
```

### Default configuration

```javastript
nginx-agent {

  # Path to file that contains PID of Nginx. This option needs for refreshing configuration of Nginx in runtime by
  # sending signal.
  nginx-pid-file="/var/run/nginx.pid"

  # Path to where generated configs are
  config-path = "/etc/nginx/services-conf"

  # Path to a template that will be used for generation of configs in "config-path" directory.
  template-path = "template.mustache"

  # If there is no any node for a service in DNS then default server and port will be set. In one hand nginx can not work
  # with empty upstreams, in another hand it allow to us show some stub page if the service is down.
  default-server = "127.0.0.1"
  default-port = 80

  # Suffix of domain, for example, if we have record _service1._tcp.marathon.mesos that a suffix is "marathon.mesos"
  domain-suffix = "marathon.mesos"

  # Hash algorithm for node ID generation.
  hash-type = "md5"

  services = [ ]
}
```

##License
Copyright 2014 Michael Rose

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the
 License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.