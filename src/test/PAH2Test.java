package test;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PAH2Test {

	public void testcase1() throws Exception {
		MqttAsyncClient client = new MqttAsyncClient("tcp://localhost:1883", "temp");

		final CountDownLatch stopLatch = new CountDownLatch(10);

		client.setCallback(new MqttCallback() {

			private final AtomicBoolean processed = new AtomicBoolean();

			@Override
			public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
				try {
					if (!processed.getAndSet(true)) {
						Thread.sleep(2000); // so the Rec: queue is full and the thread suspended
					}
					throw new RuntimeException();
				}
				finally {
					stopLatch.countDown();
				}
			}

			@Override
			public void connectionLost(Throwable throwable) {

			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

			}
		});

		client.connect().waitForCompletion(3000);
		client.subscribe("foo", 1).waitForCompletion(3000);

		for (int i = 0; i < 10; i++) {
			client.publish("foo", new MqttMessage("test".getBytes()));
		}

		assertTrue(stopLatch.await(10, TimeUnit.SECONDS));
	}
	
	public void testcase2() throws Exception {
		new Deadlock().test();
	}
}

class Deadlock implements MqttCallback {

	private final MqttClient mqttClient1;

	private final MqttClient mqttClient2;

	Deadlock() throws Exception {
		mqttClient1 = new MqttClient("tcp://localhost:1883", "foo");
		mqttClient1.setCallback(this);
		mqttClient1.connect();
		mqttClient2 = new MqttClient("tcp://localhost:1883", "bar");
		mqttClient2.setCallback(this);
		mqttClient2.connect();
		mqttClient2.subscribe("bar");
	}

	void test() throws Exception {
		for (int i = 0; i < 16; i++) {
			System.out.println("sending");
			mqttClient1.publish("bar", new MqttMessage("foo".getBytes()));
		}
	}

	@Override
	public void connectionLost(Throwable cause) {
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		System.out.println("Message arrived: " + new String(message.getPayload()));
		Thread.sleep(5000);
		//throw new RuntimeException("deadlock");
		throw new MqttException(0);
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
	}
}