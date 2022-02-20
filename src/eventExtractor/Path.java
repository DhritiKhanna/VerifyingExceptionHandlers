package eventExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import gov.nasa.jpf.util.Pair;
import gov.nasa.jpf.vm.Envelope;
import gov.nasa.jpf.vm.Pi;

public class Path {
	
	private String className;
	private String testName;
	private HashMap<Integer, ArrayList<Envelope>> eventsAlongPath;
	private ArrayList<Pi> pisAlongPath;
	private HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>> varsToAliases;
	private String pathCondition; 
	
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public String getTestName() {
		return testName;
	}
	public void setTestName(String testName) {
		this.testName = testName;
	}
	public HashMap<Integer, ArrayList<Envelope>> getEventsAlongPath() {
		return eventsAlongPath;
	}
	public void setEventsAlongPath(HashMap<Integer, ArrayList<Envelope>> eventsAlongPath) {
		this.eventsAlongPath = eventsAlongPath;
	}
	public ArrayList<Pi> getPisAlongPath() {
		return pisAlongPath;
	}
	public void setPisAlongPath(ArrayList<Pi> pisAlongPath) {
		this.pisAlongPath = pisAlongPath;
	}
	public HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>> getVarsToAliases() {
		return varsToAliases;
	}
	public void setVarsToAliases(HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>> varsToAliases) {
		this.varsToAliases = varsToAliases;
	}
	public String getPathCondition() {
		return pathCondition;
	}
	public void setPathCondition(String pathCondition) {
		this.pathCondition = pathCondition;
	}
	public String toString() {
		String s = "\nObserved path: " + ": ";
		if(className != null) s += "Class Name: " +  className + "; ";
		if(testName != null) s += "Test Name: " + testName + "\n\n";
		s += "Path Condition: " + pathCondition + "\n\n";
		s += "Events: \n";
		Iterator<Integer> threads = eventsAlongPath.keySet().iterator();
		while(threads.hasNext()) {
			int threadId = threads.next();
			ArrayList<Envelope> eventsAlongThread = eventsAlongPath.get(threadId);
			s += "ThreadID: " + threadId + "\n";
			if(eventsAlongThread != null) {
				Iterator<Envelope> itEventsAlongThread = eventsAlongThread.iterator();
				while(itEventsAlongThread.hasNext()) {
					s += itEventsAlongThread.next().toString() + "\n";
				}
				s += "\n";
			}
		}
		s += "\n\n";
		s += "Pis: \n";
		Iterator<Pi> itPisAlongPath = pisAlongPath.iterator();
		while(itPisAlongPath.hasNext()) {
			s += itPisAlongPath.next().description() + "\n";
		}
		s += "\n\n";
		s += "Vars To Aliases: \n";
		Iterator<Pair<String, String>> vars = varsToAliases.keySet().iterator();
        while(vars.hasNext()) {
            Pair<String, String> var = vars.next();
            s += var._1 + " " + var._2 + " " + varsToAliases.get(var) + "\n";
        }
		
		s += "\n--------------\n";
		return s;
	}
}
