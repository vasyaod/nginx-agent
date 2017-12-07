package ru.mobak.nginxagent

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.github.mustachejava.DefaultMustacheFactory
import org.scalatest.{FunSuite, Matchers}

class ConfigurationGeneratorTest extends FunSuite with Matchers {
  implicit val as = ActorSystem()

  val outDirectory = "target/"

  val probe = TestProbe()
  val configurationGenerator = as.actorOf(Props(new ConfigurationGenerator(
    // Инициализуруем шаблонизатор.
    mustache = new DefaultMustacheFactory().compile(
      // Путь к файлу с шаблонами берем из настроек.
      "default-template.mustache"
    ),
    hashType = "md5",
    configPath = outDirectory,
    secretKey = "qwerty",
    defaultServer = "127.0.0.1",
    defaultPort = 80,
    nginxManager = probe.ref
  )))

  test("Generator should generate empty config (without nodes) and reload nginx") {
    configurationGenerator ! ConfigurationGenerator.SetConfiguration("test1", Set())
    probe.expectMsg(NginxManager.Refresh)

    scala.io.Source.fromFile(outDirectory + "test1.conf").mkString shouldEqual
      """upstream test1 {
        |    server 127.0.0.1:80;
        |}
        |
        |map $args $test1_host {
        |    ~(.*)nodeId=fdddde15(.*) 127.0.0.1:80;
        |}""".stripMargin

  }

  test("Generator should add a config for another node") {
    configurationGenerator ! ConfigurationGenerator.SetConfiguration("test2", Set())
    probe.expectMsg(NginxManager.Refresh)

    scala.io.Source.fromFile(outDirectory + "test2.conf").mkString shouldEqual
      """upstream test2 {
        |    server 127.0.0.1:80;
        |}
        |
        |map $args $test2_host {
        |    ~(.*)nodeId=fdddde15(.*) 127.0.0.1:80;
        |}""".stripMargin
  }

  test("Generator should generate none empty config") {
    configurationGenerator ! ConfigurationGenerator.SetConfiguration("test1", Set(ConfigurationGenerator.Node("test-host", 12345)))
    probe.expectMsg(NginxManager.Refresh)

    scala.io.Source.fromFile(outDirectory + "test1.conf").mkString shouldEqual
      """upstream test1 {
        |    server test-host:12345;
        |}
        |
        |map $args $test1_host {
        |    ~(.*)nodeId=8162444346b2e365ed647ea9d0c8ea54(.*) test-host:12345;
        |}""".stripMargin
  }

  test("Generator will not change nginx config if nothing changed") {
    configurationGenerator ! ConfigurationGenerator.SetConfiguration("test1", Set(ConfigurationGenerator.Node("test-host", 12345)))
    probe.expectNoMsg()
  }
}
