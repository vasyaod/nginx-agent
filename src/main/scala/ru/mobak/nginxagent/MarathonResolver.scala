package ru.mobak.nginxagent

import java.net.InetAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging
import spray.client.pipelining._
import spray.http.{HttpEntity, MediaTypes}
import spray.httpx.unmarshalling.Unmarshaller

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect._

trait MarathonApi {
  def allNodes(url: String, urls: List[String])
}

object MarathonApi {

  /** App description */
  case class App(id: String)

  /** List of all apps runned by Marathon */
  case class Apps(apps: Seq[App])

  /** Task description for some app */
  case class Task(host: String, ports: Seq[Int])

  /** List of tasks for some app */
  case class Tasks(tasks: Seq[Task])

  case class Node(host: String, port: Int)
  case class Service(id: String, nodes: Iterable[Node])

  val mapper: ObjectMapper = new ObjectMapper()
    .registerModule(DefaultScalaModule)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  implicit def unmarshaller[T: ClassTag] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case HttpEntity.NonEmpty(contentType, data) =>
        mapper.readValue(data.asString, classTag[T].runtimeClass.asInstanceOf[Class[T]])
    }

  def tasks(appId: String, urls: List[String])(implicit as: ActorSystem): Future[Tasks] = {
    tasks(appId, urls.head, urls.tail)
  }

  def tasks(appId: String, url: String, urls: List[String])(implicit as: ActorSystem): Future[Tasks] = {
    implicit val ec = as.dispatcher
    println()
    val p =
      sendReceive  ~>
      unmarshal[Tasks]

//    println("TASK URL:" + s"http://$url/apps/$appId/tasks")

    val res = p(Get(s"http://$url/v2/apps/$appId/tasks"))
    if (urls.isEmpty) {
      res
    } else {
      res.recoverWith {
        case t: Throwable =>
          tasks(appId, urls.head, urls.tail)
      }
    }
  }

  def apps(urls: List[String])(implicit as: ActorSystem): Future[Apps] = {
    apps(urls.head, urls.tail)
  }

  def apps(url: String, urls: List[String])(implicit as: ActorSystem): Future[Apps] = {
    implicit val ec = as.dispatcher

    val p =
      sendReceive  ~>
      unmarshal[Apps]

    val res = p(Get(s"http://$url/v2/apps"))
    if (urls.isEmpty) {
      res
    } else {
      res.recoverWith {
        case t: Throwable =>
          apps(urls.head, urls.tail)
      }
    }
  }

  def allNodes(urls: List[String])(implicit as: ActorSystem): Future[Seq[Service]] = {
    implicit val ec = as.dispatcher

    for {
      apps <- apps(urls)
      services <- Future.sequence(
        apps.apps.map(app => tasks(app.id, urls).map { tasks =>
          Service(app.id, tasks.tasks.flatMap { task =>
            if(task.ports.isEmpty)
              None
            else
              Some(Node(task.host, task.ports.head))
          })
        })
      )
    } yield services
  }
}

/** The actor refresh Nginx configuration by Marathon API. */
class MarathonResolver(marathonUrls: List[String],
                       updatePeriod: Int,
                       services: List[String],
                       configurationGenerator: ActorRef) extends Actor with LazyLogging {

  case object Refresh

  /** Сат */
  private var refreshTask: Option[Cancellable] = None

  override def preStart(): Unit = {
    implicit val ec = context.dispatcher
    // Periodically
    refreshTask = Some(context.system.scheduler.schedule(updatePeriod.seconds, updatePeriod.seconds, self, Refresh))
  }

  override def postStop(): Unit = {
    // Cancel the timer
    refreshTask foreach (_.cancel())
  }

  override def receive: Receive = {
    case Refresh =>
      implicit val as = context.system
      implicit val ec = context.dispatcher

      MarathonApi.allNodes(marathonUrls).foreach { rewServices =>
        val aveilableServices = rewServices.map(service => service.copy(id = if (service.id.startsWith("/")) service.id.drop(1) else service.id.split("/").reverse.mkString("-")))
        services.foreach { service =>
          aveilableServices.find(_.id == service) match {
            case Some(service) =>
              configurationGenerator ! ConfigurationGenerator.SetConfiguration(service.id, service.nodes.map { node =>
                ConfigurationGenerator.Node(InetAddress.getByName(node.host).getHostAddress, node.port)
              }.toSet)
            case None =>
              configurationGenerator ! ConfigurationGenerator.SetConfiguration(service, Set())
          }
        }
      }
  }
}
