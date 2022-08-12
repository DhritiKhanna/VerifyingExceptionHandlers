package concretePathConditionExtractor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.util.Pair;
import main.Main;
import util.ConcExecStateInfo;
import util.RunningCommand;

public class Driver {
	
	HashMap<Integer, ArrayList<Pair<String, Integer>>> filePositionConditionValueForThread;
	ArrayList<ConcExecStateInfo> threadChoiceValueForStateID;
	String testcase;
	
	public Driver() {}
	public Driver(String testcase) {
		this.testcase = testcase;
	}
	
	static {
		createJPF();
	}
	
	public void run() { 
		createDriver();
		runJPF();
		storePC();
		storeThreadSchedule();
	}
	
	public static final int DEFAULT_MIN_INT = 0;
	public static final int DEFAULT_MAX_INT = 20; 

	
	private ArrayList<String> cutAndDependencies;
	private static int pathId=0;
	
	/**
	 * Creates a Driver class for invoking the appropriate test case
	 */
	private void createDriver() {
		String testDriverFileName = "";
		if(!Main.commandline)
			testDriverFileName = "src/TestDriver.java";
		else
			testDriverFileName = "./testDriver/TestDriver.java";
		
		try {
			StringTokenizer str = new StringTokenizer(testcase, "::");
			String className = str.nextToken();
			String testName = str.nextToken();
			FileWriter writer = new FileWriter(testDriverFileName);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			bufferedWriter.write(//"package tests;\n"
					"public class TestDriver {\n\tpublic static void main(String[] args) {\n"
					+ "\t\t try {\n"  
					+ "\t\t\t " + className + " e = new " + className + "();" + "\n"
					+ "\t\t\t e." + testName + "();" + "\n"
					+ "\t\t } catch (RuntimeException e1) {\n" 
					+ "\t\t\t e1.printStackTrace();\n"
					+ "\t\t }\n"
					+ "\t\t catch (Exception e1) {\n" 
					+ "\t\t\t e1.printStackTrace();\n"
					+ "\t\t }\n"
					+ "\t }\n}");
			bufferedWriter.flush();
			writer.flush();
			bufferedWriter.close();
			writer.close();
			Thread.sleep(2000); // --> I want to wait for this file to be written properly, before invoking JPF on it.
		}
		catch(IOException e) { System.out.println(e); } 
		catch (InterruptedException e) { e.printStackTrace(); }
		
		// Compile the newly created driver  
		String classpath = "";
		String pathForJars = "";
		if(!Main.commandline) {
			classpath = ".:binInstrumented"; // Including binInstrumented, because the instrumented file is in sootOutput directory.
			pathForJars = "/home/dhriti/Dropbox/new-workspace/VerifyingExceptionHandlers/benchmarks/"+Main.benchmark+"/jars/";
		}
		else {
			classpath = ".:./binInstrumented"; // Including binInstrumented, because the instrumented file is in sootOutput directory.
			pathForJars = "./benchmarks/"+Main.benchmark+"/jars/";
		}
		File pathToJars = new File(pathForJars);
		String contents[] = pathToJars.list();
		for(int i=0; contents!=null && i<contents.length; i++) {
			classpath += ":" + pathToJars.toString()+"/"+contents[i];
	    }
		String command = "javac -cp " + classpath + " " + testDriverFileName + " -d sootOutput";
		//System.err.println("command: " + command);
		try {
			RunningCommand.runProcess(command);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	/**
	 * Creates a JPF file to invoke the test case runner
	 */
	private static void createJPF() {
		String jpfFileName = "temp/" + "TestDriver" + ".jpf";
		try {
			FileWriter writer = new FileWriter(jpfFileName);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			
			bufferedWriter.write("@using = jpf-nhandler" + "\n"); // Using jpf-nhandler
			bufferedWriter.write("target=" + "TestDriver" + "\n"); // --> TestDriver invokes the appropriate test case
			//bufferedWriter.write("nhandler.genSource = true" + "\n");
			//bufferedWriter.write("nhandler.delegateUnhandledNative = true" + "\n");
			bufferedWriter.write("nhandler.spec.delegate = java.net.PlainSocketImpl.*, java.net.DualStackPlainSocketImpl.*" + "\n");
			bufferedWriter.write("symbolic.method=main()" + "\n");
			bufferedWriter.write("symbolic.max_int=" + DEFAULT_MAX_INT + "\n");
			bufferedWriter.write("symbolic.min_int=" + DEFAULT_MIN_INT + "\n");
			String classpath = ".:binInstrumented";
			File pathToJars = new File("/home/dhriti/Dropbox/new-workspace/VerifyingExceptionHandlers/benchmarks/"+Main.benchmark+"/jars/");
			String contents[] = pathToJars.list();
			for(int i=0; contents!=null && i<contents.length; i++) {
				classpath += ":" + pathToJars.toString()+"/"+contents[i];
		    }
			bufferedWriter.write("classpath=" + classpath + "\n");
			// Rest of the variables are hard-wired
			bufferedWriter.write("symbolic.debug=true" + "\n");
			bufferedWriter.write("symbolic.lazy=false" + "\n");
			bufferedWriter.write("symbolic.dp=z3" + "\n");
			//bufferedWriter.write("symbolic.arrays=true" + "\n");
			bufferedWriter.write("symbolic.collect_constraints=true" + "\n");
			bufferedWriter.write("search.multiple_errors=true" + "\n");	
			//bufferedWriter.write("search.depth_limit=500" + "\n");
			bufferedWriter.write("cg.enumerate_random=true" + "\n");
			bufferedWriter.write("cg.randomize_choices=VAR_SEED" + "\n");
						
			StringBuilder str = new StringBuilder("");
			if(Main.classDependencies != null) {
				Iterator<String> classDependenciesIt = Main.classDependencies.iterator();
				while(classDependenciesIt.hasNext()) {
					str.append(classDependenciesIt.next() + ";");
				}
			}
			
			bufferedWriter.write("classDependencies=" + str + "\n");
			
			bufferedWriter.close();
			writer.close();
		} 
		catch (IOException e) {
			System.out.println(e);
		}
	}
	
	/**
	 * Runs the symbolic executor JPF
	 */ 
	private void runJPF() { 
		String outFileName = "temp/TestDriverConcrete.txt";
		String [] props = {"temp/TestDriver.jpf"};
		Config c = JPF.createConfig(props);
		JPF jpf = new JPF(c);
		//GatherPathCondition gatherPathCondition = new GatherPathCondition(c, jpf);
		SymbolicListener symbolicListener = new SymbolicListener(c, jpf);
		//jpf.addListener(gatherPathCondition);
		jpf.addListener(symbolicListener);
		
        PrintStream originalOut = System.out; // Save original out stream [optionally save System.err too].
        
        System.out.flush(); 
        
        try {
        	PrintStream fileOut = new PrintStream(outFileName); // Create a new file output stream.
        	System.setOut(fileOut); // Redirect standard out to file.
        } 
        catch (FileNotFoundException e) {
        	System.out.println(e); 
        }
        
        try {
        	jpf.run();
		} 
        catch(Exception e) {}
        finally {
        	System.setOut(originalOut); // Reset to original
        }
	}
	
	
	private void storePC() {
		try {
        	FileInputStream fileIn = new FileInputStream("./temp/pathCondition.txt");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            filePositionConditionValueForThread = (HashMap<Integer, ArrayList<Pair<String, Integer>>>) in.readObject();
            in.close();
            fileIn.close();
        }
		catch(IOException e) {
			System.err.println("*** pathCondition.txt file not found!");
		} 
		catch (ClassNotFoundException e) {
			System.err.println();
			e.printStackTrace();
		}
		// Display the path condition arraylist: for debugging
		System.out.print("Concrete Path Condition: ");
		if(filePositionConditionValueForThread != null) {
			Iterator<Integer> threadIdIt = filePositionConditionValueForThread.keySet().iterator();
			while(threadIdIt.hasNext()) {
				int threadId = threadIdIt.next();
				System.out.print(threadId + ": ");
				ArrayList<Pair<String, Integer>> filePositionConditionValue = filePositionConditionValueForThread.get(threadId);
				Iterator<Pair<String, Integer>> filePositionConditionValueIt = filePositionConditionValue.iterator();
				while(filePositionConditionValueIt.hasNext()) {
					Pair<String, Integer> pair = filePositionConditionValueIt.next();
					System.out.print("<" + pair._1 + ", " + pair._2 + "> ");
				}
				System.out.println();
			}
		}
		// Delete the file after you have read data from it.
		File f = new File("./temp/pathCondition.txt");
		f.delete();
	}
	
	private void storeThreadSchedule() {
		try {
        	FileInputStream fileIn = new FileInputStream("./temp/threadSchedule.txt");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            threadChoiceValueForStateID = (ArrayList<ConcExecStateInfo>) in.readObject();
            in.close();
            fileIn.close();
        }
		catch(IOException e) {
			System.err.println("*** threadSchedule.txt file not found!");
		} 
		catch (ClassNotFoundException e) {
			System.err.println();
			e.printStackTrace();
		}		
		// Display the schedule choices arraylist: for debugging
		System.out.print("Thread choices in storeThreadSchedule: ");
		if(threadChoiceValueForStateID != null) {
			Iterator<ConcExecStateInfo> threadChoiceValueForStateIDIt = threadChoiceValueForStateID.iterator();
			while(threadChoiceValueForStateIDIt.hasNext()) {
				ConcExecStateInfo c = threadChoiceValueForStateIDIt.next();
				System.out.print("<" + c.getStateID() + ", " + c.getThreadID() + ", " + c.getInstructionsExecutedInState() + "> ");
			}
		}
		// Delete the file after you have read data from it.
		File f = new File("./temp/threadSchedule.txt");
		f.delete();
	}
	
	public HashMap<Integer, ArrayList<Pair<String, Integer>>> getConditionValueForFilePosition() {
    	return filePositionConditionValueForThread;
    }
	
	public ArrayList<ConcExecStateInfo> getThreadChoiceValueForStatID() {
		return threadChoiceValueForStateID;
	}
}