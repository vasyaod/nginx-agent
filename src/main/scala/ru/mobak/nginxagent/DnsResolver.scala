package ru.mobak.nginxagent

import java.net.InetAddress

import akka.actor.{Actor, ActorRef}
import com.spotify.dns.ChangeNotifier.ChangeNotification
import com.spotify.dns.{ChangeNotifier, DnsSrvResolver, DnsSrvWatcher, LookupResult}
import com.typesafe.scalalogging.LazyLogging

/** The actor refresh Nginx configuration by SRV records in DNS service. */
class DnsResolver(resolver: DnsSrvResolver,
                  watcher: DnsSrvWatcher[LookupResult],
                  serviceName: String,
                  domainSuffix: String,
                  configurationGenerator: ActorRef) extends Actor with LazyLogging {

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
        ConfigurationGenerator.Node(
          InetAddress.getByName(lookupResult.host()).getHostAddress,
          lookupResult.port()
        )
      }

      configurationGenerator ! ConfigurationGenerator.SetConfiguration(ConfigurationGenerator.Configuration(serviceName, nodes, Map()))
  }
}
