/* $Id: Socket.java 840 2010-11-25 01:10:42Z cartho $ */

package env.java.net;

/* Stub class for java.net.Socket. */

import env.java.io.InputStream;
import env.java.io.OutputStream;
import gov.nasa.jpf.vm.Verify;
import java.io.IOException;
import java.net.InetAddress;

public class Socket {
	int port;
  InputStream hardCodedInput;
  boolean closed;

  public Socket() {
     hardCodedInput = new InputStream();
     closed = false;
  }

  public Socket(int port) {
	// TODO Auto-generated constructor stub
	  this.port = port;
}

  public void close() throws IOException {
    /*if (Verify.getBoolean()) {
      throw new IOException("Simulated exception when closing connection.");
    }*/
	closed = true;
  }
  
  public boolean isClosed() {
	  return closed;
  }

  public OutputStream getOutputStream() throws IOException {
    return new OutputStream();
  }

  public InputStream getInputStream() throws IOException {
    return hardCodedInput;
  }

public void setSoTimeout(int to) {
	// TODO Auto-generated method stub
	
}

public void bind(InetSocketAddress inetSocketAddress) {
	// TODO Auto-generated method stub
	
}

public void connect(InetSocketAddress addr) throws java.net.ConnectException {
	if (Verify.getBoolean()) { // possible failure
		throw new java.net.ConnectException("Connection refused");
	}
}

public void connect(InetSocketAddress inetSocketAddress, int cto) {
	// TODO Auto-generated method stub
	
}

public InetAddress getInetAddress() {
	// TODO Auto-generated method stub
	return null;
}

public int getPort() {
	// TODO Auto-generated method stub
	return port;
}
}
