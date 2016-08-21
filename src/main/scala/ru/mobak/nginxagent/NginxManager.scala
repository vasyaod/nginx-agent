package ru.mobak.nginxagent

import akka.actor.Actor
import com.typesafe.scalalogging.LazyLogging
import sys.process._

object NginxManager {
  case object Refresh
}

/** The actor force Nginx to refresh configuration. */
class NginxManager(pidFile: String) extends Actor with LazyLogging {

  override def receive: Receive = {
    case NginxManager.Refresh =>
      logger.info("Nginx configuration refreshing.")
      val pid = scala.io.Source.fromFile(pidFile).mkString
      logger.info(s"Nginx pid: $pid")
      val code = s"kill -HUP $pid".!
      logger.info(s"Nginx has returned code: $code")
  }
}
