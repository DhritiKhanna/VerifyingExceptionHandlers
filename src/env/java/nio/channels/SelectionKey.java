package env.java.nio.channels;

import env.java.nio.channels.SelectableChannel;
import env.java.nio.channels.Selector;
//import zmq.poll.PollItem;
//import zmq.poll.Poller;

public class SelectionKey {

	
	public static final int OP_READ = 1;
	public static final int OP_WRITE = 4;
	public static final int OP_CONNECT = 8;
	public static final int OP_ACCEPT = 16;

	private volatile Object attachment = null;

	public void cancel() {
		// TODO Auto-generated method stub
		
	}

	
	public SelectableChannel channel() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public int interestOps() {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public java.nio.channels.SelectionKey interestOps(int ops) {
		// TODO Auto-generated method stub
		return null;
	}

	
	public boolean isValid() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public int readyOps() {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public Selector selector() {
		// TODO Auto-generated method stub
		return null;
	}

	public void attach(Object attachment) {
		this.attachment = attachment;
	}


	public Object attachment() {
		// TODO Auto-generated method stub
		return attachment;
	}


	public boolean isAcceptable() {
		// TODO Auto-generated method stub
		return false;
	}


	public boolean isConnectable() {
		// TODO Auto-generated method stub
		return false;
	}


	public boolean isWritable() {
		// TODO Auto-generated method stub
		return false;
	}


	public boolean isReadable() {
		// TODO Auto-generated method stub
		return false;
	}
}