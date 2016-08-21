package ru.mobak.nginxagent

import java.io.{File, FileWriter}

import akka.actor.{Actor, ActorRef}
import com.github.mustachejava.Mustache
import com.spotify.dns.ChangeNotifier.ChangeNotification
import com.spotify.dns.{ChangeNotifier, DnsSrvResolver, DnsSrvWatcher, LookupResult}
import com.typesafe.scalalogging.LazyLogging

object DnsConfigurationGenerator {
  /**
    * Данные классы нужны для шаблонизатора, мы их используем как модель.
    */
  case class Config(serviceName: String, serviceNameUnderscore: String, nodes: Array[Node])

  /**
    * Данные классы нужны для шаблонизатора, мы их используем как модель.
    */
  case class Node(host: String, port: Int, nodeId: String)

  def md5hash(s: String) = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16)
  }
}

/** The actor refresh Nginx configuration by SRV records in DNS service. */
class DnsConfigurationGenerator(resolver: DnsSrvResolver,
                                watcher: DnsSrvWatcher[LookupResult],
                                serviceName: String,
                                mustache: Mustache,
                                domainSuffix: String,
                                hashType: String,
                                configPath: String,
                                defaultServer: String,
                                defaultPort: Int,
                                nginxManager: ActorRef) extends Actor with LazyLogging {

  case class Reconfigure(newLookupResults: Set[LookupResult])

  var notifier: Option[ChangeNotifier[LookupResult]] = None

  override def preStart(): Unit = {
    logger.info(s"DNS configurator for service $serviceName has been started")
    // First of all set default servers to config.
    self ! Reconfigure(Set())

    val notifier = watcher.watch(s"_$serviceName._tcp.$domainSuffix")
    notifier.setListener(new ChangeNotifier.Listener[LookupResult] {
      override def onChange(changeNotification: ChangeNotification[LookupResult]): Unit = {
        import scala.collection.JavaConverters._

        self ! Reconfigure(changeNotification.current().asScala.toSet)
      }
    }, false)

  }

  override def postStop(): Unit = {
    logger.info(s"DNS configurator for service $serviceName has been stoped")
    notifier.foreach(_.close())
  }

  override def receive: Receive = {
    case Reconfigure(newLookupResults) =>

      val nodes = newLookupResults.map { lookupResult =>
        val hash =
          if (hashType == "java")
            Integer.toHexString((lookupResult.host() + ":" + lookupResult.port()).hashCode)
          else
            DnsConfigurationGenerator.md5hash(lookupResult.host() + ":" + lookupResult.port())

        DnsConfigurationGenerator.Node(lookupResult.host(), lookupResult.port(), hash)
      }.toArray

      val confFile = new File(s"$configPath/$serviceName.conf")

      if (nodes.nonEmpty) {
        mustache.execute(
          new FileWriter(confFile),
          DnsConfigurationGenerator.Config(serviceName, serviceName.replace("-", "_"), nodes)
        ).close()

      } else {
        mustache.execute(
          new FileWriter(confFile),
          // If there is not any records for a service, we set default IP address
          DnsConfigurationGenerator.Config(
            serviceName,
            serviceName.replace("-", "_"),
            Seq(DnsConfigurationGenerator.Node(defaultServer, defaultPort, Integer.toHexString((defaultServer + ":" + defaultPort).hashCode))).toArray
          )
        ).close()
      }

      logger.info(s"Reconfigure for service: $serviceName")
      nginxManager ! NginxManager.Refresh
  }
}
