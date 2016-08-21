# Nginx Agent

The single goal of the project is transmitting DNS records (SRV type) to nginx configuration. It can be
useful if there is some container manager system with DNS supporting for example Mesos + MesosDNS and you
want use Nginx as load balancer or some gate for access to nodes.

The project was inspired by https://github.com/Xorlev/gatekeeper but the one has quite wide ability since DNS
was used for discovering instead of Apache Zookeeper.

##Configuration


##License
Copyright 2014 Michael Rose

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the
 License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 language governing permissions and limitations under the License.