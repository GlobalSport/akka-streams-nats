/*
 * Copyright 2024 MYCOACH PRO SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.mycoachsport

import akka.actor.ActorSystem
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.testkit.TestKit
import io.nats.client.{ConsumerContext, Nats}
import io.nats.client.api.{AckPolicy, ConsumerConfiguration, RetentionPolicy, StreamConfiguration}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.collection.mutable
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Try}

class JetStreamSourceStageTest
    extends TestKit(ActorSystem())
    with WordSpecLike
    with BeforeAndAfterAll
    with MockFactory {

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

  implicit val ec = materializer.system.dispatcher

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
          m.ackSync(Duration.ofMillis(100))
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
      .name("EVENTS-2")
      .subjects("events2.>")
      .retentionPolicy(RetentionPolicy.WorkQueue)
      .build();

    jsm.addStream(sc)

    val js = natsConnection.jetStream()

    val expectedMessages = Seq("foo", "bar", "baz", "toto")

    expectedMessages.foreach { em =>
      js.publish(
        "events2.test",
        em.getBytes(StandardCharsets.UTF_8)
      )
    }

    val c1 = ConsumerConfiguration
      .builder()
      .durable("processor-2")
      .ackPolicy(AckPolicy.Explicit)
      .build()

    jsm.addOrUpdateConsumer("EVENTS-2", c1)

    val streamContext = natsConnection.getStreamContext("EVENTS-2")
    val consumerContext = streamContext.getConsumerContext("processor-2")

    jsm
      .getStreamInfo("EVENTS-2")
      .getStreamState
      .getMsgCount shouldBe expectedMessages.size

    val receivedMessages = mutable.Set[String]()

    Await.result(
      JetStreamSource(consumerContext)
        .map { m =>
          receivedMessages.add(new String(m.getData, StandardCharsets.UTF_8))
          m.ackSync(Duration.ofMillis(100))
        }
        .take(2)
        .run(),
      1.second
    )

    jsm
      .getStreamInfo("EVENTS-2")
      .getStreamState
      .getMsgCount shouldBe expectedMessages.size - 2
    receivedMessages shouldBe expectedMessages.take(2).toSet
  }

  "Not throw when no elements are available" in {
    val jsm = natsConnection.jetStreamManagement()
    val sc = StreamConfiguration
      .builder()
      .name("EVENTS-3")
      .subjects("events3.>")
      .retentionPolicy(RetentionPolicy.WorkQueue)
      .build();

    jsm.addStream(sc)

    val js = natsConnection.jetStream()

    val c1 = ConsumerConfiguration
      .builder()
      .durable("processor-3")
      .ackPolicy(AckPolicy.Explicit)
      .build()

    jsm.addOrUpdateConsumer("EVENTS-3", c1)

    val streamContext = natsConnection.getStreamContext("EVENTS-3")
    val consumerContext = streamContext.getConsumerContext("processor-3")

    val result = Try(
      Await.result(
        JetStreamSource(consumerContext, Duration.ofMillis(1000))
          .mapAsyncUnordered(1) { m =>
            Future.successful(m.ackSync(Duration.ofMillis(100)))
          }
          .run(),
        2.seconds
      )
    )

    result.isFailure shouldBe true
    result.toEither.swap.exists(_.isInstanceOf[TimeoutException]) shouldBe true
  }

  "Properly wait for messages to be available" in {
    val jsm = natsConnection.jetStreamManagement()
    val sc = StreamConfiguration
      .builder()
      .name("EVENTS-4")
      .subjects("events4.>")
      .retentionPolicy(RetentionPolicy.WorkQueue)
      .build();

    jsm.addStream(sc)

    val js = natsConnection.jetStream()

    val c1 = ConsumerConfiguration
      .builder()
      .durable("processor-4")
      .ackPolicy(AckPolicy.Explicit)
      .build()

    jsm.addOrUpdateConsumer("EVENTS-4", c1)

    val streamContext = natsConnection.getStreamContext("EVENTS-4")
    val consumerContext = streamContext.getConsumerContext("processor-4")

    val expectedMessages = Set("foo", "bar")

    val receivedMessages = mutable.Set[String]()

    val runnable = JetStreamSource(consumerContext, Duration.ofMillis(1000))
      .map { m =>
        receivedMessages.add(new String(m.getData, StandardCharsets.UTF_8))
        m.ackSync(Duration.ofMillis(100))
      }
      .take(2)
      .run()

    Thread.sleep(2000)
    expectedMessages.foreach { em =>
      js.publish(
        "events4.test",
        em.getBytes(StandardCharsets.UTF_8)
      )
    }

    Await.result(runnable, 2.seconds)

    receivedMessages shouldBe expectedMessages
  }

  "should retry on exception" in {
    val consumerContext = mock[ConsumerContext]
    val waitTime = Duration.ofMillis(1000)

    val exception = new RuntimeException("fail to pull")

    (consumerContext
      .next(_: java.time.Duration))
      .expects(waitTime)
      .throws(exception)
      .repeated(4)

    implicit val decider: Supervision.Decider = Supervision.stoppingDecider

    Try(
      Await.result(
        JetStreamSource(
          consumerContext,
          waitTime
        ).withAttributes(ActorAttributes.supervisionStrategy(decider))
          .map(println)
          .run(),
        5.seconds
      )
    ) shouldBe Failure(exception)
  }
}
