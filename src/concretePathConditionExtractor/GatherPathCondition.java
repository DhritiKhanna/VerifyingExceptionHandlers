package concretePathConditionExtractor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.DCMPG;
import gov.nasa.jpf.jvm.bytecode.DCMPL;
import gov.nasa.jpf.jvm.bytecode.FCMPG;
import gov.nasa.jpf.jvm.bytecode.FCMPL;
import gov.nasa.jpf.jvm.bytecode.IfInstruction;
import gov.nasa.jpf.jvm.bytecode.LCMP;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.Pair;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadChoiceGenerator;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.choice.ThreadChoiceFromSet;
import main.Main;

/**
 * This class gathers the path conditions of each thread and the scheduling information (which thread got turn at the scheduling times)
 * during the concrete execution.
 * @author Dhriti
 *
 */
public class GatherPathCondition extends PropertyListenerAdapter implements PublisherExtension {
    
	private String target;
	private HashMap<Integer, ArrayList<Pair<String, Integer>>> filePositionConditionValueForThread;
	private ArrayList<Pair<Integer, Integer>> threadChoiceValueForStateID;
	
	GatherPathCondition(Config conf, JPF jpf) {
		target = conf.getString("target");
		filePositionConditionValueForThread = new HashMap<Integer, ArrayList<Pair<String, Integer>>>();
		threadChoiceValueForStateID = new ArrayList<Pair<Integer, Integer>>();
	}
	
	public void executeInstruction(VM vm, ThreadInfo ti, Instruction insnToExecute) {
		//System.out.println("Next Instruction to be executed: " + insnToExecute);
	}
	
	public void instructionExecuted(VM vm, ThreadInfo currentThread, Instruction nextInstruction, Instruction executedInstruction) {
    	
    	if (vm.getSystemState().isIgnored())
    		return;
    	
		Instruction lastIns = executedInstruction;
		MethodInfo methodInfo = lastIns.getMethodInfo();
		System.out.println("~~~~~~~~~~~~~~~~~~~ Instruction: " + lastIns + " from method: " + methodInfo.getFullName());
		// TODO: Checking the instructions to scan on the package granularity level.
    	if(!(Main.contains(methodInfo.getClassInfo().getPackageName()) || methodInfo.getClassInfo().getName().equals(target)))
    		return;

    	//System.out.println("~~~~~~~~~~~~~~~~~~~ Instruction: " + lastIns + " from method: " + methodInfo.getFullName());
    		
		ThreadInfo ti = currentThread;	// Information regarding the thread that executed the last instruction
		String tid = ti.getName();
		MethodInfo mi = lastIns.getMethodInfo(); // Information regarding the method to which the last instruction belongs
		
		if (mi == null) {
			System.out.println("There might be a problem, MethodInfo is not set for the last instruction!");
			return;
		}

		// We always need to check whether the instruction is completed or not in order to avoid transition breaks
		// These breaks sometimes forces an instruction to be re-executed
		if (lastIns.isCompleted(ti)) {
			if(lastIns instanceof IfInstruction) {
				IfInstruction ifInstruction = (IfInstruction) lastIns;
				System.out.println("This is an if instruction: " + ifInstruction.getConditionValue() 
						 			+ " at line number: " + ifInstruction.getLineNumber() +  " " + ifInstruction.getFileLocation());
				Pair<String, Integer> p;
				if(ifInstruction.getConditionValue())
					p = new Pair<String, Integer>(ifInstruction.getFileLocation(), 1);
				else
					p = new Pair<String, Integer>(ifInstruction.getFileLocation(), 0);
				ArrayList<Pair<String, Integer>> filePositionConditionValue = filePositionConditionValueForThread.get(ti.getId());
				if(filePositionConditionValue == null)
					filePositionConditionValue = new ArrayList<Pair<String, Integer>>();
				filePositionConditionValue.add(p);
				filePositionConditionValueForThread.put(ti.getId(), filePositionConditionValue);
			}
			else if (lastIns instanceof LCMP  || 
					lastIns instanceof FCMPG || 
					lastIns instanceof FCMPL || 
					lastIns instanceof DCMPG || 
					lastIns instanceof DCMPL) {
				int result = currentThread.getModifiableTopFrame().peek(); // Result of the executed instruction
				Pair<String, Integer> p = new Pair<String, Integer>(lastIns.getFileLocation(), result);
				ArrayList<Pair<String, Integer>> filePositionConditionValue = filePositionConditionValueForThread.get(ti.getId());
				if(filePositionConditionValue == null)
					filePositionConditionValue = new ArrayList<Pair<String, Integer>>();
				filePositionConditionValue.add(p);
				filePositionConditionValueForThread.put(ti.getId(), filePositionConditionValue);
			}
		}
	}
	
    @Override
    public void searchFinished(Search search) {
    	// End state reached
        try {
            FileOutputStream fileOut = new FileOutputStream("./temp/pathCondition.txt");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(filePositionConditionValueForThread);  
            out.close();
            fileOut.close();
        }
        catch(IOException e) {System.out.println(e);}
        
        // Store the schedule choices made in the concrete execution
    	try {
            FileOutputStream fileOut = new FileOutputStream("./temp/threadSchedule.txt");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(threadChoiceValueForStateID);  
            out.close();
            fileOut.close();
        }
        catch(IOException e) {
        	System.out.println(e);
        	e.printStackTrace();
		}
    }
    
    @Override
    public void stateAdvanced(Search search)  {
      SystemState ss = search.getVM().getSystemState();
      
      ChoiceGenerator<?> cg = ss.getChoiceGenerator();
      if(cg instanceof ThreadChoiceGenerator) {
    	  System.out.println("State advanced; <stateid, runningthreadID>: " + ss.getId() + " " + ((ThreadChoiceFromSet)cg).getNextChoice().getId());
    	  // Store the state ID and the thread choice taken in that state
    	  threadChoiceValueForStateID.add(new Pair<Integer, Integer>(ss.getId(), ((ThreadChoiceFromSet)cg).getNextChoice().getId()));
      }
    }
    
    @Override
    public void searchStarted(Search search) {
      SystemState ss = search.getVM().getSystemState();
      
      // Store the state ID and the thread choice taken in that state
      threadChoiceValueForStateID.add(new Pair<Integer, Integer>(ss.getId(), ss.getExecThread().getId()));
    }
}