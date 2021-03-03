package main;


public class Main {
	
	private String className;
	private String benchmark;
	
	private instrumenter.Driver instrumenterDriver;
	
	public Main(String c, String b) {
		this.className = c;
		this.benchmark = b;
		instrumenterDriver = new instrumenter.Driver(className, benchmark);		
	}
	
	public void start() {
		instrumenterDriver.run();		
	}

	public static void main(String[] args) {
		if(args.length==0)
	 	{
			System.err.println("Usage: java Driver [options] classname benchmark");
	 		System.exit(0);
	 	}
		
		Main m = new Main(args[0], args[1]);
		m.start();
	}
}