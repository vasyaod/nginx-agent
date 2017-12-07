package ru.mobak.nginxagent

import java.io.{File, FileWriter}

import akka.actor.{Actor, ActorRef}
import com.github.mustachejava.Mustache
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.filefilter.WildcardFileFilter

object ConfigurationGenerator {

  case class Node(host: String, port: Int)

  /** Command that set new configuration */
  case class SetConfiguration(serviceName: String, nodes: Set[Node])

  def md5hash(s: String) = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16).toLowerCase
  }
}

object MustacheModel {
  /** The class is needed for mustache template as a model. */
  case class Config(serviceName: String, serviceNameUnderscore: String, nodes: Array[Node])

  /** The class is needed for mustache template as a model. */
  case class Node(host: String, port: Int, nodeId: String)
}

/** The actor refresh Nginx configuration. */
class ConfigurationGenerator(mustache: Mustache,
                             hashType: String,
                             configPath: String,
                             secretKey: String,
                             defaultServer: String,
                             defaultPort: Int,
                             nginxManager: ActorRef) extends Actor with LazyLogging {

  var configuration: Map[String, Set[ConfigurationGenerator.Node]] = Map()

  override def preStart(): Unit = {
    configuration = Map()

    ////
    // Remove our old nginx-configs if of cause there are it.
    val dir = new File(configPath)
    val fileFilter: java.io.FilenameFilter = new WildcardFileFilter("*.conf")
    val files = dir.listFiles(fileFilter)
    files.foreach(f =>
      if (!f.delete())
        logger.error(s"File ${f.getPath} can not be removed.")
      else
        logger.info(s"Config file ${f.getPath} was removed.")
    )
  }

  override def receive: Receive = {

    case ConfigurationGenerator.SetConfiguration(serviceName, _nodes) =>
      if (!configuration.contains(serviceName) || (configuration.contains(serviceName) && configuration(serviceName) != _nodes)) {
        configuration = configuration + (serviceName -> _nodes)

        val nodes = _nodes.map { node =>
          val hash =
            if (hashType == "java")
              Integer.toHexString((node.host + node.port + secretKey).hashCode)
            else
              ConfigurationGenerator.md5hash(node.host + node.port + secretKey)

          MustacheModel.Node(node.host, node.port, hash)
        }.toArray

        val confFile = new File(s"$configPath/$serviceName.conf")

        if (nodes.nonEmpty) {
          mustache.execute(
            new FileWriter(confFile),
            MustacheModel.Config(serviceName, serviceName.replace("-", "_"), nodes)
          ).close()

        } else {
          mustache.execute(
            new FileWriter(confFile),
            // If there is not any records for a service, we set default IP address
            MustacheModel.Config(
              serviceName,
              serviceName.replace("-", "_"),
              Seq(MustacheModel.Node(defaultServer, defaultPort, Integer.toHexString((defaultServer + ":" + defaultPort).hashCode))).toArray
            )
          ).close()
        }

        logger.info(s"Reconfigure for service: $serviceName")
        nginxManager ! NginxManager.Refresh
      }
  }
}
