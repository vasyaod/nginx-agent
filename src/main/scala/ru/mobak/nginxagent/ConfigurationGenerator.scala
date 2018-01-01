package ru.mobak.nginxagent

import java.io.{File, FileNotFoundException, FileWriter}
import java.net.URL

import akka.actor.{Actor, ActorRef}
import com.github.mustachejava.resolver.FileSystemResolver
import com.github.mustachejava.{DefaultMustacheFactory, Mustache, MustacheFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.WildcardFileFilter

import scala.collection.JavaConverters._

object ConfigurationGenerator {

  case class Node(host: String, port: Int)

  case class Configuration(serviceName: String,
                           nodes: Set[Node],
                           additionalParams: Map[String, String] = Map(),
                           templateUrl: Option[String] = None)

  /** Command that set new configuration */
  case class SetConfiguration(configuration: Configuration)

  /** Command that set new configurations for all services */
  case class SetConfigurations(consfigurations: Set[Configuration])

  def md5hash(s: String) = {
    val m = java.security.MessageDigest.getInstance("MD5")
    val b = s.getBytes("UTF-8")
    m.update(b, 0, b.length)
    new java.math.BigInteger(1, m.digest()).toString(16).toLowerCase
  }
}

object MustacheModel {
  /** The class is needed for mustache template as a model. */
  case class Config(SERVICE_NAME: String, SERVICE_NAME_UNDERSCORE: String, NODES: Array[Node])

  /** The class is needed for mustache template as a model. */
  case class Node(HOST: String, PORT: Int, NODE_ID: String)
}

/** The actor refresh Nginx configuration. */
class ConfigurationGenerator(defaultTemplateUrl: String,
                             hashType: String,
                             configPath: String,
                             secretKey: String,
                             defaultServer: String,
                             defaultPort: Int,
                             nginxManager: ActorRef) extends Actor with LazyLogging {
  val mustacheFactory: MustacheFactory = new DefaultMustacheFactory(new FileSystemResolver(new File("/")))

  var configurationCache: Map[String, ConfigurationGenerator.Configuration] = Map()
  var filesCache: Map[String, File] = Map()

  override def preStart(): Unit = {
    configurationCache = Map()

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

    case ConfigurationGenerator.SetConfiguration(configuration) =>

      val serviceName = configuration.serviceName

      if (!configurationCache.contains(configuration.serviceName) || (configurationCache.contains(serviceName) && configurationCache(serviceName) != configuration)) {
      configurationCache = configurationCache + (serviceName -> configuration)

        def tmpFile(templatePath: String) = {
          filesCache.get(templatePath) match {
            case Some(x) => x
            case None =>

              val resUrl = getClass().getClassLoader().getResource(templatePath)
              val url = if (resUrl == null) new URL(templatePath) else resUrl

              val temp = File.createTempFile("template", ".mustache")
              FileUtils.copyURLToFile(url, temp)
              filesCache = filesCache + (templatePath -> temp)
              temp
          }
        }

        val templateUrl = configuration.templateUrl.getOrElse(defaultTemplateUrl)
        val file = try {
          tmpFile(templateUrl)
        } catch {
          case ex: FileNotFoundException =>
            logger.error(s"Template can not be found in $templateUrl,  in this case will be used default template from resources.")
            tmpFile("default-template.mustache")
        }

        val mustache = mustacheFactory.compile(file.getAbsolutePath)

        val nodes = configuration.nodes.map { node =>
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
            Array(
              MustacheModel.Config(serviceName, serviceName.replace("-", "_"), nodes),
              configuration.additionalParams.asJava
            )
          ).close()

        } else {
          mustache.execute(
            new FileWriter(confFile),
            // If there is not any records for a service, we set default IP address
            Array(
              MustacheModel.Config(
                serviceName,
                serviceName.replace("-", "_"),
                Seq(MustacheModel.Node(defaultServer, defaultPort, Integer.toHexString((defaultServer + ":" + defaultPort).hashCode))).toArray
              ),
              configuration.additionalParams.asJava
            )
          ).close()
        }

        logger.info(s"Reconfigure for service: $serviceName")
        nginxManager ! NginxManager.Refresh
      }

    case ConfigurationGenerator.SetConfigurations(configurations) =>
      configurationCache.foreach { case (serviceName, _) =>
        if (!configurations.exists(_.serviceName == serviceName)) {
          ConfigurationGenerator.SetConfiguration(ConfigurationGenerator.Configuration(serviceName, Set(), Map()))
        }
      }

      configurations.foreach { configuration =>
        self ! ConfigurationGenerator.SetConfiguration(configuration)
      }
  }
}
