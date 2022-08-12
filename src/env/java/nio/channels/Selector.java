package env.java.nio.channels;

import java.io.IOException;
import env.java.nio.channels.SelectionKey;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class Selector {

	String Selector;
	
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public static Selector open() throws IOException {
		return new Selector();
	}

	
	public boolean isOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	
	public Set<SelectionKey> keys() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public SelectorProvider provider() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public int select() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public int select(long timeout) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public int selectNow() throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	
	public Set<SelectionKey> selectedKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	
	public java.nio.channels.Selector wakeup() {
		// TODO Auto-generated method stub
		return null;
	}

}
