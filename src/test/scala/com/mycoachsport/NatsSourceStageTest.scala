/*
 * Copyright 2022 MyCoach SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.mycoachsport

import akka.actor.ActorSystem
import akka.pattern
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.mycoachsport.ImplicitHelpers._
import io.nats.client.{Connection, Dispatcher, MessageHandler, Nats}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class NatsSourceStageTest
    extends TestKit(ActorSystem())
    with WordSpecLike
    with MockFactory
    with BeforeAndAfterAll {

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val connection: Connection = mock[Connection]
  val dispatcher = mock[Dispatcher]

  val natsServer = NatsContainer.create()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    natsServer.start()
  }

  override protected def afterAll(): Unit = {
    natsServer.stop()
    super.afterAll()
  }

  "Nats source" should {
    "Subscribe to topics" in {
      mockCreateDispatcher

      val topicNames = (0 to 10).map(_ => UUID.randomUUID().toString).toSet

      topicNames.foreach(mockSubscribeToTopic)

      val subjectSubscription: Set[NatsSubscription] =
        topicNames.map(SubjectSubscription.apply)
      // No need for await here since we don't test the handle message logic
      NatsSource(NatsSettings(connection, subjectSubscription), 10).run
    }

    "Subscribe to queue groupes" in {
      mockCreateDispatcher

      val queueGroupName = UUID.randomUUID().toString

      val topicNames = (0 to 10).map(_ => UUID.randomUUID().toString).toSet

      topicNames.foreach(x => mockSubscribeToQueueGroup(x, queueGroupName))

      val subjectSubscription: Set[NatsSubscription] =
        topicNames.map(x => QueueGroupSubscription(x, queueGroupName))
      // No need for await here since we don't test the handle message logic
      NatsSource(NatsSettings(connection, subjectSubscription), 10).run
    }

    "Unsubscribe when buffer overflows and resubscribe when 10 percent of buffer is available" in {
      val subject = UUID.randomUUID().toString
      val natsConnection =
        Nats.connect(s"nats://localhost:${natsServer.getMappedPort(4222)}")

      val natsSettings =
        NatsSettings(natsConnection, SubjectSubscription(subject))

      val handledMessages = new AtomicInteger()

      val future = NatsSource(natsSettings, 20)
        .mapAsync(1) { _ =>
          pattern.after(10.millis, using = system.scheduler) {
            handledMessages.incrementAndGet()
            Future.successful()
          }
        }
        .run

      (1 to 40).foreach { _ =>
        Thread.sleep(1)
        natsConnection
          .publish(subject, "test".getBytes)
      }

      try {
        future.await(3.seconds)
      } catch {
        case _: TimeoutException =>
        // Ignore timeout exceptions since the stream is an infinite stream awaiting for nats messages
      }

      // We produce 20 messages in 20 ms
      // Buffer is full at 10 ms.
      // A task takes 10 ms to run.
      // Buffer will be able to consume one more task between millisecond 10 and 20
      // This his why we have this magic 11 number here
      handledMessages.intValue() shouldBe 21
    }

    "Consume messages" in {
      val subject = UUID.randomUUID().toString
      val natsConnection =
        Nats.connect(s"nats://localhost:${natsServer.getMappedPort(4222)}")

      val natsSettings =
        NatsSettings(natsConnection, SubjectSubscription(subject))

      val messages = (0 until 100).map(_ => UUID.randomUUID().toString).toSet

      val receivedMessages = scala.collection.mutable.Set[NatsMessage]()

      val future = NatsSource(natsSettings, 10).map { x =>
        receivedMessages.add(x)
      }.run

      messages.foreach(m => natsConnection.publish(subject, m.getBytes))

      try {
        future.await(1.seconds)
      } catch {
        case _: TimeoutException =>
        // Ignore timeout exceptions since the stream is an infinite stream awaiting for nats messages
      }

      receivedMessages.toSet shouldBe messages.map(NatsMessage)
    }
  }

  private def mockSubscribeToTopic(topicName: String) = {
    (dispatcher
      .subscribe(_: String))
      .expects(topicName)
      .returns(dispatcher)
  }

  private def mockSubscribeToQueueGroup(
      topicName: String,
      queueGroup: String
  ) = {
    (dispatcher
      .subscribe(_: String, _: String))
      .expects(topicName, queueGroup)
      .returns(dispatcher)
  }

  private def mockCreateDispatcher = {
    (connection
      .createDispatcher(_: MessageHandler))
      .expects(*)
      .returns(dispatcher)
  }
}
