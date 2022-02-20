package test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZLoop;
import org.zeromq.ZMQ;
import org.zeromq.ZMQQueue;

public class ZRMQTest {
	
	public void testcase1() throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(1);

		ZContext ctx = new ZContext();

		ZMQ.Socket socket = ctx.createSocket(SocketType.PULL);
		socket.bind("ipc://test");

		ZLoop zLoop = new ZLoop(ctx);

		ZMQ.PollItem pollItem = new ZMQ.PollItem(socket, ZMQ.Poller.POLLIN);

		zLoop.addPoller(pollItem, (loop, item, arg) -> 0, null);

		Future<Integer> future = executorService.submit(zLoop::start);

		Thread.sleep(1000);

		//future.cancel(true);

		socket.close();
		ctx.close();

		executorService.shutdownNow();
	}
	public void testcase2() throws InterruptedException {
		ZContext context = new ZContext();

		ZMQ.Socket inSocket = context.createSocket(SocketType.PULL);
		inSocket.bind("inproc://test.in");
		ZMQ.Socket outSocket = context.createSocket(SocketType.PUSH);
		outSocket.bind("inproc://test.out");

		Future<?> queueFuture =
				Executors.newSingleThreadExecutor().submit(new ZMQQueue(context.getContext(), inSocket, outSocket));

		ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
		pushSocket.connect("inproc://test.in");

		ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
		pullSocket.connect("inproc://test.out");

		Thread.sleep(1000);

		pushSocket.close();
		pullSocket.close();
		inSocket.close();
		outSocket.close();

		queueFuture.cancel(true);

		context.close();
	}
}