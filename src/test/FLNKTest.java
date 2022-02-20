/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.typeutils.base.IntSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.testutils.OneShotLatch;
import org.apache.flink.runtime.execution.Environment;
import org.apache.flink.runtime.operators.testutils.UnregisteredTaskMetricsGroup;
import org.apache.flink.runtime.taskmanager.TaskManagerRuntimeInfo;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.functions.async.AsyncFunction;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.apache.flink.streaming.api.functions.async.collector.AsyncCollector;
import org.apache.flink.streaming.api.graph.StreamConfig;
import org.apache.flink.streaming.api.operators.Output;
import org.apache.flink.streaming.api.operators.async.AsyncWaitOperator;
import org.apache.flink.streaming.api.operators.async.queue.StreamElementQueue;
import org.apache.flink.streaming.api.operators.async.queue.StreamElementQueueEntry;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import org.apache.flink.streaming.runtime.tasks.StreamTask;
import org.apache.flink.streaming.runtime.tasks.TestProcessingTimeService;
import org.apache.flink.util.Preconditions;
import org.apache.flink.util.TestLogger;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class FLNKTest /*extends TestLogger*/ {

	private static final long TIMEOUT = 1000L;

	private static class MyAsyncFunction extends RichAsyncFunction<Integer, Integer> {
		private static final long serialVersionUID = 8522411971886428444L;

		private static final long TERMINATION_TIMEOUT = 5000L;
		private static final int THREAD_POOL_SIZE = 10;

		static ExecutorService executorService;
		static int counter = 0;

		@Override
		public void open(Configuration parameters) throws Exception {
			super.open(parameters);

			synchronized (MyAsyncFunction.class) {
				if (counter == 0) {
					executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
				}

				++counter;
			}
		}

		@Override
		public void close() throws Exception {
			super.close();

			freeExecutor();
		}

		private void freeExecutor() {
			synchronized (MyAsyncFunction.class) {
				--counter;

				if (counter == 0) {
					executorService.shutdown();

					try {
						if (!executorService.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS)) {
							executorService.shutdownNow();
						}
					} catch (InterruptedException interrupted) {
						executorService.shutdownNow();

						Thread.currentThread().interrupt();
					}
				}
			}
		}

		@Override
		public void asyncInvoke(final Integer input, final AsyncCollector<Integer> collector) throws Exception {
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					collector.collect(Collections.singletonList(input * 2));
				}
			});
		}
	}

	/**
	 * A special {@link org.apache.flink.streaming.api.functions.async.AsyncFunction} without issuing
	 * {@link AsyncCollector#collect} until the latch counts to zero.
	 * This function is used in the testStateSnapshotAndRestore, ensuring
	 * that {@link StreamElementQueueEntry} can stay
	 * in the {@link StreamElementQueue} to be
	 * snapshotted while checkpointing.
	 */
	private static class LazyAsyncFunction extends MyAsyncFunction {
		private static final long serialVersionUID = 3537791752703154670L;

		private static CountDownLatch latch;

		public LazyAsyncFunction() {
			latch = new CountDownLatch(1);
		}

		@Override
		public void asyncInvoke(final Integer input, final AsyncCollector<Integer> collector) throws Exception {
			this.executorService.submit(new Runnable() {
				@Override
				public void run() {
					try {
						latch.await();
					}
					catch (InterruptedException e) {
						// do nothing
					}

					collector.collect(Collections.singletonList(input));
				}
			});
		}

		public static void countDown() {
			latch.countDown();
		}
	}

	/**
	 * A {@link Comparator} to compare {@link StreamRecord} while sorting them.
	 */
	private class StreamRecordComparator implements Comparator<Object> {
		@Override
		public int compare(Object o1, Object o2) {
			if (o1 instanceof Watermark || o2 instanceof Watermark) {
				return 0;
			} else {
				StreamRecord<Integer> sr0 = (StreamRecord<Integer>) o1;
				StreamRecord<Integer> sr1 = (StreamRecord<Integer>) o2;

				if (sr0.getTimestamp() != sr1.getTimestamp()) {
					return (int) (sr0.getTimestamp() - sr1.getTimestamp());
				}

				int comparison = sr0.getValue().compareTo(sr1.getValue());
				if (comparison != 0) {
					return comparison;
				} else {
					return sr0.getValue() - sr1.getValue();
				}
			}
		}
	}
	/**
	 * Test case for FLINK-5638: Tests that the async wait operator can be closed even if the
	 * emitter is currently waiting on the checkpoint lock (e.g. in the case of two chained async
	 * wait operators where the latter operator's queue is currently full).
	 *
	 * Note that this test does not enforce the exact strict ordering because with the fix it is no
	 * longer possible. However, it provokes the described situation without the fix.
	 */
	@Test(timeout = 10000L)
	public void testClosingWithBlockedEmitter() throws Exception {
		final Object lock = new Object();

		ArgumentCaptor<Throwable> failureReason = ArgumentCaptor.forClass(Throwable.class);

		Environment environment = mock(Environment.class);
		when(environment.getMetricGroup()).thenReturn(new UnregisteredTaskMetricsGroup());
		when(environment.getTaskManagerInfo()).thenReturn(
			new TaskManagerRuntimeInfo(
				"localhost",
				new Configuration(),
				System.getProperty("java.io.tmpdir")));
		when(environment.getUserClassLoader()).thenReturn(getClass().getClassLoader());
		when(environment.getTaskInfo()).thenReturn(new TaskInfo(
			"testTask",
			1,
			0,
			1,
			0));
		doNothing().when(environment).failExternally(failureReason.capture());

		StreamTask<?, ?> containingTask = mock(StreamTask.class);
		when(containingTask.getEnvironment()).thenReturn(environment);
		when(containingTask.getCheckpointLock()).thenReturn(lock);
		when(containingTask.getProcessingTimeService()).thenReturn(new TestProcessingTimeService());

		StreamConfig streamConfig = mock(StreamConfig.class);
		doReturn(IntSerializer.INSTANCE).when(streamConfig).getTypeSerializerIn1(any(ClassLoader.class));

		final OneShotLatch closingLatch = new OneShotLatch();
		final OneShotLatch outputLatch = new OneShotLatch();

		Output<StreamRecord<Integer>> output = mock(Output.class);
		doAnswer(new Answer() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				assertTrue("Output should happen under the checkpoint lock.", Thread.currentThread().holdsLock(lock));

				outputLatch.trigger();

				// wait until we're in the closing method of the operator
				while (!closingLatch.isTriggered()) {
					lock.wait();
				}

				return null;
			}
		}).when(output).collect(any(StreamRecord.class));

		AsyncWaitOperator<Integer, Integer> operator = new TestAsyncWaitOperator<>(
			new MyAsyncFunction(),
			1000L,
			1,
			AsyncDataStream.OutputMode.ORDERED,
			closingLatch);

		operator.setup(
			containingTask,
			streamConfig,
			output);

		operator.open();

		synchronized (lock) {
			operator.processElement(new StreamRecord<>(42));
		}

		outputLatch.await();

		synchronized (lock) {
			operator.close();
		}

		// check that no concurrent exception has occurred
		try {
			verify(environment, never()).failExternally(any(Throwable.class));
		} catch (Error e) {
			// add the exception occurring in the emitter thread (root cause) as a suppressed
			// exception
			e.addSuppressed(failureReason.getValue());
			throw e;
		}
	}

	/**
	 * Testing async wait operator which introduces a latch to synchronize the execution with the
	 * emitter.
	 */
	private static final class TestAsyncWaitOperator<IN, OUT> extends AsyncWaitOperator<IN, OUT> {

		private static final long serialVersionUID = -8528791694746625560L;

		private final transient OneShotLatch closingLatch;

		public TestAsyncWaitOperator(
				AsyncFunction<IN, OUT> asyncFunction,
				long timeout,
				int capacity,
				AsyncDataStream.OutputMode outputMode,
				OneShotLatch closingLatch) {
			super(asyncFunction, timeout, capacity, outputMode);

			this.closingLatch = Preconditions.checkNotNull(closingLatch);
		}

		@Override
		public void close() throws Exception {
			closingLatch.trigger();
			checkpointingLock.notifyAll();
			super.close();
		}
	}
}