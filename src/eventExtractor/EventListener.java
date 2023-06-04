package eventExtractor;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.PropertyListenerAdapter;
import gov.nasa.jpf.jvm.bytecode.ALOAD;
import gov.nasa.jpf.jvm.bytecode.ASTORE;
import gov.nasa.jpf.jvm.bytecode.ATHROW;
import gov.nasa.jpf.jvm.bytecode.GETFIELD;
import gov.nasa.jpf.jvm.bytecode.GETSTATIC;
import gov.nasa.jpf.jvm.bytecode.INVOKEINTERFACE;
import gov.nasa.jpf.jvm.bytecode.INVOKESPECIAL;
import gov.nasa.jpf.jvm.bytecode.INVOKEVIRTUAL;
import gov.nasa.jpf.jvm.bytecode.ISTORE;
import gov.nasa.jpf.jvm.bytecode.JVMLocalVariableInstruction;
import gov.nasa.jpf.jvm.bytecode.JVMStaticFieldInstruction;
import gov.nasa.jpf.jvm.bytecode.LSTORE;
import gov.nasa.jpf.jvm.bytecode.LockInstruction;
import gov.nasa.jpf.jvm.bytecode.MONITORENTER;
import gov.nasa.jpf.jvm.bytecode.MONITOREXIT;
import gov.nasa.jpf.jvm.bytecode.PUTFIELD;
import gov.nasa.jpf.jvm.bytecode.PUTSTATIC;
import gov.nasa.jpf.report.ConsolePublisher;
import gov.nasa.jpf.report.Publisher;
import gov.nasa.jpf.report.PublisherExtension;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.symbc.Operand;
import gov.nasa.jpf.symbc.SymbolicInstructionFactory;
import gov.nasa.jpf.symbc.concolic.PCAnalyzer;
import gov.nasa.jpf.symbc.numeric.PCChoiceGenerator;
import gov.nasa.jpf.symbc.numeric.PathCondition;
import gov.nasa.jpf.symbc.numeric.SymbolicConstraintsGeneral;
import gov.nasa.jpf.symbc.numeric.SymbolicInteger;
import gov.nasa.jpf.util.Pair;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Envelope;
import gov.nasa.jpf.vm.FieldInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;
import gov.nasa.jpf.vm.Pi;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.Types;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.bytecode.FieldInstruction;
import gov.nasa.jpf.vm.bytecode.ReturnInstruction;
import main.Main;
import util.ConcExecStateInfo;

public class EventListener extends PropertyListenerAdapter implements PublisherExtension {

    /*
     * Locals to preserve the value that was held by JPF prior to changing it in order to turn off state matching during
     * symbolic execution no longer necessary because we run spf stateless
     */
    private Map<String, MethodSummary> allSummaries;
    private String currentMethodName = "";
    
    private int currentlyExecutingTransitionInstructionCount; 

    // DK
    HashMap<String, Object> symbolicInfo; // Content of the information for its name
    HashMap<Integer, ArrayList<Envelope>> eventsAlongPathForThread;
    ArrayList<Pi> pisAlongPath;
    Stack<SystemState> path;
    Stack<SystemState> snapshotPath; // This is to be returned to the application whenever we encounter a wait or a notify.
    Stack<Operand> operands; // This stack tracks the expression to be formed for the upcoming STORE/if. This is so similar to the stack which JVM maintains.
    String methodBeingAnalysed, /*classBeingAnalysed,*/ target;
    ArrayList<String> methodNames;
    private static boolean nextInstructionMightBeAThrow = false;
    private static Instruction lastSavedIns;
    int heapVarCount; // The variables initialized through the heap have to be provided a temporary name till they are stored. This counter will be used to provide unique names to the heap variables like arrays. See the NEWARRAY insn for reference.
    String nameOfThread;
    Map<String, String> classNameToThreadName;
	ArrayList<ConcExecStateInfo> threadSchedule;
	HashMap<Integer, Stack<String>> methodMonitorForThreadID;
    // DK

    volatile private String operation;
    volatile private String detail;
    volatile private int depth;
    volatile private int id;

    public EventListener(Config conf, JPF jpf, ArrayList<ConcExecStateInfo> threadSchedule) {
    	
        jpf.addPublisherExtension(ConsolePublisher.class, this);
        allSummaries = new HashMap<String, MethodSummary>();
        symbolicInfo = new HashMap<String, Object>(); // DK
        path = new Stack<SystemState>();
        operands = new Stack<Operand>();
        eventsAlongPathForThread = new HashMap<Integer, ArrayList<Envelope>>();
        pisAlongPath = new ArrayList<Pi>();
        String temp = conf.getString("symbolic.method");
        StringTokenizer st1 = new StringTokenizer(temp, "(");
        methodBeingAnalysed = st1.nextToken(); // This is ClassName.methodName (same as methodInfo.getBaseName())
        target = conf.getString("target");
        // Extracting the class name
        /*int indexOfLastDot = methodBeingAnalysed.lastIndexOf(".");
        classBeingAnalysed = methodBeingAnalysed.substring(0, indexOfLastDot);*/
		methodNames = new ArrayList<String>();
		heapVarCount = 0;
		nameOfThread = null;
		classNameToThreadName = new HashMap<String, String>();
		this.threadSchedule = threadSchedule;
		methodMonitorForThreadID = new HashMap<Integer, Stack<String>>();
		currentlyExecutingTransitionInstructionCount = threadSchedule.get(0).getInstructionsExecutedInState(); // Executed instructions in the first transition
        // Some trivia
        //methodInfo.getFullName(); // Returns Test.test(II)V
        //methodInfo.getName(); // Returns test
        //methodInfo.getBaseName(); // Returns Test.test 
        //methodInfo.getClassInfo().getName(); // Returns Test
    }

