package com.mycoachsport

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import io.nats.client.Nats
import io.nats.client.api.{
  AckPolicy,
  ConsumerConfiguration,
  RetentionPolicy,
  StreamConfiguration
}
import org.scalatest.Matchers._
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import java.nio.charset.StandardCharsets
import scala.collection.mutable
import scala.concurrent.Await

class JetStreamSourceStageTest
    extends TestKit(ActorSystem())
    with WordSpecLike
    with BeforeAndAfterAll {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val natsServer = NatsContainer.create()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    natsServer.start()
  }

  override protected def afterAll(): Unit = {
    natsServer.stop()
    super.afterAll()
  }

  lazy val natsConnection =
    Nats.connect(s"nats://localhost:${natsServer.getMappedPort(4222)}")

  "Test consuming all messages available" in {
    val jsm = natsConnection.jetStreamManagement()
    val sc = StreamConfiguration
      .builder()
      .name("EVENTS")
      .subjects("events.>")
      .retentionPolicy(RetentionPolicy.WorkQueue)
      .build();

    jsm.addStream(sc)

    val js = natsConnection.jetStream()

    val expectedMessages = Set("foo", "bar")

    expectedMessages.foreach { em =>
      js.publish(
        "events.test",
        em.getBytes(StandardCharsets.UTF_8)
      )
    }

    val c1 = ConsumerConfiguration
      .builder()
      .durable("processor")
      .ackPolicy(AckPolicy.Explicit)
      .build()

    jsm.addOrUpdateConsumer("EVENTS", c1)

    val streamContext = natsConnection.getStreamContext("EVENTS")
    val consumerContext = streamContext.getConsumerContext("processor")

    jsm
      .getStreamInfo("EVENTS")
      .getStreamState
      .getMsgCount shouldBe expectedMessages.size

    val receivedMessages = mutable.Set[String]()

    Await.result(
      JetStreamSource(consumerContext)
        .map { m =>
          receivedMessages.add(new String(m.getData, StandardCharsets.UTF_8))
          m.ack()
        }
        .take(expectedMessages.size)
        .run(),
      1.second
    )

    jsm.getStreamInfo("EVENTS").getStreamState.getMsgCount shouldBe 0
    receivedMessages shouldBe expectedMessages
  }

  "Test consuming a subset of available messages" in {
    val jsm = natsConnection.jetStreamManagement()
    val sc = StreamConfiguration
      .builder()
      .name("EVENTS")
      .subjects("events.>")
      .retentionPolicy(RetentionPolicy.WorkQueue)
      .build();

    jsm.addStream(sc)

    val js = natsConnection.jetStream()

    val expectedMessages = Seq("foo", "bar", "baz", "toto")

    expectedMessages.foreach { em =>
      js.publish(
        "events.test",
        em.getBytes(StandardCharsets.UTF_8)
      )
    }

    val c1 = ConsumerConfiguration
      .builder()
      .durable("processor")
      .ackPolicy(AckPolicy.Explicit)
      .build()

    jsm.addOrUpdateConsumer("EVENTS", c1)

    val streamContext = natsConnection.getStreamContext("EVENTS")
    val consumerContext = streamContext.getConsumerContext("processor")

    jsm
      .getStreamInfo("EVENTS")
      .getStreamState
      .getMsgCount shouldBe expectedMessages.size

    val receivedMessages = mutable.Set[String]()

    Await.result(
      JetStreamSource(consumerContext)
        .map { m =>
          receivedMessages.add(new String(m.getData, StandardCharsets.UTF_8))
          m.ack()
        }
        .take(2)
        .run(),
      1.second
    )

    jsm
      .getStreamInfo("EVENTS")
      .getStreamState
      .getMsgCount shouldBe expectedMessages.size - 2
    receivedMessages shouldBe expectedMessages.take(2).toSet
  }

}
