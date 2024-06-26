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

import akka.stream.scaladsl.Source
import io.nats.client.ConsumerContext

import java.time.Duration
import scala.concurrent.ExecutionContext

object JetStreamSource {

  /** Create a new jetstream source from a consumer context.
    *
    * The source will get the next message onPull.
    *
    * You are responsible for managing Ack's and Nak's.
    *
    * @param consumerContext the consumer context to retrieve messages
    * @return a Jetstrem source
    */
  def apply(
      consumerContext: ConsumerContext,
      pullMessageTimeout: java.time.Duration = Duration.ofSeconds(30)
  )(implicit ec: ExecutionContext) = {
    Source.fromGraph(
      new JetStreamSourceStage(consumerContext, pullMessageTimeout)
    )
  }

}
