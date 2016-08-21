package ru.mobak.nginxagent

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import com.github.mustachejava.DefaultMustacheFactory
import com.spotify.dns.{DnsSrvResolvers, DnsSrvWatchers}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.filefilter.WildcardFileFilter

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

object App extends App with LazyLogging {

  val config = ConfigFactory.load()
  val as = ActorSystem()

  val nginxManager = as.actorOf(Props(new NginxManager(
    App.config.getString("nginx-agent.nginx-pid-file")
  )))

  ////
  // Initialize conf generation from DNS records,
  val resolver = DnsSrvResolvers.newBuilder()
    .cachingLookups(true)
    .dnsLookupTimeoutMillis(1000)
    .build()

  val watcher = DnsSrvWatchers.newBuilder(resolver)
    .polling(1, TimeUnit.SECONDS)
    .build()

  ////
  // Remove our old nginx-configs if of cause there are it.
  val dir = new File(config.getString("nginx-agent.config-path"))
  val fileFilter: java.io.FilenameFilter = new WildcardFileFilter("*.conf")
  val files = dir.listFiles(fileFilter)
  files.foreach(f =>
    if (!f.delete())
      logger.error(s"File ${f.getPath} can not be removed.")
    else
      logger.info(s"Config file ${f.getPath} was removed.")
  )

  ////
  // Run configurator for each service.
  config.getStringList("nginx-agent.services").foreach { serviceName =>
    as.actorOf(Props(new DnsConfigurationGenerator(
      resolver,
      watcher,
      serviceName,
      // Инициализуруем шаблонизатор.
      new DefaultMustacheFactory().compile(
        // Путь к файлу с шаблонами берем из настроек.
        config.getString("nginx-agent.template-path")
      ),
      config.getString("nginx-agent.domain-suffix"),
      config.getString("nginx-agent.hash-type"),
      config.getString("nginx-agent.config-path"),
      secretKey = config.getString("nginx-agent.secret-key"),
      defaultServer = config.getString("nginx-agent.default-server"),
      defaultPort = config.getInt("nginx-agent.default-port"),
      nginxManager = nginxManager
    )))
  }

  // Set hook for PERM signal.
  def shutdown(): Unit = {
    Await.result(as.terminate(), 2.minutes)

    watcher.close()

    logger.info("Agent has been terminated")
    println("Agent has been terminated")
  }
  Runtime.getRuntime.addShutdownHook(new Thread { override def run(): Unit = shutdown() })
}

