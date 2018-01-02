## Getting started with Marathon

### Step 1. Run a sandbox over Vagrand

Run https://github.com/mesosphere/playa-mesos by vagrand

### Step 2. Set nginx-balancer up

```bash
curl -X POST http://10.141.141.10:8080/v2/apps \
-H 'Content-type: application/json' \
-d @- << EOF
{
  "id": "nginx-balancer",
  "cpus": 0.1,
  "mem": 0,
  "instances": 1,
  "env": {
    "MARATHON_URLS": "10.141.141.10:8080",
    "NGINX_AGENT_RESOLVER": "marathon",
    "BALANCER_ID": "primary"
  },
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "vasyaod/nginx-balancer",
      "network": "HOST",
      "forcePullImage": true
    }
  },
  "upgradeStrategy": {
    "minimumHealthCapacity": 0,
    "maximumOverCapacity": 0
  }
}
EOF
```

### Step 3. Install any custom web service 

```bash
curl -X POST http://10.141.141.10:8080/v2/apps \
-H 'Content-type: application/json' \
-d @- << EOF
{
  "id": "hello-world-service",
  "cpus": 0.1,
  "mem": 0,
  "instances": 1,
  "labels": {
    "BALANCER": "primary",
    "BALANCER_PARAM_SERVER_CONFIG": "TRUE",
    "BALANCER_PARAM_SERVER_NAME": "hello-world-service.f-proj.com"
  },
  "container": {
    "type": "DOCKER",
    "docker": {
      "image": "crccheck/hello-world",
      "network": "BRIDGE",
      "portMappings": [
        {"containerPort": 8000, "hostPort": 0 }
      ],
      "forcePullImage": true
    }
  },
  "upgradeStrategy": {
    "minimumHealthCapacity": 0,
    "maximumOverCapacity": 0
  }
}
EOF
```

### Step 4. Check results out in a browser 

Open **http://hello-world-service.f-proj.com** in a browser. In this case domain name hello-world-service.f-proj.com is 
just alias of 10.141.141.10 and was created for convenient.