package ru.mobak.nginxagent

import akka.actor.{Actor, Cancellable}
import com.typesafe.scalalogging.LazyLogging

import sys.process._
import scala.concurrent.duration._

object NginxManager {
  case object Refresh
}

/** The actor force Nginx to refresh configuration. */
class NginxManager(pidFile: String, reloadCommand: String) extends Actor with LazyLogging {

  /** Command for server restarting */
  case object Restart

  var task: Option[Cancellable] = None

  override def receive: Receive = {
    case NginxManager.Refresh =>

      implicit val ec = context.dispatcher

      task.foreach(_.cancel())
      task = Some(context.system.scheduler.scheduleOnce(1.seconds, self, Restart))

    case Restart =>
      logger.info("Nginx configuration refreshing.")
      val code =
        if (reloadCommand.contains("{PID}")) {
          val pid = scala.io.Source.fromFile(pidFile).mkString
          logger.info(s"Nginx pid: $pid")
          reloadCommand.replace("{PID}", pid).!
        } else {
          reloadCommand.!
        }

      logger.info(s"Nginx has returned code: $code")
  }
}
