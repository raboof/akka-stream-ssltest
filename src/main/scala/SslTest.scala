import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, TrustManagerFactory }

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.TLSProtocol._
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.{ Failure, Success }

object SslTest {

  def main(args: Array[String]): Unit = {
    args match {
      case Array("client", "placebo", _*) => client(placeboSslTls)
      case Array("client", "ssl", _*)     => client(clientSslTls)
      case Array("server", "placebo", _*) => server(placeboSslTls)
      case Array("server", "ssl", _*)     => server(serverSslTls)
      case _                              => println("Use parameters `client placebo`, `client ssl`, `server placebo`, `server ssl")
    }
  }

  val clientSslTls = TLS(sslContext, None, NegotiateNewSession.withDefaults, Client)
  val serverSslTls = TLS(sslContext, None, NegotiateNewSession.withDefaults, Server)
  val placeboSslTls = TLSPlacebo()

  def sslContext(): SSLContext = {

    val password = "changeme"

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(getClass.getResourceAsStream("/keystore"), password.toCharArray)

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    trustStore.load(getClass.getResourceAsStream("/truststore"), password.toCharArray)

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, password.toCharArray)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  def server(sslTlsBidiFlow: BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val handler = Sink.foreach[Tcp.IncomingConnection] { conn =>
      println("Client connected from: " + conn.remoteAddress)

      val echo = Flow[SslTlsInbound]
        .collect[ByteString] { case SessionBytes(_, bytes) => bytes }
        .map[SslTlsOutbound](SendBytes)

      conn handleWith sslTlsBidiFlow.reversed.join(echo).alsoTo(Sink.onComplete(_ => println("Client disconnected")))
    }

    val connections = Tcp().bind("127.0.0.1", 6000)
    val binding = connections.to(handler).run()

    binding.onComplete {
      case Success(b) =>
        println("Server started, listening on: " + b.localAddress)
      case Failure(e) =>
        println(s"Server could not bind to 127.0.0.1:6000: ${e.getMessage}")
        system.terminate()
    }

  }

  def client(sslTlsBidiFlow: BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val outgoing = Tcp().outgoingConnection("127.0.0.1", 6000)

    val securedOutgoing = Flow[ByteString]
      .map(SendBytes(_))
      .via(sslTlsBidiFlow.join(outgoing))
      .collect { case SessionBytes(_, b) => b }

    Source.fromFuture(Promise[ByteString]().future) // Will not produce an element
      .via(securedOutgoing)
      .runWith(Sink.ignore)

    // Kill client after 1 second
    Thread.sleep(1000)
    println("Simulating client crashing")
    System.exit(0)
  }
}
