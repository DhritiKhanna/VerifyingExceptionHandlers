package main;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.microsoft.z3.Model;

import eventExtractor.Path;
import util.Pair;
import util.RunningCommand;
import verifier.SolverLogic;

public class Main {
	
	public static String className;
	public static String benchmark; // Short name of the benchmark like POOL, LUCN etc. 	
	public static ArrayList<String> classDependencies; // Since we do not analyze full library, this DS will contain the list of classes
	public static boolean commandline = false;
	
	private int [] linenos; // These are the user-provided line numbers that we use in the 'if' instruction: 
							// if(line number == any of these linenos) 
							// 		throw exception. 
							// These line numbers will let us have a better control over the function calls 
							// we need to consider when throwing the exceptions.
	private instrumenter.Driver instrumenterDriver;
	private eventExtractor.Driver eventExtractorDriver;
	private concretePathConditionExtractor.Driver pcExtractorDriver;
	
	/** This is what you should provide in the command line: --className test.Test --benchmark Test --linenos 92,93,23
		For Jeromq: --className test.JermomqTest --benchmark JeromqTest --linenos
	*/
	public Main(String [] args) {
		Options options = new Options();

        Option opt = new Option("cn", "className", true, "Name of the CUT");
        opt.setRequired(true);
        options.addOption(opt);

        opt = new Option("bm", "benchmark", true, "Short name of the benchmark like POOL, LUCN etc.");
        opt.setRequired(true);
        options.addOption(opt);

        opt = new Option("ln", "linenos", true, "Line numbers that we use in the 'if' instruction (separated by commas)");
        opt.setRequired(true);
        options.addOption(opt);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Verifying Exception Handlers", options);

            System.exit(1);
        }

        className = cmd.getOptionValue("className");
        benchmark = cmd.getOptionValue("benchmark");
        if(cmd.hasOption("linenos")) {
        	String stringLineNos = cmd.getOptionValue("linenos");
        	StringTokenizer str = new StringTokenizer(stringLineNos, ",");
        	linenos = new int[str.countTokens()];
        	int i = 0; 
        	while(str.hasMoreTokens()) {
        		String s = str.nextToken();
        		linenos[i] = Integer.parseInt(s);
        		i++;
        	}
        }
		
