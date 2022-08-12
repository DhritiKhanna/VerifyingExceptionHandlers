package env.java.nio.channels;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import env.java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;

public class SelectableChannel {
	
	public Object blockingLock() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public java.nio.channels.SelectableChannel configureBlocking(boolean block) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	
	public boolean isBlocking() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public boolean isRegistered() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public SelectionKey keyFor(Selector sel) {
		// TODO Auto-generated method stub
		return null;
	}

	
	public SelectorProvider provider() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
		// TODO Auto-generated method stub
		return null;
	}

	
	public int validOps() {
		// TODO Auto-generated method stub
		return 0;
	}

	
	protected void implCloseChannel() throws IOException {
		// TODO Auto-generated method stub
		
	}

	public boolean isOpen() {
		return false;
	}

}
