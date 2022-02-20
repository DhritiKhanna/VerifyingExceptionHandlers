package eventExtractor;

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
import gov.nasa.jpf.vm.Envelope;
import gov.nasa.jpf.vm.Pi;

import gov.nasa.jpf.util.Pair;

public class Driver {
	
	String testcase;
	Path path;
	
	public Driver() {}
	public Driver(String testcase) {
		this.testcase = testcase;
		path = new Path();
	}
	
	public void run(HashMap<Integer, ArrayList<Pair<String, Integer>>> concretePathCondition, ArrayList<Pair<Integer, Integer>> threadSchedule) { 
		runJPF(concretePathCondition, threadSchedule);
		storeSymbolicInformation();
	}
	private static int pathId=0;
	
	/**
	 * Runs the symbolic executor JPF
	 */ 
	public void runJPF(HashMap<Integer, ArrayList<Pair<String, Integer>>> concretePathCondition, ArrayList<Pair<Integer, Integer>> threadSchedule) { 
		// Append Concrete Path Condition in the Config file
		// The concrete path condition is in the form of pairs of string, boolean: file position and the truth value
		String jpfFileName = "temp/TestDriver" + ".jpf";
		try {
			FileWriter writer = new FileWriter(jpfFileName, true);
			BufferedWriter bufferedWriter = new BufferedWriter(writer);
			
			StringBuilder str = new StringBuilder("");
			if(concretePathCondition != null) {
				Iterator<Integer> threadIdIt = concretePathCondition.keySet().iterator();
				while(threadIdIt.hasNext()) {
					int threadId = threadIdIt.next();
					str.append(threadId + "@");
					ArrayList<Pair<String, Integer>> filePositionConditionValue = concretePathCondition.get(threadId);
					Iterator<Pair<String, Integer>> filePositionConditionValueIt = filePositionConditionValue.iterator();
					while(filePositionConditionValueIt.hasNext()) {
						Pair<String, Integer> pair = filePositionConditionValueIt.next();
						str.append(/*pair._1 + ":" +*/ pair._2 + ";");
					}
					str.append(":"); // Separator for different threads
				}
			}
			System.out.println("Property concrete_path_condition: " + str);
			bufferedWriter.write("concrete_path_condition=" + str + "\n");
			
			str = new StringBuilder("");
			if(threadSchedule != null) {
				Iterator<Pair<Integer, Integer>> threadScheduleIt = threadSchedule.iterator();
				while(threadScheduleIt.hasNext()) {
					Pair<Integer, Integer> pair = threadScheduleIt.next();
					str.append(pair._1 + ":" + pair._2 + ";");
				}
			}
			System.out.println("Property thread_schedule: " + str);
			bufferedWriter.write("thread_schedule=" + str + "\n");
			
			bufferedWriter.write("symbolic_execution=true");
			
			bufferedWriter.close();
		}
		catch(IOException e) {System.out.println("Some problem opening the jpf file to write concrete path condition!");} 
		
		String outFileName = "temp/TestDriverSymbolic.txt";
		String [] props = {"temp/TestDriver.jpf"};
		Config c = JPF.createConfig(props);
		JPF jpf = new JPF(c);
		EventListener eventListener = new EventListener(c, jpf);
		//SymbolicListener symbcListener = new SymbolicListener(c, jpf);
		jpf.addListener(eventListener);
		//jpf.addListener(symbcListener);
		
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
        catch(Exception e) { System.out.println(e); }
        finally {
        	System.setOut(originalOut); // Reset to original
        }
	}
	
	// This method will be based on the information eventListener generates.
	@SuppressWarnings("unchecked")
	public void storeSymbolicInformation() {        
        HashMap<String, Object> observedPath = null;
        HashMap<Integer, ArrayList<Envelope>> eventsAlongPath = null;
        ArrayList<Pi> pisAlongPath = null;
        HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>> varsToAliases = null;
        String pathCondition = "";

        try {
        	FileInputStream fileIn = new FileInputStream("./pc.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            observedPath = (HashMap<String, Object>) in.readObject();
            in.close();
            fileIn.close();
            // Delete this file after use. JPF will create and fill it again.
            File file = new File("./pc.ser");
            file.delete();
        }
        catch(FileNotFoundException f) {System.out.println(f); return;}
        catch(IOException e) {System.out.println(e);}
        catch(ClassNotFoundException e) {System.out.println(e);}
		
        eventsAlongPath = (HashMap<Integer, ArrayList<Envelope>>) observedPath.get("EventsAlongPath");
        pisAlongPath = (ArrayList<Pi>) observedPath.get("PisAlongPath");
        varsToAliases = (HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>>) observedPath.get("VarsToAliases");
        pathCondition = (String) observedPath.get("PC");
        
        StringTokenizer str = new StringTokenizer(testcase, "::");
		String className = str.nextToken();
		String testName = str.nextToken();
		
    	path.setClassName(className);
    	path.setTestName(testName);
    	path.setEventsAlongPath(eventsAlongPath);
    	path.setPisAlongPath(pisAlongPath);
    	path.setVarsToAliases(varsToAliases);  
    	path.setPathCondition(pathCondition);
	}
	
	public Path getPath() {
		return path;
	}
}