		// Read the dependencies of the cut from the file
		classDependencies = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader("classDependencies.txt"));
			String dependency = "";
			while((dependency=br.readLine()) != null) {
				if(dependency.startsWith("#"))
					continue;
				classDependencies.add(dependency);
			}
		} catch(IOException e) { e.printStackTrace(); }
	}
	
	public static boolean contains(String name) {
//		if(name.startsWith("com.sun.mail.imap"))
//			return true;
//		if(name.startsWith("org.eclipse.paho.client.mqttv3"))
//			return true;
		if (classDependencies==null || classDependencies.size()==0)
			return false;
    	
		Iterator<String> classesBeingAnalyzedIt = classDependencies.iterator();
    	while(classesBeingAnalyzedIt.hasNext()) {
    		if(name.contains(classesBeingAnalyzedIt.next()))
    			return true;
    	}
    	return false;
    }	
	
	public void start() {
		
		// 0. Copy all the class files from folder bin into binInstrumented. The instrumentation that runs in step 1
		// will output the instrumented files into sootOutput and we are going to copy them into binInstrumented.
		// After step 1, we will be making use of the files present in binInstrumented.
		try {
			String cmd = "cp -R /home/dhriti/Dropbox/new-workspace/VerifyingExceptionHandlers/bin/. /home/dhriti/Dropbox/new-workspace/VerifyingExceptionHandlers/binInstrumented/.";
			Runtime run = Runtime.getRuntime();
			Process pr = run.exec(cmd);
			pr.waitFor();
			BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
			String line = "";
			while ((line=buf.readLine())!=null) {
				System.out.println(line);
			}
		}
		catch(IOException e) {
			System.out.println(e);
		}
		catch(InterruptedException e) {
			System.out.println(e);
		}
		
		// 1. Instrument the library with the throw instructions at appropriate locations
		// Utilizing this phase to also extract the shared variables of the class, these shared variables are required in solver logic. 
		instrumenterDriver = new instrumenter.Driver(linenos);
		ArrayList<Pair<String, String>> sharedVariables = instrumenterDriver.run();
		System.out.println("\n\n********* Instrumented\n\n");
		
		//ClassLoader loader = Main.class.getClassLoader();
        //System.out.println(loader.getResource(className.replace('.', '/')+".class"));
		
		// From this point onwards we are supposed to run everything on the classes found in binInstrumented and not bin.
		String classPath = System.getProperty("java.class.path").replace("bin", "binInstrumented");
	
		/* 2. Capture the events by running the test cases, the names of which are given by the user in a file testcases.txt
		 *    The user must specify the test names like this: FQclassName::testName. This test case must call the library's methods
		 *    We are running the test case symbolically to capture the path condition and the PC will only be available if the variables
		 *    are created symbolic.
		 *    Hence, although we run the code symbolically, we make it follow the same path the concrete execution did.
		 *    And we stop at just a single symbolic execution: no backtracking and all. 
		 *    Caveat: We have assumed that the concrete execution will go over the function calls where we have to mock the exceptions
		 *    However, this may not be always true. Therefore we may need complex methodology like changing the path of the execution etc. 
		 *    For this we may need to refer to the papers like Malavika Samak's work on directed synthesis.
		 */
		try {
			BufferedReader br = new BufferedReader(new FileReader("testcases.txt"));
			String testcase = "";
			while((testcase=br.readLine()) != null) {
				if(testcase.startsWith("#"))
					continue;
				
				pcExtractorDriver = new concretePathConditionExtractor.Driver(testcase);
				eventExtractorDriver = new eventExtractor.Driver(testcase);
				
				// Collect the concrete path condition by running the program through JPF and tracking every if condition
				System.out.println("\n>>>>>>>>>>>>>>>>>> Collecting the concrete path condition of test case: " + testcase);
				pcExtractorDriver.run();
				
				// The following code snippet is to test if the test case gets stuck in deadlock --> For debugging.
				/*System.out.println("Executing TestDriver");
				System.out.flush();
		        String command = "java -cp " + ".:binInstrumented" + " " + "TestDriver" + " -d binInstrumented";
				try {
					RunningCommand.runProcess(command);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				
				
				// Run the test case through the eventListener, so as to capture the events
				System.out.println("\n>>>>>>>>>>>>>>>>>> EventListener progressing on: " + testcase);
				eventExtractorDriver.run(pcExtractorDriver.getConditionValueForFilePosition(), pcExtractorDriver.getThreadChoiceValueForStatID());
				// Print the path you have collected. See the toString method of class Path to see what things will get printed.
//				Path path = eventExtractorDriver.getPath();
//				System.out.println(path);
				
				// Verify the collected trace
//				SolverLogic solverLogic = new SolverLogic(path, sharedVariables);
//				Model model = null;
//				try {
//					model = solverLogic.checkCompatibility();
//				} catch(NullPointerException e) {
//					e.printStackTrace();
//				}
//				if(model == null)
//					System.out.println("Unsatisfiable for " + path);
//				else {
//					System.out.println("Satisfiable for " + path);
//				}
			}
			br.close();
		} catch(IOException e) { e.printStackTrace(); }  
	}

	public static void main(String[] args) {
		
		long millis=System.currentTimeMillis();  
		java.util.Date date=new java.util.Date(millis); 
		System.out.println(date);  
		
		if(args.length==0) {
			System.err.println("Usage: java Driver [options] classname benchmark");
	 		System.exit(0);
	 	}
		
		Main m = new Main(args);
		m.start();
		
		millis=System.currentTimeMillis();  
		date=new java.util.Date(millis);  
		System.out.println(date);  
	}
}