    // Writes the method summaries to a file for use in another application
    private void writeTable(){
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("/home/dhriti/Desktop/outFile.txt"));
            Iterator it = allSummaries.entrySet().iterator();
            String line = "";
            while (it.hasNext()) {
                Map.Entry me = (Map.Entry)it.next();
                String methodName = (String)me.getKey();
                MethodSummary ms = (MethodSummary)me.getValue();
                line = "METHOD: " + methodName + "," +
                        ms.getMethodName() + "(" + ms.getArgValues() + ")," + 
                        ms.getMethodName() + "(" + ms.getSymValues() + ")";
                out.write(line);
                out.newLine();
                Vector<Pair> pathConditions = ms.getPathConditions();
                if (pathConditions.size() > 0) {
                    Iterator it2 = pathConditions.iterator();
                    while(it2.hasNext()){
                        Pair pcPair = (Pair)it2.next();
                        String pc = (String)pcPair._1;
                        String errorMessage = (String)pcPair._2;
                        line = pc;
                        if (!errorMessage.equalsIgnoreCase(""))
                            //line = line + "$" + errorMessage;
                        out.write(line);
                        out.newLine();
                    }
                }
            }
            out.close();
        } catch (Exception e) {}
    }
    
    @Override
    public void propertyViolated(Search search) {
        VM vm = search.getVM();

        ChoiceGenerator<?> cg = vm.getChoiceGenerator();
        if (!(cg instanceof PCChoiceGenerator)) {
            ChoiceGenerator<?> prev_cg = cg.getPreviousChoiceGenerator();
            while (!((prev_cg == null) || (prev_cg instanceof PCChoiceGenerator))) {
                prev_cg = prev_cg.getPreviousChoiceGenerator();
            }
            cg = prev_cg;
        }

        if ((cg instanceof PCChoiceGenerator) && ((PCChoiceGenerator) cg).getCurrentPC() != null) {
            PathCondition pc = ((PCChoiceGenerator) cg).getCurrentPC();
            String error = search.getLastError().getDetails();
            error = "\"" + error.substring(0, error.indexOf("\n")) + "...\"";
            // C: not clear where result was used here -- to review
            // PathCondition result = new PathCondition();
            // IntegerExpression sym_err = new SymbolicInteger("ERROR");
            // IntegerExpression sym_value = new SymbolicInteger(error);
            // result._addDet(Comparator.EQ, sym_err, sym_value);
            // solve the path condition, then print it
            // pc.solve();
            if (SymbolicInstructionFactory.concolicMode) { // TODO: cleaner
                SymbolicConstraintsGeneral solver = new SymbolicConstraintsGeneral();
                PCAnalyzer pa = new PCAnalyzer();
                pa.solve(pc, solver);
            } else
                pc.solve();

            Pair<String, String> pcPair = new Pair<String, String>(pc.toString(), error);// (pc.toString(),error);

            // String methodName = vm.getLastInstruction().getMethodInfo().getName();
            MethodSummary methodSummary = allSummaries.get(currentMethodName);
            if (methodSummary == null)
                methodSummary = new MethodSummary();
            methodSummary.addPathCondition(pcPair);
            allSummaries.put(currentMethodName, methodSummary);
            System.out.println("Property Violated: PC is " + pc.toString());
            System.out.println("Property Violated: result is  " + error);
            System.out.println("****************************");
            writeTable();
        }
    }

    private void printOperandStack() {
        Iterator<Operand> opIt = operands.iterator();
        while(opIt.hasNext()) {
            System.out.println(opIt.next());
        }
    }

    public void executeInstruction(VM vm, ThreadInfo currentThread, Instruction instructionToExecute) {
    }
    
    public void instructionExecuted(VM vm, ThreadInfo currentThread, Instruction nextInstruction, Instruction executedInstruction) {
    	    	
    	if (vm.getSystemState().isIgnored())
    		return;
    	
    	// Break the ongoing transition if the number of instructions have reached the count stored during concrete execution
    	if(currentThread.getExecutedInstructions() == currentlyExecutingTransitionInstructionCount) {
    		vm.getSystemState().breakTransAccToConcExec = true;
    		// Update currentlyExecutingTransitionInstructionCount
    		System.out.println("Breaking the transition " + currentlyExecutingTransitionInstructionCount + " " + currentThread.getExecutedInstructions());
    	}
    	
		Instruction lastIns = executedInstruction;
		MethodInfo methodInfo = lastIns.getMethodInfo();
		
    	if(!(Main.contains(methodInfo.getClassInfo().getName()) || methodInfo.getClassInfo().getName().equals(target)))
    		return;    		

    	ThreadInfo ti = currentThread;	// Information regarding the thread that executed the last instruction
		String tid = ti.getName();
		MethodInfo mi = lastIns.getMethodInfo(); // Information regarding the method to which the last instruction belongs
		
		if (mi == null) {
			System.out.println("[SymbiosisJPF] There might be a problem, MethodInfo is not set for the last instruction!");
			return;
		}

		// We always need to check whether the instruction is completed or not in order to avoid transition breaks
		// These breaks sometimes forces an instruction to be re-executed
		if (lastIns.isCompleted(ti)) {
			System.out.println(currentThread.getExecutedInstructions() + " " + currentThread.getId() + "~~~~~~~~~~~~~~~~~~~ Instruction: " + lastIns + " from method: " + methodInfo.getFullName());
    		
			int line = lastIns.getLineNumber();
			String file = lastIns.getFileLocation();

			//TODO: for some weird reason, it might happen that a thread executes an instruction after being terminated
			//if(threadsFinished.contains(tid)){
				//return;
			//}
			
			//account for calls to monitor inside <clinit> and <init> methods,
			//where t-main name has not yet been changed
			if(tid.equals("main"))
				tid = "0";
			
			//Handle reads
			if ((lastIns instanceof GETFIELD)) { 
				FieldInstruction getfieldIns = (FieldInstruction) lastIns;
				/*if(getfieldIns.getLastElementInfo() == null) {
					//TODO: System.out.println("["+getStatePathId(tid,file)+"] There might be a problem, field info is null!");
					System.out.println("There might be a problem, field info is null!");
					return;
				}*/
				//log read event
	            String varName = getfieldIns.getFieldInfo().getName();
	            String dataType = getfieldIns.getFieldInfo().getType();

	            System.out.println("A GETFIELD instruction: " + getfieldIns.getFieldInfo() + " " + ti.getTopFrame().isOperandRef() + " " + ti.getTopFrame().peek());
	            if(!operands.isEmpty()) {
		            Object o = operands.pop().getOperand();
		            if(o instanceof Pi) {
			            String classVar = ((Pi)o).getVarNameType()._1; // Pushed in the latest ALOAD operation
			            if(!path.isEmpty()) {
			                SystemState currState = path.pop();
			                // 1. Add the alias in the list of variables
			                //String latestAlias = classVar + "." + varName + Envelope.counter++;
			                //currState.addInVarsToAliases(classVar + "." + varName, dataType, latestAlias); // Sending the name, data type, and the alias of the variable being stored
			                Pair<String, Integer> latestAlias = currState.fetchLatestAlias(classVar+"."+varName, dataType);
			                // 2. Create a new Pi variable
			                Pi pi = new Pi(classVar + "." + varName, dataType);
			                pi.addVariable(latestAlias._1, latestAlias._2); // Add the latest written alias of this variable in Pi's variables to chose from
			                currState.addPi(pi);
			                // 3. Create a read event for pi and store it into the list of events
			                Envelope e = new Envelope(Envelope.LOAD, currentThread.getId());
		                    e.setLoadVariableName(pi.getPiName());
		                    currState.addEvent(e, currentThread.getId());
		                    pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
		                    
			                // 4. Mark variable as symbolic and push the variable on the operand stack
			                //operands.push(new Operand(Operand.OperandType.FIELD, pi));
		    	    		Symbolic.newSymbolic(dataType, pi.getPiName(), vm);
			                path.push(currState);
			            }
		            }
	            }
			}
			else if(lastIns instanceof GETSTATIC) {
	            JVMStaticFieldInstruction i = (JVMStaticFieldInstruction) lastIns;
	            String varName = i.getFieldInfo().getName();
	            String dataType = i.getFieldInfo().getType();
	            String className = i.getClassInfo().getName();
	            System.out.println("A GETSTATIC instruction: " + i.getFieldInfo().getName() + " " + i.getFieldInfo().getType() + " " + i.getClassInfo().getName());
	            if(!path.isEmpty()) {
	                SystemState currState = path.pop();
	                // 1. Add the alias in the list of variables
	                //String latestAlias = className + "." + varName + Envelope.counter++;
	                //currState.addInVarsToAliases(className + "." + varName, dataType, latestAlias); // Sending the name, data type, and the alias of the variable being stored
	                Pair<String, Integer> latestAlias = currState.fetchLatestAlias(className+"."+varName, dataType);
	                // 2. Create a new Pi variable
	                Pi pi = new Pi(className+"."+varName, dataType);
	                pi.addVariable(latestAlias._1, latestAlias._2); // Add the latest written alias of this variable in Pi's variables to chose from
	                currState.addPi(pi);
	                // 3. Create a read event for pi and store it into the list of events
	                Envelope e = new Envelope(Envelope.LOAD, currentThread.getId());
                    e.setLoadVariableName(pi.getPiName());
                    currState.addEvent(e, currentThread.getId());
                    pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
	                // 4. Push the variable on the operand stack
	                //operands.push(new Operand(Operand.OperandType.FIELD, pi));
	                Symbolic.newSymbolic(dataType, pi.getPiName(), vm);
	                path.push(currState);
	            }
	        }
			else if(lastIns instanceof ALOAD) {
				JVMLocalVariableInstruction i = (JVMLocalVariableInstruction) lastIns; 
	            System.out.println("An ALOAD instruction: " + i.getLocalVarInfo() + " " + ti.getTopFrame().peek());
	            //ti.getTopFrame().printOperands(System.out);
	            if(i.getLocalVarInfo() != null) { // A store variable might be null because it is not used after being stored in further program
	                String varName = i.getLocalVarInfo().getName();
	                String dataType = i.getLocalVarInfo().getType();
	                
    				if(!path.isEmpty()) {
                        SystemState currState = path.pop();
                        // 1. Add the alias in the list of variables
                        //String latestAlias = "" + i.getLocalVarInfo().getName() + Envelope.counter++;
                        //currState.addInVarsToAliases(varName, dataType, latestAlias); // Sending the name, data type, and the alias of the variable being stored
                        Pair<String, Integer> latestAlias = currState.fetchLatestAlias(varName, dataType);
                        // 2. Create a new Pi variable
                        Pi pi = new Pi(varName, dataType);
                        pi.addVariable(latestAlias._1, latestAlias._2); // Add the latest written alias of this variable in Pi's variables to chose from
                        currState.addPi(pi);
                        // 3. Create a read event for pi and store it into the list of events
		                Envelope e = new Envelope(Envelope.LOAD, currentThread.getId());
	                    e.setLoadVariableName(pi.getPiName());
	                    currState.addEvent(e, currentThread.getId());
	                    pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
                        // 3. Mark variable as symbolic and push the variable on the operand stack
                        operands.push(new Operand(Operand.OperandType.REFERENCE, pi));
                        Symbolic.newSymbolic(dataType, pi.getPiName(), vm);
	    	    		path.push(currState);
                    }
	            }
			}
			//Handle writes
			else if (lastIns instanceof PUTFIELD) {
				FieldInstruction putfieldIns = (FieldInstruction) lastIns;
				ElementInfo ei = putfieldIns.getLastElementInfo();
				
				if(ei == null){
					//System.out.println("["+getStatePathId(tid,file)+"] There might be a problem, field info is null!");
					System.out.println("There might be a problem, field info is null!");
					return;
				}
//				Object valueObj = extractWrittenValue(putfieldIns, ei, vm);
//				System.out.println(valueObj);
				
	            String varName = putfieldIns.getFieldInfo().getName();
	            String dataType = putfieldIns.getFieldInfo().getType();
	            System.out.println("A PUTFIELD instruction: " + putfieldIns.getFieldInfo());
	            
	            if(!operands.isEmpty()) {
		            Operand op = operands.pop(); // Pushed in the ALOAD instruction
		            String classVar = ((Pi) op.getOperand()).getVarNameType()._1; // Not the Pi's name, but the actual variable for which this Pi stands
		            System.out.println(classVar + " " + varName);
		            if(putfieldIns.getFieldInfo() != null) { // A store variable might be null because it is not used after being stored in further program
		                Pair<String, String> var = new Pair<String, String>(classVar+"."+varName, putfieldIns.getFieldInfo().getType());
		                if(!path.isEmpty()) {
		                    SystemState currState = path.pop();
		                    // 1. Create an event and store it into the list of events
		                    Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
		                    e.setStoreVariable(var._1);
		                    Object expr = extractWrittenValue(vm, putfieldIns, ei);
		                    e.setExpr(expr.toString());
		                    currState.addEvent(e, currentThread.getId());
		                    // 2. Add the alias of the stored variable in the list of variables
		                    currState.addInVarsToAliases(var._1, var._2, e.getStoreVariableName(), e.getClk()); // Sending the name, data type, and the alias of the variable being stored
		                    //System.out.println(e.getStoreVariableName() + " = " + expr);
		                    
		                    path.push(currState);
		                }
		            }
	            }
			}
			else if (lastIns instanceof PUTSTATIC) {
				FieldInstruction putfieldIns = (FieldInstruction) lastIns;
				ElementInfo ei = putfieldIns.getLastElementInfo();
				
	            JVMStaticFieldInstruction i = (JVMStaticFieldInstruction) lastIns;
	            String varName = i.getFieldInfo().getName();
	            String dataType = i.getFieldInfo().getType();
	            String className = i.getClassInfo().getName();
	            
	            if(ei == null) { // A store variable might be null because it is not used after being stored in further program
	            	System.out.println("There might be a problem, field info is null!");
	            	return;
	            }
	            //System.out.println("A PUTSTATIC instruction: " /*+ i.getFieldInfo().getName() + " " + i.getFieldInfo().getType() + " " + i.getClassInfo().getName() + " " + operands.peek()*/);
	            
                Pair<String, String> var = new Pair<String, String>(className + "." + varName, dataType);
                if(!path.isEmpty()) {
                    SystemState currState = path.pop();
                    // 1. Create an event and store it into the list of events
                    Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
                    e.setStoreVariable(var._1);
                    Object expr = extractWrittenValue(vm, i, ei);
                    e.setExpr(expr.toString());
                    currState.addEvent(e, currentThread.getId());
                    // 2. Add the alias of the stored variable in the list of variables
                    currState.addInVarsToAliases(var._1, var._2, e.getStoreVariableName(), e.getClk()); // Sending the name, data type, and the alias of the variable being stored
                    
                    path.push(currState);
                }
			}
			else if(lastIns instanceof ISTORE || lastIns instanceof ASTORE || lastIns instanceof LSTORE) {
	            JVMLocalVariableInstruction i = (JVMLocalVariableInstruction) lastIns;
	            System.out.println("A store instruction: " + i.getLocalVarInfo());
	            if(i.getLocalVarInfo() != null) { // A store variable might be null because it is not used after being stored in further program
	                Pair<String, String> var = new Pair<String, String>(i.getLocalVarInfo().getName(), i.getLocalVarInfo().getType());
	                if(!path.isEmpty()) {
	                    SystemState currState = path.pop();
	                    // 1. Create an event and store it into the list of events
	                    Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
	                    e.setStoreVariable(var._1);
	                    if(!operands.isEmpty()) {
	                        Operand op = operands.pop();
	                        e.setExpr(op.getOperand().toString());
	                    }
	                    currState.addEvent(e, currentThread.getId());
	                    // 2. Add the alias of the stored variable in the list of variables
	                    currState.addInVarsToAliases(var._1, var._2, e.getStoreVariableName(), e.getClk()); // Sending the name, data type, and the alias of the variable being stored
	                    
	                    path.push(currState);
	                }
	            }
	            else
	                if(!operands.isEmpty())
	                    operands.pop();
			}
			// Synchronized block
			else if(lastIns instanceof MONITORENTER){
				LockInstruction i = (LockInstruction) lastIns;
	            System.out.println("Found a MONITORENTER");
	            // There must be an operand on top of the stack signifying the monitor variable
	            if(!operands.isEmpty()) {
	                Operand operand = operands.pop();
	                Pi p = (Pi) operand.getOperand(); // Pop this operand, and push a bit modified version of this event, where the operand type is changed to MONITOR
	                Operand newoperand = new Operand(Operand.OperandType.MONITOR, operand.getOperand());
	                operands.push(newoperand);

	                Pair<String, String> varNameType = p.getVarNameType();
	                // Lets say the monitor's name is 'r'. Check if there exists a variable named 'r_lock__' in varsToAliases.
	                // If there is no such var, add it.
	                // If there is, do not create a new one.
	                // Perform this STORE operation: r_lock__<new_alias> = Pi(r_lock__) - 1.
	                if(!path.isEmpty()) {
	                    SystemState currState = path.pop();
	                    Pi pi = new Pi(varNameType._1+"_lock__", varNameType._2);
	                    Pair<String, Integer> latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
	                    
	                    // This lock is being used for the first time, and hence, it must be assigned an initial value of 1
	                    if(latestAlias._1.equals(varNameType._1+"_lock__")) {
	                        Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
	                        e.setStoreVariable(varNameType._1+"_lock__");
	                        e.setStoreVariableType("int");
	                        e.setExpr("1");
	                        e.setClk(-1); // Assigning an initial value. This event is supposedly occurring before 'start'
	                        System.out.println("Storing this expression: " + e.getStoreVariableName() + " = " + "1");
	                        currState.addEvent(e, currentThread.getId());
	                        currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, e.getStoreVariableName(), e.getClk());
	                        pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
	                    }

	                    latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
	                    pi.addVariable(latestAlias._1, latestAlias._2);
	                    currState.addPi(pi);
	                    Envelope storeEvent = new Envelope(Envelope.STORE, currentThread.getId());
	                    storeEvent.setStoreVariable(varNameType._1+"_lock__");
	                    storeEvent.setStoreVariableType("int");
	                    storeEvent.setExpr("( ISUB " + pi + " 1 )");
	                    System.out.println("Storing this expression: " + storeEvent.getStoreVariableName() + " = " + pi + " - 1");
	                    currState.addEvent(storeEvent, currentThread.getId());
	                    pi.setClk(storeEvent.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.

	                    // Creating an assume event too, to specify the condition that the lock must be available
	                    Envelope assumeEvent = new Envelope(Envelope.ASSUME, currentThread.getId());
	                    assumeEvent.setAssume("( " + ">" + " " + pi.getPiName() + " " + "0" + " )");
	                    
	                    currState.addEvent(assumeEvent, currentThread.getId());

	                    // Add the alias of the stored variable in the list of variables
	                    currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, storeEvent.getStoreVariableName(), storeEvent.getClk()); // Sending the name, data type, and the alias of the variable being stored

	                    path.push(currState);
	                }
	            }  
			}
			else if(lastIns instanceof MONITOREXIT){
				LockInstruction i = (LockInstruction) lastIns;
	            System.out.println("Found a MONITOREXIT");
	            // There must be an operand somewhere on the stack signifying the monitor variable, pop till then.
	            if(!operands.isEmpty()) {
	                Operand operand = operands.pop();
	                Object o = operand.getOperand();
	                while(operand.getOperandType()!=Operand.OperandType.MONITOR && !operands.isEmpty()) {
	                    operand = operands.pop();
	                    o = operand.getOperand();
	                }
	                Pi p = (Pi) o;
	                Pair<String, String> varNameType = p.getVarNameType();
	                // Lets say there the monitor's name is 'r'. 
	                // There must exist a variable named 'r_lock__' in varsToAliases.
	                // Perform this STORE operation: r_lock__<new_alias> = Pi(r_lock__) + 1.
	                if(!path.isEmpty()) {
	                    SystemState currState = path.pop();
	                    Pi pi = new Pi(varNameType._1+"_lock__", varNameType._2);
	                    Pair<String, Integer> latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
	                    pi.addVariable(latestAlias._1, latestAlias._2);
	                    currState.addPi(pi);
	                    Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
	                    e.setStoreVariable(varNameType._1+"_lock__");
	                    e.setStoreVariableType("int");
	                    e.setExpr("( IADD " + pi + " 1 )");
	                    System.out.println("Storing this expression: " + e.getStoreVariableName() + " = " + pi + " + 1");
	                    currState.addEvent(e, currentThread.getId());
	                    pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
	                    // Add the alias of the stored variable in the list of variables
	                    currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, e.getStoreVariableName(), e.getClk()); // Sending the name, data type, and the alias of the variable being stored

	                    path.push(currState);
	                }
	            }
			}

			// Used to detect the run method of a new thread
			if(lastIns.isFirstInstruction()) {
				String methodName = ti.getEntryMethod().getName()+ti.getEntryMethod().getSignature();

				// Identifying when a new thread is starting in order to trace start events
				if(methodName.contains("run()V")) {
					if(!path.isEmpty()) {
	                    SystemState currState = path.pop();
	                    Envelope e = new Envelope(Envelope.START, currentThread.getId());
	                    e.setThreadName(ti.getName());
	                    currState.addEvent(e, currentThread.getId());
					}
				}
			}
			//VIRTUAL INVOCATIONS *** Used to detect start, join, lock, unlock, newCondition
			else if (lastIns instanceof INVOKEVIRTUAL){

				INVOKEVIRTUAL virtualIns = (INVOKEVIRTUAL) lastIns;
				String method = virtualIns.getInvokedMethod().getName();
				String invokedMethod = virtualIns.getInvokedMethodName();

				// Start method invocation
				if ((method.equals("start")) && (virtualIns.getInvokedMethod().getClassInfo().getName().equals("java.lang.Thread")))
				{
					if(!path.isEmpty()) {
	                    SystemState currState = path.pop();
	                    Envelope e = new Envelope(Envelope.START, currentThread.getId());
	                    e.setThreadName(ti.getName());
	                    currState.addEvent(e, currentThread.getId());
					}
				}
				//Join method invocation
				else if ((method.equals("join")) && (virtualIns.getInvokedMethod().getClassInfo().getName().equals("java.lang.Thread")))
				{
					System.out.println("["+ti.getName()+"] skip JOIN");
					/*StackFrame sf = ti.popFrame();
					Instruction nextIns = sf.getPC().getNext();*/
					vm.getCurrentThread().skipInstruction(); // TODO: find out what does this skipInstruction method do 
				}
				// Wait or notify method invocation
				else if (invokedMethod.equals("notify()V") || invokedMethod.equals("notifyAll()V") 
						|| invokedMethod.equals("wait()V") || invokedMethod.equals("wait(I)V") || invokedMethod.equals("wait(IJ)V")) {

					Envelope e = new Envelope(Envelope.WAIT, currentThread.getId()); 
					if(invokedMethod.equals("notify()V") || invokedMethod.equals("notifyAll()V"))
						e = new Envelope(Envelope.NOTIFY, currentThread.getId());					
					
					if(!operands.isEmpty()) {
		                Pi p = (Pi) operands.pop().getOperand();
		                Pair<String, String> pr = p.getVarNameType();
		                e.setWaitNotifyPi(p);
		                e.setWait_notifyMonitor(pr._1, pr._2); // Kind of redundant
		            }
		            SystemState currState = path.pop();
		            currState.addEvent(e, currentThread.getId());
		            path.push(currState);
	            }
	            // Lock method invocation
				else if(invokedMethod.equals("lock()V"))
				{
				}
				// Unlock method invocation
				else if(invokedMethod.equals("unlock()V"))
				{
				}
				else if(!virtualIns.getInvokedMethod().getClassName().startsWith("java."))
				{
					//System.out.println("-- method "+virtualIns.getInvokedMethod()+" is sync? "+virtualIns.getInvokedMethod().isSynchronized());
					// Synchronized method invocation
					if (virtualIns.getInvokedMethod().isSynchronized()){
						//System.out.println("--> SYNC METHOD ENTER: "+lastIns);
						ElementInfo obj = vm.getElementInfo(virtualIns.getCalleeThis(ti)); 
						String object = Integer.toHexString(obj.getObjectRef());
						Pair<String, String> varNameType = new Pair<String, String>(object, virtualIns.getInvokedMethod().getClassName()); //The method's lass is considered its type here
						// Lets say the monitor's name is 'r'. Check if there exists a variable named 'r_lock__' in varsToAliases.
		                // If there is no such var, add it.
		                // If there is, do not create a new one.
		                // Perform this STORE operation: r_lock__<new_alias> = Pi(r_lock__) - 1.
		                if(!path.isEmpty()) {
		                    SystemState currState = path.pop();
		                    Pi pi = new Pi(varNameType._1+"_lock__", varNameType._2);
		                    Pair<String, Integer> latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
		                    
		                    // This lock is being used for the first time, and hence, it must be assigned an initial value of 1
		                    if(latestAlias._1.equals(varNameType._1+"_lock__")) {
		                        Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
		                        e.setStoreVariable(varNameType._1+"_lock__");
		                        e.setStoreVariableType("int");
		                        e.setExpr("1");
		                        e.setClk(-1); // Assigning an initial value. This event is supposedly occurring before 'start'
		                        System.out.println("Storing this expression: " + e.getStoreVariableName() + " = " + "1");
		                        currState.addEvent(e, currentThread.getId());
		                        pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
		                        currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, e.getStoreVariableName(), e.getClk());
		                    }

		                    latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
		                    pi.addVariable(latestAlias._1, latestAlias._2);
		                    currState.addPi(pi);
		                    Envelope storeEvent = new Envelope(Envelope.STORE, currentThread.getId());
		                    storeEvent.setStoreVariable(varNameType._1+"_lock__");
		                    storeEvent.setStoreVariableType("int");
		                    storeEvent.setExpr("( ISUB " + pi + " 1 )");
		                    System.out.println("Storing this expression: " + storeEvent.getStoreVariableName() + " = " + pi + " - 1");
		                    currState.addEvent(storeEvent, currentThread.getId());
		                    pi.setClk(storeEvent.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.

		                    // Creating an assume event too, to specify the condition that the lock must be available
		                    Envelope assumeEvent = new Envelope(Envelope.ASSUME, currentThread.getId());
		                    assumeEvent.setAssume("( " + ">" + " " + pi.getPiName() + " " + "0" + " )");
		                    
		                    currState.addEvent(assumeEvent, currentThread.getId());

		                    // Add the alias of the stored variable in the list of variables
		                    currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, storeEvent.getStoreVariableName(), storeEvent.getClk()); // Sending the name, data type, and the alias of the variable being stored

		                    path.push(currState);
		                }

						//save monitor obj to store the unlock operation when returning from the sync method
						if(methodMonitorForThreadID.containsKey(ti.getId())){
							methodMonitorForThreadID.get(ti.getId()).push(object);
						}
						else{
							Stack<String> tmp = new Stack<String>();
							tmp.push(object);
							methodMonitorForThreadID.put(ti.getId(), tmp);
						}
					}
				}

			}//end if invokevirtual
			else if(lastIns instanceof INVOKEINTERFACE)
			{
				INVOKEINTERFACE interfaceIns = (INVOKEINTERFACE) lastIns;
				String invokedMethod = interfaceIns.getInvokedMethodName();
				if (invokedMethod.equals("await()V")||invokedMethod.equals("awaitNanos(J)V"))
				{
				}
				else if (invokedMethod.equals("signal()V")||invokedMethod.equals("signalAll()V"))
				{
				}
			}
			//RETURN INSTRUCTION *** Used to detect the end of synchronized methods
			else if (lastIns instanceof ReturnInstruction){
				ReturnInstruction genReturnIns = (ReturnInstruction) lastIns;
				MethodInfo me = genReturnIns.getMethodInfo();
				if(!me.getClassName().startsWith("java.")){
					if (me.isSynchronized() && methodMonitorForThreadID.containsKey(tid) && !methodMonitorForThreadID.get(tid).isEmpty()){
						//System.out.println("--> SYNC METHOD EXIT: "+lastIns);
						String object = methodMonitorForThreadID.get(ti.getId()).pop();
						Pair<String, String> varNameType = new Pair<String, String>(object, me.getClassName());
		                // Lets say there the monitor's name is 'r'. 
		                // There must exist a variable named 'r_lock__' in varsToAliases.
		                // Perform this STORE operation: r_lock__<new_alias> = Pi(r_lock__) + 1.
		                if(!path.isEmpty()) {
		                    SystemState currState = path.pop();
		                    Pi pi = new Pi(varNameType._1+"_lock__", varNameType._2);
		                    Pair<String, Integer> latestAlias = currState.fetchLatestAlias(varNameType._1+"_lock__", varNameType._2);
		                    pi.addVariable(latestAlias._1, latestAlias._2);
		                    currState.addPi(pi);
		                    Envelope e = new Envelope(Envelope.STORE, currentThread.getId());
		                    e.setStoreVariable(varNameType._1+"_lock__");
		                    e.setStoreVariableType("int");
		                    e.setExpr("( IADD " + pi + " 1 )");
		                    System.out.println("Storing this expression: " + e.getStoreVariableName() + " = " + pi + " + 1");
		                    currState.addEvent(e, currentThread.getId());
		                    pi.setClk(e.getClk()); // I have added the clk to the Pi variable also, because the path condition can have the pi variables and at that time we need the clock to create the event.
		                    // Add the alias of the stored variable in the list of variables
		                    currState.addInVarsToAliases(varNameType._1+"_lock__", varNameType._2, e.getStoreVariableName(), e.getClk()); // Sending the name, data type, and the alias of the variable being stored

		                    path.push(currState);
		                }
					}
				}
			}
			else if (lastIns instanceof INVOKESPECIAL) {
				nextInstructionMightBeAThrow = true;
				lastSavedIns = lastIns;
			}
			else if(nextInstructionMightBeAThrow) {
				nextInstructionMightBeAThrow = false;
				if(lastIns instanceof ATHROW) {
					INVOKESPECIAL invokeInst = (INVOKESPECIAL) lastSavedIns;
					System.out.println("Throw instruction of type: " + invokeInst.getInvokedMethodClassName());
				}
			}
    	}
		System.out.println("Next instruction: " + nextInstruction);
    }
    
    private Object extractWrittenValue(VM vm, FieldInstruction insn, ElementInfo ei) {
    	FieldInfo fi = insn.getFieldInfo();
		String type = fi.getType();
		Object attr = null;
		long lvalue = insn.getLastValue();

		if (insn instanceof PUTSTATIC){	
			attr = fi.getClassInfo().getStaticElementInfo().getFieldAttr(fi);
		}
		else if (insn instanceof PUTFIELD){
			ei = insn.getLastElementInfo();
			attr = ei.getFieldAttr(fi);			
		}
		Type itype = Type.typeToInteger(type);
		Object value = null;
		// Giving the proper shape to the read value.
		if (itype==Type.INT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				value = attr;
				//System.out.println("Writing symint "+((SymbolicInteger)((BinaryLinearIntegerExpression)value).getLeft())._max);
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.BOOLEAN){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				value = attr;
				//System.out.println("Writing symint "+((SymbolicInteger)((BinaryLinearIntegerExpression)value).getLeft())._max);
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.BYTE){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.CHAR){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.LONG){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.SHORT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.REAL){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.FLOAT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
			// TODO: Not working for String. lastValue is going to be the object reference.
		}else if (itype==Type.STRING){
			if (attr != null){
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
			//System.out.println("WARNING: String variable. Not ready for it yet.");
		}else if (itype==Type.REFERENCE){
			if (attr != null){
				value = attr;
			}else{
				value = Type.transformValueFromLong(vm, lvalue, type);
			}
			//System.out.println("WARNING: Ref value for field "+fi.getName()+": "+type+" (value: "+value+")");
		}
		return value;
    }
    
    public static Type simplifyTypeFromType(Type t){
		int type = t.getCode();
		if ((4<=type)&&(type<=8)){
			return Type.INT;
		}else if (type == 9){
			return Type.REAL;
		}else if (type == 0){
			return Type.INT;
		}else{
			return t;
		}
	}
    
	/**
	 * Returns the value written by a write operation on a symbolic variable
	 * @param lastIns
	 * @param ei
	 * @param vm
	 * @return
	 */
	public Object extractWrittenValue(FieldInstruction lastIns, ElementInfo ei, VM vm)
	{
		FieldInfo fi = lastIns.getFieldInfo();
		String type = fi.getType();
		Object attr = null;
		long lvalue = lastIns.getLastValue();

		if (lastIns instanceof PUTSTATIC){	
			attr = fi.getClassInfo().getStaticElementInfo().getFieldAttr(fi);
		}
		else if (lastIns instanceof PUTFIELD){
			ei = lastIns.getLastElementInfo();
			attr = ei.getFieldAttr(fi);			
		}
		Type itype = Type.typeToInteger(type);
		Type rtype = simplifyTypeFromType(itype);
		Object value = null;
		// Giving the proper shape to the read value.
		if (itype==Type.INT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				rtype = Type.SYMINT;
				value = attr;
				//System.out.println("Writing symint "+((SymbolicInteger)((BinaryLinearIntegerExpression)value).getLeft())._max);
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.BOOLEAN){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				rtype = Type.SYMINT;
				value = attr;
				//System.out.println("Writing symint "+((SymbolicInteger)((BinaryLinearIntegerExpression)value).getLeft())._max);
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.BYTE){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				rtype = Type.SYMINT;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.CHAR){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes){
				rtype = Type.SYMINT;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.LONG){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				rtype = Type.SYMINT;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.SHORT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				rtype = Type.SYMINT;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.REAL){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				rtype = Type.SYMREAL;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
		}else if (itype==Type.FLOAT){
			if (attr != null && !attr.toString().startsWith("W-")){ //we don't want references to writes
				rtype = Type.SYMREAL;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
			// TODO: Not working for String. lastValue is going to be the object reference.
		}else if (itype==Type.STRING){
			if (attr != null){
				rtype = Type.SYMSTRING;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
			//System.out.println("WARNING: String variable. Not ready for it yet.");
		}else if (itype==Type.REFERENCE){
			if (attr != null){
				rtype = Type.SYMREF;
				value = attr;
			}else{
				System.out.println(lvalue);
				//value = Utilities.transformValueFromLong(vm, lvalue, type);
			}
			//System.out.println("WARNING: Ref value for field "+fi.getName()+": "+type+" (value: "+value+")");
		}
		
		try{
			System.out.print(" type: "+type);
			System.out.print(" | attr: "+(attr != null ? attr : "null"));
			System.out.print(" | lvalue: "+lvalue);
			System.out.println(" | value: "+(value!=null ? value : "null"));
			
			//remove "valueOf"
			if(value.toString().startsWith(".valueof[")){
				String newval = value.toString();
				newval = newval.substring(9,newval.length()-1);
				value = newval;
			}
		}
		catch(Exception e){ System.out.println(" | null");}
		
		
		return value;
	}
    
    public Stack<SystemState> getSnapshotPath() {
      return snapshotPath;
    }

    @Override
    public void stateRestored(Search search) {
      id = search.getStateId();
      depth = search.getDepth();
      operation = "restored";
      detail = null;
    }

    //--- the ones we are interested in
    @Override
    public void searchStarted(Search search) {
      SystemState ss = search.getVM().getSystemState();
      path.push(ss.cloneState()); // This is in reality creating a new state with the same id as the one in which the luceneSearchComponent starts; cloneState is a superficial name
      System.out.println("Pushing state: " + ss.getId());
    }

    @Override
    public void stateAdvanced(Search search)  {
      SystemState ss = search.getVM().getSystemState();
      
      path.push(ss); // DK --> Since JPF works with a single state and keeps making changes in that state only, this operation is just pushing that state 
      // What we have to do is push a snapshot of the state comprising the locks and state-id. 
      // But then this pushed state will be a different state from the one where actually locks are getting acquired and released (ss).
      SystemState newState = ss.cloneState(); // This is in reality creating a new state with the same id as the one to which it is advanced to; cloneState is a superficial name
      newState.runningThreadId = search.getVM().getCurrentThread().getId();
      newState.cloneEventsAndAliasesAndPis(path.peek()); // We want events and varsAliases from the previous state
      path.push(newState); 
      System.out.println("Pushing state: " + ss.getId()); 
      id = search.getStateId();
      depth = search.getDepth();
      operation = "forward";
      if (search.isNewState()) {
        detail = "new";
      } else {
        detail = "visited";
      }

      if (search.isEndState()) {
        detail += " end";
      }
      
      int i = 1;
		while(i < threadSchedule.size()-1) {
			ConcExecStateInfo concStateInfo = threadSchedule.get(i);
			int temp1 = concStateInfo.getStateID();
			int temp2 = ss.getId();
			if(concStateInfo.getStateID() == ss.getId()) {
				break;
			}
			i++;
		}
		currentlyExecutingTransitionInstructionCount = threadSchedule.get(i).getInstructionsExecutedInState();
    }

    @Override
    public void stateBacktracked(Search search) {
      if(!path.isEmpty()) {
        SystemState ss = path.pop(); System.out.println("Popping: " + ss.getId());
        if(!path.isEmpty()) {
          ss = path.pop();
          System.out.println("Going to state: " + ss.getId());
          /*if(!path.isEmpty() && reverseConditions.get(path.peek().getId()) != null) {
            SystemState peeked = path.pop();
            Envelope e = new Envelope(Envelope.ASSUME);
            e.setAssume(reverseConditions.get(peeked.getId()));
            System.out.println("Removing " + peeked.events.get(peeked.events.size()-1) + " from state " + peeked.getId());
            peeked.events.remove(peeked.events.size()-1);
            peeked.addEvent(e);
            path.push(peeked);
            System.out.println("Added this instead: " + path.peek().getId() + " " + path.peek().events.get(path.peek().events.size()-1));
            reverseConditions.put(peeked.getId(), null);
          }*/
          ss.locks = new ArrayList<Pair<Object, Integer>>(); // Forget all the locks that were held before this state was backtracked to
          ss.events = new HashMap<Integer, ArrayList<Envelope>>();  // Forget all the events that happened in this state
          // Forget the latest assume event that happened in this state
          //ss.pis = new ArrayList<Pi>(); // Forget all the pi variables created in this state
          path.push(ss);
        }
      }

      id = search.getStateId();
      depth = search.getDepth();
      operation = "backtrack";
      detail = null;
    }

    @Override
    public void searchFinished(Search search) {
    	VM vm = search.getVM();
		
		ChoiceGenerator<?> cg = vm.getChoiceGenerator();
		if (!(cg instanceof PCChoiceGenerator)) {
		    ChoiceGenerator<?> prev_cg = cg.getPreviousChoiceGenerator();
		    while (!((prev_cg == null) || (prev_cg instanceof PCChoiceGenerator))) {
		        prev_cg = prev_cg.getPreviousChoiceGenerator();
		    }
		    cg = prev_cg;
		}
		
		if ((cg instanceof PCChoiceGenerator) && ((PCChoiceGenerator) cg).getCurrentPC() != null) {
			PathCondition pc = ((PCChoiceGenerator) cg).getCurrentPC();
		    System.out.println("PC is " + pc.toString());
		}
		
		if((cg instanceof PCChoiceGenerator) && ((PCChoiceGenerator) cg).getCurrentPC() != null) {
		    PathCondition pathCond = ((PCChoiceGenerator) cg).getCurrentPC();
		    symbolicInfo.put("PC", pathCond.toString()); // Serializing the path condition TODO: just chk why you are not able to store PathCondition as it is, and you have to take it as String.
		}
		else {
			symbolicInfo.put("PC", ""); // Serializing the path condition   
		}
		
		Stack<SystemState> tempPath = new Stack<SystemState>();
		while(!path.isEmpty()) {
		    SystemState ss = path.pop();
		    tempPath.push(ss);
		    /*System.out.println("\n~~~~~~~~~~State: " + ss.getId());
			HashMap<Pair<String, String>, ArrayList<String>> varsToAliases = ss.getVarsToAliases();
			Iterator<Pair<String, String>> vars = varsToAliases.keySet().iterator();
			while(vars.hasNext()) {
			    Pair<String, String> var = vars.next();
			    System.out.println(var._1 + " " + var._2);
			    System.out.println(varsToAliases.get(var));
			}
			System.out.println(ss.getEvents());
			Iterator<Pi> pisIt = ss.getPis().iterator();
			while(pisIt.hasNext()) {
			    Pi pi = pisIt.next();
			    System.out.println(pi.getPiName() + " " + pi.getvariablesToChoseFrom());
			}*/
		}
		
		while(!tempPath.isEmpty()) {
		    SystemState ss = tempPath.pop();
		    // Push all the events in the state for each thread id into eventsAlongPathForThread
		    HashMap<Integer, ArrayList<Envelope>> ssEvents = ss.getEvents();
		    Iterator<Integer> ssEventsThreadIds = ssEvents.keySet().iterator();
		    while(ssEventsThreadIds.hasNext()) {
		    	Integer threadId = ssEventsThreadIds.next();
		    	ArrayList<Envelope> arrayListInEventsAlongPathForThread = eventsAlongPathForThread.get(threadId);
		    	if(arrayListInEventsAlongPathForThread == null)
		    		arrayListInEventsAlongPathForThread = new ArrayList<Envelope>();
		    	arrayListInEventsAlongPathForThread.addAll(ssEvents.get(threadId));
		    	eventsAlongPathForThread.put(threadId, arrayListInEventsAlongPathForThread);
		    }
		    pisAlongPath.addAll(ss.getPis());
		    path.push(ss);
		    if(tempPath.isEmpty()) { // Store the varsToAliases of the last state
		    	symbolicInfo.put("VarsToAliases", ss.getVarsToAliases());
		    }
		}
		
		symbolicInfo.put("EventsAlongPathSize", eventsAlongPathForThread.size());
		symbolicInfo.put("EventsAlongPath", eventsAlongPathForThread);
		symbolicInfo.put("PisAlongPathSize", pisAlongPath.size());
		symbolicInfo.put("PisAlongPath", pisAlongPath);
		
		try {
			FileOutputStream fileOut = new FileOutputStream("./pc.ser");
		    ObjectOutputStream out = new ObjectOutputStream(fileOut);
		    out.writeObject(symbolicInfo);
		    out.close();
		    fileOut.close();
		}
		catch(Exception e) {System.out.println(e);}
		//catch(IOException e) {System.out.println(e);}
    }

    @Override
    public void methodEntered (VM vm, ThreadInfo currentThread, MethodInfo enteredMethod) {
      if(!path.isEmpty()) {
        SystemState currState = path.pop();
        
        if(enteredMethod.isSynchronized() && enteredMethod.getClassName().equals("tests.ExampleWithBorrowObject")) {
          currState.addLockAlongPath(new Pair<Object, Integer>(enteredMethod, 1));
          System.out.println("Method Enter: " + enteredMethod.getFullName() + " " + enteredMethod.isSynchronized() + " " + enteredMethod.getClassName());
        }
        path.push(currState);
      }
    }
    
    @Override
    public void methodExited (VM vm, ThreadInfo currentThread, MethodInfo exitedMethod) {
      if(!path.isEmpty()) {
        SystemState currState = path.pop();
        
        if(exitedMethod.isSynchronized() && exitedMethod.getClassName().equals("tests.ExampleWithBorrowObject")) {
          currState.addLockAlongPath(new Pair<Object, Integer>(exitedMethod, 0));
          System.out.println("Method Enter: " + exitedMethod.getFullName() + " " + exitedMethod.isSynchronized() + " " + exitedMethod.getClassName());
        }
        path.push(currState);
      }
    }
    

    /*
     * The way this method works is specific to the format of the methodSummary data structure
     */

    // TODO: needs to be changed not to use String representations
    private void printMethodSummary(PrintWriter pw, MethodSummary methodSummary) {

        System.out.println("Inputs: " + methodSummary.getSymValues());
        Vector<Pair> pathConditions = methodSummary.getPathConditions();
        if (pathConditions.size() > 0) {
            Iterator it = pathConditions.iterator();
            String allTestCases = "";
            while (it.hasNext()) {
                String testCase = methodSummary.getMethodName() + "(";
                Pair pcPair = (Pair) it.next();
                String pc = (String) pcPair._1;
                String errorMessage = (String) pcPair._2;
                String symValues = methodSummary.getSymValues();
                String argValues = methodSummary.getArgValues();
                String argTypes = methodSummary.getArgTypes();

                StringTokenizer st = new StringTokenizer(symValues, ",");
                StringTokenizer st2 = new StringTokenizer(argValues, ",");
                StringTokenizer st3 = new StringTokenizer(argTypes, ",");
                //if (!argTypes.isEmpty() && argValues.isEmpty()) { // DK: I don't know why isEmpty is not working
                if (argTypes.length()!=0 && argValues.length()==0) {
                    continue;
                }
                while (st2.hasMoreTokens()) {
                    String token = "";
                    String actualValue = st2.nextToken();
                    byte actualType = Byte.parseByte(st3.nextToken());
                    if (st.hasMoreTokens())
                        token = st.nextToken();
                    if (pc.contains(token)) {
                        String temp = pc.substring(pc.indexOf(token));
                        if (temp.indexOf(']') < 0) {
                            continue;
                        }

                        String val = temp.substring(temp.indexOf("[") + 1, temp.indexOf("]"));

                        // if(actualType == Types.T_INT || actualType == Types.T_FLOAT || actualType == Types.T_LONG ||
                        // actualType == Types.T_DOUBLE)
                        // testCase = testCase + val + ",";
                        if (actualType == Types.T_INT || actualType == Types.T_FLOAT || actualType == Types.T_LONG
                                || actualType == Types.T_SHORT || actualType == Types.T_BYTE
                                || actualType == Types.T_CHAR || actualType == Types.T_DOUBLE) {
                            String suffix = "";
                            if (actualType == Types.T_LONG) {
                                suffix = "l";
                            } else if (actualType == Types.T_FLOAT) {
                                val = String.valueOf(Double.valueOf(val).floatValue());
                                suffix = "f";
                            }
                            if (val.endsWith("Infinity")) {
                                boolean isNegative = val.startsWith("-");
                                val = ((actualType == Types.T_DOUBLE) ? "Double" : "Float");
                                val += isNegative ? ".NEGATIVE_INFINITY" : ".POSITIVE_INFINITY";
                                suffix = "";
                            }
                            testCase = testCase + val + suffix + ",";
                        } else if (actualType == Types.T_BOOLEAN) { // translate boolean values represented as ints
                            // to "true" or "false"
                            if (val.equalsIgnoreCase("0"))
                                testCase = testCase + "false" + ",";
                            else
                                testCase = testCase + "true" + ",";
                        } /*else
                            throw new RuntimeException(
                                    "## Error: listener does not support type other than int, long, short, byte, float, double and boolean");*/
                        // TODO: to extend with arrays
                    } else {
                        // need to check if value is concrete
                        if (token.contains("CONCRETE"))
                            testCase = testCase + actualValue + ",";
                        else
                            testCase = testCase + SymbolicInteger.UNDEFINED + "(don't care),";// not correct in concolic
                                                                                              // mode
                    }
                }
                if (testCase.endsWith(","))
                    testCase = testCase.substring(0, testCase.length() - 1);
                testCase = testCase + ")";
                // process global information and append it to the output

                if (!errorMessage.equalsIgnoreCase(""))
                    testCase = testCase + "  --> " + errorMessage;
                // do not add duplicate test case
                if (!allTestCases.contains(testCase))
                    allTestCases = allTestCases + "\n" + testCase;
            }
            pw.println(allTestCases);
        } else {
            pw.println("No path conditions for " + methodSummary.getMethodName() + "(" + methodSummary.getArgValues()
                    + ")");
        }
    }

    private void printMethodSummaryHTML(PrintWriter pw, MethodSummary methodSummary) {
        pw.println("<h1>Test Cases Generated by Symbolic JavaPath Finder for " + methodSummary.getMethodName()
                + " (Path Coverage) </h1>");

        Vector<Pair> pathConditions = methodSummary.getPathConditions();
        if (pathConditions.size() > 0) {
            Iterator it = pathConditions.iterator();
            String allTestCases = "";
            String symValues = methodSummary.getSymValues();
            StringTokenizer st = new StringTokenizer(symValues, ",");
            while (st.hasMoreTokens())
                allTestCases = allTestCases + "<td>" + st.nextToken() + "</td>";
            allTestCases = "<tr>" + allTestCases + "<td>RETURN</td></tr>\n";
            while (it.hasNext()) {
                String testCase = "<tr>";
                Pair pcPair = (Pair) it.next();
                String pc = (String) pcPair._1;
                String errorMessage = (String) pcPair._2;
                // String symValues = methodSummary.getSymValues();
                String argValues = methodSummary.getArgValues();
                String argTypes = methodSummary.getArgTypes();
                // StringTokenizer
                st = new StringTokenizer(symValues, ",");
                StringTokenizer st2 = new StringTokenizer(argValues, ",");
                StringTokenizer st3 = new StringTokenizer(argTypes, ",");
                while (st2.hasMoreTokens()) {
                    String token = "";
                    String actualValue = st2.nextToken();
                    byte actualType = Byte.parseByte(st3.nextToken());
                    if (st.hasMoreTokens())
                        token = st.nextToken();
                    if (pc.contains(token)) {
                        String temp = pc.substring(pc.indexOf(token));
                        if (temp.indexOf(']') < 0) {
                            continue;
                        }

                        String val = temp.substring(temp.indexOf("[") + 1, temp.indexOf("]"));
                        if (actualType == Types.T_INT || actualType == Types.T_FLOAT || actualType == Types.T_LONG
                                || actualType == Types.T_SHORT || actualType == Types.T_BYTE
                                || actualType == Types.T_DOUBLE)
                            testCase = testCase + "<td>" + val + "</td>";
                        else if (actualType == Types.T_BOOLEAN) { // translate boolean values represented as ints
                            // to "true" or "false"
                            if (val.equalsIgnoreCase("0"))
                                testCase = testCase + "<td>false</td>";
                            else
                                testCase = testCase + "<td>true</td>";
                        } /*else
                            throw new RuntimeException(
                                    "## Error: listener does not support type other than int, long, short, byte, float, double and boolean");*/

                    } else {
                        // need to check if value is concrete
                        if (token.contains("CONCRETE"))
                            testCase = testCase + "<td>" + actualValue + "</td>";
                        else
                            testCase = testCase + "<td>" + SymbolicInteger.UNDEFINED + "(don't care)</td>"; // not
                                                                                                            // correct
                                                                                                            // in
                                                                                                            // concolic
                                                                                                            // mode
                    }
                }

                // testCase = testCase + "</tr>";
                // process global information and append it to the output

                if (!errorMessage.equalsIgnoreCase(""))
                    testCase = testCase + "<td>" + errorMessage + "</td>";
                // do not add duplicate test case
                if (!allTestCases.contains(testCase))
                    allTestCases = allTestCases + testCase + "</tr>\n";
            }
            pw.println("<table border=1>");
            pw.print(allTestCases);
            pw.println("</table>");
        } else {
            pw.println("No path conditions for " + methodSummary.getMethodName() + "(" + methodSummary.getArgValues()
                    + ")");
        }

    }

    // -------- the publisher interface
    @Override
    public void publishFinished(Publisher publisher) {
        String[] dp = SymbolicInstructionFactory.dp;
        if (dp[0].equalsIgnoreCase("no_solver") || dp[0].equalsIgnoreCase("cvc3bitvec"))
            return;

        PrintWriter pw = publisher.getOut();

        publisher.publishTopicStart("Method Summaries");
        Iterator it = allSummaries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry me = (Map.Entry) it.next();
            MethodSummary methodSummary = (MethodSummary) me.getValue();
            printMethodSummary(pw, methodSummary);
        }

        publisher.publishTopicStart("Method Summaries (HTML)");
        it = allSummaries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry me = (Map.Entry) it.next();
            MethodSummary methodSummary = (MethodSummary) me.getValue();
            printMethodSummaryHTML(pw, methodSummary);
        }
    }

    protected class MethodSummary {
        private String methodName = "";
        private String argTypes = "";
        private String argValues = "";
        private String symValues = "";
        private Vector<Pair> pathConditions;

        public MethodSummary() {
            pathConditions = new Vector<Pair>();
        }

        public void setMethodName(String mName) {
            this.methodName = mName;
        }

        public String getMethodName() {
            return this.methodName;
        }

        public void setArgTypes(String args) {
            this.argTypes = args;
        }

        public String getArgTypes() {
            return this.argTypes;
        }

        public void setArgValues(String vals) {
            this.argValues = vals;
        }

        public String getArgValues() {
            return this.argValues;
        }

        public void setSymValues(String sym) {
            this.symValues = sym;
        }

        public String getSymValues() {
            return this.symValues;
        }

        public void addPathCondition(Pair pc) {
            pathConditions.add(pc);
        }

        public Vector<Pair> getPathConditions() {
            return this.pathConditions;
        }

    }
}