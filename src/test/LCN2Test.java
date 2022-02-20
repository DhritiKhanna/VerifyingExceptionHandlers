package test;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;

class MyDirectory extends Directory {

	@Override
	public String[] listAll() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteFile(String name) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long fileLength(String name) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public IndexOutput createOutput(String name, IOContext context) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void sync(Collection<String> names) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void renameFile(String source, String dest) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IndexInput openInput(String name, IOContext context) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Lock makeLock(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		
	}	
}

public class LCN2Test {
	
	public void testcase1() throws IOException {
		
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		
		Runnable task1 = () -> {
			Directory directory = new MyDirectory();
			IndexWriter writer;
			try {
				writer = new IndexWriter(directory, new IndexWriterConfig(null));
				Document doc = new Document();
				doc.add(new SortedNumericDocValuesField("dv", 5)); 
				writer.addDocument(doc);
				writer.addDocument(new Document());
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        };
        
        Runnable task2 = () -> {
        	Directory directory = new MyDirectory();
			IndexWriter writer;
            try {  			
    			writer = new IndexWriter(directory, new IndexWriterConfig(null));
            	            	
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
        };
        
        executorService.submit(task1);
        executorService.submit(task2);

        executorService.shutdown();	 
	}
}