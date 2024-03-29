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

import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import io.nats.client.{ConsumerContext, Message}

import java.time.Duration
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class JetStreamSourceStage(
    consumerContext: ConsumerContext,
    pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30)
) extends GraphStage[SourceShape[Message]] {

  val out: Outlet[Message] = Outlet[Message]("JetstreamSource.out")

  override def shape: SourceShape[Message] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            val message = waitForMessage()
            push(out, message)
          }

          @tailrec
          def waitForMessage(): Message = {
            val maybeMessage = getMessage(3)

            maybeMessage match {
              case Some(value) =>
                value
              case None =>
                waitForMessage()
            }
          }

          def getMessage(retries: Int): Option[Message] = {
            Try(Option(consumerContext.next(pullMessageTimeout))) match {
              case Failure(exception) if retries == 0 =>
                throw exception
              case Failure(_) =>
                getMessage(retries - 1)
              case Success(value) =>
                value
            }
          }
        }
      )
    }
}
