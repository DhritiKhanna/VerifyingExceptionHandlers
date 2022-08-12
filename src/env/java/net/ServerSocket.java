/* $Id: ServerSocket.java 838 2010-11-24 08:16:23Z cartho $ */

package env.java.net;

/* ServerSocket stub. */

import gov.nasa.jpf.vm.Verify;
import java.io.IOException;

public class ServerSocket {
  
  boolean closed = false;
  int port;
  
  public ServerSocket(int port) throws IOException {
	  this.port = port;
  }

  public Socket accept() throws IOException {
    return new Socket();
  }

  public void close() throws IOException {
	  closed = true;
  }
  
  public boolean isClosed() {
	  return closed;
  }
  
  public int getLocalPort() {
	  return port;
  }
  
}
