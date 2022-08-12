/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package test;

import org.apache.log4j.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Louis Jacomet (axjuz)
 */
public class LOGJTest {

  private static int BUFFER_SIZE = 1;

  private Logger logger;

  public void testcase1() throws InterruptedException {
	  setUp();
	  dispatcherDeathDoesNotCauseDeadlock();
  }
  
  public void setUp() {
    AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setBufferSize(BUFFER_SIZE);
    asyncAppender.addAppender(new ConsoleAppender(new SimpleLayout()));
    LogManager.getRootLogger().addAppender(asyncAppender);

    logger = Logger.getLogger(LOGJTest.class);

  }

  public void dispatcherDeathDoesNotCauseDeadlock() throws InterruptedException {
    final CountDownLatch dispatcherLatch = new CountDownLatch(1);
    final CountDownLatch endLatch = new CountDownLatch(1);

    logToFillBuffer(dispatcherLatch, endLatch);

    logToKillDispatcher(dispatcherLatch);

    // Wait for logging thread to be done
    endLatch.await(1L, TimeUnit.SECONDS);

    //assertThat("Logging should have been done", endLatch.getCount(), is(0L));
  }

  private void logToFillBuffer(final CountDownLatch dispatcherLatch, final CountDownLatch endLatch) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          // Wait for the dispatcher thread to be processing events
          dispatcherLatch.await();
          // Log above buffer capacity, very fast to fill AsyncAppender buffer up
          for (int i = 0; i < BUFFER_SIZE + 30; i++) {
              logger.error("Locking me up " + i + " " + BUFFER_SIZE);
          }
          // Indicate thread done
          endLatch.countDown();
        } catch (Exception e) {
        }
      }
    }).start();
  }

  private void logToKillDispatcher(final CountDownLatch dispatcherLatch) {
    logger.error(new Object() {
      @Override
      public String toString() {
        // Start logging loop
        dispatcherLatch.countDown();
        try {
          // Wait for buffer to fill
          Thread.sleep(5000);
        } catch (InterruptedException e) { }
        // Kill dispatcher thread
        //throw new RuntimeException();
        //System.out.println("abcd " + Thread.currentThread().getName());
        return "abcd " + Thread.currentThread().getName();
      }
    });
  }
}