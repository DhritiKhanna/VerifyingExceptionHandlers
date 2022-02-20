package test;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPHandler;
import com.sun.mail.test.TestServer;

public class JVMLTest {
	
	private static final String utf8Folder = "test\u03b1";
	
	public void testcase1() {
		
		//IMAPHandler handler = new IMAPHandler();
		//TestServer server = null;
        try {
            //server = new TestServer(handler);
            //server.start();

            final Properties properties = new Properties();
            properties.setProperty("mail.imap.host", "localhost");
            properties.setProperty("mail.imap.port", "" + 4500 /*server.getPort()*/);
            
            
            final Session session = Session.getInstance(properties);
            //session.setDebug(true);

            final Store store = session.getStore("imap");
            store.connect("dhriti", "khanna");
            
            final IMAPFolder folder = (IMAPFolder) store.getFolder(utf8Folder);
            
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            Runnable task1 = () -> {
                try {
                	System.out.println("Calling close");
					folder.close(true); // This calls waitIfIdle().
				} catch (MessagingException e) {
					e.printStackTrace();
				}
            };

            Runnable task2 = () -> {
                try {
                	System.out.println("Calling idle");
					folder.idle(true); // This waits forever.
				} catch (MessagingException e) {
					e.printStackTrace();
				} 
            };
            
            executorService.submit(task1);
            executorService.submit(task2);

            executorService.shutdown();	   
            
            store.close();
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            /*if (server != null) {
                server.quit();
            }*/
        }
	}
	
	public static void main(String[] args) {
		new JVMLTest().testcase1();
	}
}