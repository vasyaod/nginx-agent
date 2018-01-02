package ru.mobak.nginxagent

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import com.github.mustachejava.DefaultMustacheFactory
import com.spotify.dns.{DnsSrvResolvers, DnsSrvWatchers}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object App extends App with LazyLogging {

  val config = ConfigFactory.load()
  val as = ActorSystem()

  val nginxManager = as.actorOf(Props(new NginxManager(
    config.getString("nginx-agent.nginx-pid-file"),
    config.getString("nginx-agent.reload-command")
  )))

  val configurationGenerator = as.actorOf(Props(new ConfigurationGenerator(
    defaultTemplateUrl = config.getString("nginx-agent.template-url"),
    hashType = config.getString("nginx-agent.hash-type"),
    configPath = config.getString("nginx-agent.config-path"),
    secretKey = config.getString("nginx-agent.secret-key"),
    defaultServer = config.getString("nginx-agent.default-server"),
    defaultPort = config.getInt("nginx-agent.default-port"),
    nginxManager = nginxManager
  )))

  if (config.getString("nginx-agent.resolver") == "dns") {
    logger.info("Dns resolver has been initialized")
    ////
    // Initialize conf generation from DNS records,
    lazy val resolver = DnsSrvResolvers.newBuilder()
      .cachingLookups(true)
      .dnsLookupTimeoutMillis(1000)
      .build()

    lazy val refreshPeriod = config.getInt("nginx-agent.dns.refresh-period")
    lazy val watcher = DnsSrvWatchers.newBuilder(resolver)
      .polling(refreshPeriod, TimeUnit.SECONDS)
      .build()

    ////
    // Run configurator for each service.
    config.getStringList("nginx-agent.services").foreach { serviceName =>
      as.actorOf(Props(new DnsResolver(
        resolver = resolver,
        watcher = watcher,
        serviceName = serviceName,
        domainSuffix = config.getString("nginx-agent.dns.domain-suffix"),
        configurationGenerator = configurationGenerator
      )))
    }

  } else {
    val marathonUrls = config.getString("nginx-agent.marathon.urls").split(",").toList.map(_.trim)
    logger.info("Marathon resolver has been initialized for marathon nodes: {}", marathonUrls.mkString(","))

    as.actorOf(Props(new MarathonResolver(
      marathonUrls = marathonUrls,
      registredServices = config.getStringList("nginx-agent.services").asScala.toList,
      updatePeriod = config.getInt("nginx-agent.marathon.refresh-period"),
      balancerId = config.getString("nginx-agent.marathon.balancer-id"),
      configurationGenerator = configurationGenerator
    )))
  }

  // Set hook for PERM signal.
  def shutdown(): Unit = {
    Await.result(as.terminate(), 1.minutes)

    logger.info("Agent has been terminated")
  }
  Runtime.getRuntime.addShutdownHook(new Thread { override def run(): Unit = shutdown() })
}

