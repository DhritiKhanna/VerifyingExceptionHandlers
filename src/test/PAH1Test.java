package test;

import java.net.URI;
import java.util.logging.Level;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.test.client.MqttClientFactoryPaho;
import org.eclipse.paho.client.mqttv3.test.properties.TestProperties;
import org.eclipse.paho.client.mqttv3.test.utilities.Utility;

public class PAH1Test {

	private static URI serverURI;
	private static MqttClientFactoryPaho clientFactory;
	
	public static void setUp() throws Exception {
		try {
	      serverURI = TestProperties.getServerURI();
	      clientFactory = new MqttClientFactoryPaho();
	      clientFactory.open();
	    }
	    catch (Exception exception) {
	    	System.out.println(Level.SEVERE +"caught exception:" + exception);
	    	throw exception;
	    }
	  }

	  public static void tearDown() throws Exception {
		  try {
			  if (clientFactory != null) {
				  clientFactory.close();
				  clientFactory.disconnect();
			  }
		  }
		  catch (Exception exception) {
			  System.out.println(Level.SEVERE + "caught exception:" + exception);
		  }
	  }

	  public void testConnect() throws Exception {
	    final String methodName = Utility.getMethodName();

	    IMqttClient mqttClient = null;
	    try {
	    	mqttClient = clientFactory.createMqttClient(serverURI, methodName);
	    	System.out.println("Connecting...(serverURI:" + serverURI + ", ClientId:" + methodName);
	    	mqttClient.connect();
	    	System.out.println("Disconnecting...");
	    	mqttClient.disconnect();
	    	System.out.println("Connecting...(serverURI:" + serverURI + ", ClientId:" + methodName);
	    	mqttClient.connect();
	    	System.out.println("Disconnecting...");
	    	mqttClient.disconnect();
	    }
	    catch (Exception exception) {
	    	System.out.println(Level.SEVERE + "caught exception:" + exception);
	    }
	    finally {
	    	if (mqttClient != null) {
	    		System.out.println("Close...");
	    		mqttClient.close();
	    	}
	    }

	    System.out.println(methodName);
	  }
	  
	  public void testcase1() {
		  try {
			  setUp();
			  testConnect();
			  tearDown();
		  } catch(Exception e) {}
	  }
}