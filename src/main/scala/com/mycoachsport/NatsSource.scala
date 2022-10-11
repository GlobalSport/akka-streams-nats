/*
 * Copyright 2020 MyCoach SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.mycoachsport

import akka.NotUsed
import akka.stream.scaladsl.Source

object NatsSource {

  /** Create a new Nats source with the given settings.
   *
   * The buffer size will be used as a cap for an in memory queue. The queue will store messages if there's no upstream
   * available to consume them.
   *
   * If the queue size is reached an exception will be thrown.
   *
   * @param natsSettings the settings for the source
   * @param bufferSize   the size of the buffer
   * @return a nats source
   */
  def apply(
             natsSettings: NatsSettings,
             bufferSize: Int
           ): Source[NatsMessage, NotUsed] =
    Source.fromGraph(new NatsSourceStage(natsSettings, bufferSize))
}
