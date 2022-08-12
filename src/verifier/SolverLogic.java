package verifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.StringTokenizer;

import com.microsoft.z3.*;

import eventExtractor.Path;
import gov.nasa.jpf.vm.Envelope;
import gov.nasa.jpf.vm.Pi;
import gov.nasa.jpf.util.Pair;

class SVar {
	BoolExpr s;
	Envelope waitEvent;
	Envelope notifyEvent;
}

public class SolverLogic {
	
	private Context ctx;
	public Solver solver;
	private ArrayList<SVar> sVars;
	private HashMap<Envelope, BoolExpr> mVars_For_Events; // Instead of creating a bigger umbrella over each event to include m variables, I am keeping a map from an event to a m variable.
	private HashMap<String, IntExpr> piAndTheirExpressions;
	private Path path;
	ArrayList<BoolExpr> pathCondition;
	private ArrayList<util.Pair<String, String>> sharedVariables;
	private boolean loggingOn = true; 
	
	
	private void log(Object x) {
		if(loggingOn)
			System.out.println(x);
	}
	
	public SolverLogic(Path path, ArrayList<util.Pair<String, String>> sharedVariables) {
		// Set the Z3 solver
		try
        {
            com.microsoft.z3.Global.ToggleWarningMessages(true);
            Log.open("test.log");

            log("Z3 Major Version: " + Version.getMajor());
            log("Z3 Full Version: " + Version.getString());

            HashMap<String, String> cfg = new HashMap<String, String>();
            cfg.put("model", "true");
            ctx = new Context(cfg);
            solver = ctx.mkSolver();
        }
		catch (Z3Exception ex)
        {
            log("Z3 Managed Exception: " + ex.getMessage());
            log("Stack trace: ");
            ex.printStackTrace(System.out);
        } 
		catch (Exception ex)
        {
            log("Unknown Exception: " + ex.getMessage());
            log("Stack trace: ");
            ex.printStackTrace(System.out);
        }
		this.path = path;
		sVars = new ArrayList<SVar>();
		mVars_For_Events = new HashMap<Envelope, BoolExpr>();
		piAndTheirExpressions = new HashMap<String, IntExpr>();
		pathCondition = new ArrayList<BoolExpr>();
		this.sharedVariables = sharedVariables;
	}
	
	private IntExpr isInteger(String expr) {
		String str = expr;
		if(expr.startsWith("CONST_")) {
			str = expr.substring(expr.indexOf("CONST_")+6, expr.length());
		}
		try {
	         return ctx.mkInt(Integer.parseInt(str));
	    }
	    catch (NumberFormatException ex) { return null; }
	}
	
	private IntExpr isFloat(String expr) {
		String str = expr;
		if(expr.startsWith("CONST_")) {
			str = expr.substring(expr.indexOf("CONST_")+6, expr.length());
		}
		try {
			return ctx.mkInt((int) Float.parseFloat(str));
	    }
	    catch (NumberFormatException ex) { return null; }
	 
	}
	
	private BoolExpr[] returnArray(ArrayList<BoolExpr> arrayList) {
		BoolExpr[] arr = new BoolExpr[arrayList.size()];
		int i=0;
		Iterator<BoolExpr> iterator = arrayList.iterator();
		while(iterator.hasNext()) {
			arr[i] = iterator.next();
			i++;
		}
		return arr;
	}
	
	private void formSelectionRules(IntExpr piExpr, Pi pi, IntExpr thisEvent, boolean forLockOrWaitVariable) {
		
		ArrayList<BoolExpr> eqs = new ArrayList<BoolExpr>();
		String varName = pi.getvariablesToChoseFrom().get(pi.getvariablesToChoseFrom().size()-1)._1; // Last written alias in this thread
		int eventNo = pi.getvariablesToChoseFrom().get(pi.getvariablesToChoseFrom().size()-1)._2;
		IntExpr eventTimeFromThisThread = (eventNo == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+eventNo);
		IntExpr varFromThisThread = ctx.mkIntConst(/*pathId + "_" + */varName);
			
		ArrayList<BoolExpr> selections = new ArrayList<BoolExpr>();
		
		BoolExpr eqFromFirstThread = ctx.mkEq(piExpr, varFromThisThread);
		BoolExpr hbWithFirstThread;
		if(eventTimeFromThisThread.getSExpr().equals(thisEvent.getSExpr()))
			hbWithFirstThread = ctx.mkBool(true);
		else
			hbWithFirstThread = ctx.mkLt(eventTimeFromThisThread, thisEvent);
		ArrayList<BoolExpr> eqAndHbAndOtherHbs = new ArrayList<BoolExpr>();
		eqAndHbAndOtherHbs.add(eqFromFirstThread);
		eqAndHbAndOtherHbs.add(hbWithFirstThread);
		
		// Find every alias of the variable
		HashMap<Pair<String, String>, ArrayList<Pair<String, Integer>>> varsToAliases = path.getVarsToAliases();
		Iterator<Pair<String, String>> varsIt = varsToAliases.keySet().iterator();
		Pair<String, String> var = new Pair<String, String>(pi.getVarNameType()._1, pi.getVarNameType()._2);
		while(varsIt.hasNext()) {
			var = varsIt.next();
			if(var._1.equals(pi.getVarNameType()._1) && var._2.equals(pi.getVarNameType()._2))
				break;
		}
		Iterator<Pair<String, Integer>> aliasesIt = path.getVarsToAliases().get(var).iterator();
		while(aliasesIt.hasNext()) {
			Pair<String, Integer> alias = aliasesIt.next();
			// Form HB options for this alias: HB(tj, ti) OR HB(t, tj)
			BoolExpr[] optionsForOtherHB = new BoolExpr[2];
			IntExpr aliasClk = (alias._2 == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+alias._2);
			
			if(aliasClk.getSExpr().equals(eventTimeFromThisThread.getSExpr()))
				optionsForOtherHB[0] = ctx.mkBool(true);
			else
				optionsForOtherHB[0] = ctx.mkLt(aliasClk, eventTimeFromThisThread);
			
			if(aliasClk.getSExpr().equals(thisEvent.getSExpr()))
				optionsForOtherHB[1] = ctx.mkBool(true);
			else
				optionsForOtherHB[1] = ctx.mkLt(thisEvent, aliasClk);
			
			eqAndHbAndOtherHbs.add(ctx.mkOr(optionsForOtherHB));
		}
		
		BoolExpr[] eqAndHbAndOtherHbsArr = returnArray(eqAndHbAndOtherHbs);

		// Forming v' = vi And HB(ti, t) And (HB(tj, ti) Or HB(t, tj)) for the first thread's variable
		eqs.add(ctx.mkAnd(eqAndHbAndOtherHbsArr));
		
		// Forming Or of v' = wi And HB(ti, t) And (HB(tj, ti) Or HB(t, tj)) for the aliases of the variable of the second thread (for one pvw option)
		aliasesIt = path.getVarsToAliases().get(var).iterator();
		while(aliasesIt.hasNext()) { 
			// Forming v' = vi And HB(ti, t) for aliases of the variable from other thread
			Pair<String, Integer> alias = aliasesIt.next(); 
			IntExpr varFromOtherThread = ctx.mkIntConst(/*otherPathId + "_" + */alias._1);
			BoolExpr eq = ctx.mkEq(piExpr, varFromOtherThread);
			IntExpr aliasClk = (alias._2 == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+alias._2);
			BoolExpr hb;
			if(aliasClk.getSExpr().equals(thisEvent.getSExpr()))
				hb = ctx.mkBool(true);
			else
				hb = ctx.mkLt(aliasClk, thisEvent);
			
			ArrayList<BoolExpr> varAssign_hb_otherHbs = new ArrayList<BoolExpr>();
			varAssign_hb_otherHbs.add(eq);
			varAssign_hb_otherHbs.add(hb);
			
			BoolExpr[] optionsForOtherHB = new BoolExpr[2];
			
			if(aliasClk.getSExpr().equals(eventTimeFromThisThread.getSExpr()))
				optionsForOtherHB[0] = ctx.mkBool(true);
			else
				optionsForOtherHB[0] = ctx.mkLt(eventTimeFromThisThread, aliasClk);
			
			if(eventTimeFromThisThread.getSExpr().equals(thisEvent.getSExpr()))
				optionsForOtherHB[1] = ctx.mkBool(true);
			else
				optionsForOtherHB[1] = ctx.mkLt(thisEvent, eventTimeFromThisThread);
			
			varAssign_hb_otherHbs.add(ctx.mkOr(optionsForOtherHB));
			
			Iterator<Pair<String, Integer>> innerAliasesIt = path.getVarsToAliases().get(var).iterator();
			while(innerAliasesIt.hasNext()) {
				Pair<String, Integer> innerAlias = innerAliasesIt.next();
				// Form HB options for this alias: HB(tj, ti) OR HB(t, tj)
				if(!alias.equals(innerAlias)) {
					optionsForOtherHB = new BoolExpr[2];
					IntExpr innerAliasClk = (innerAlias._2 == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+innerAlias._2);
					
					if(innerAliasClk.getSExpr().equals(aliasClk.getSExpr()))
						optionsForOtherHB[0] = ctx.mkBool(true);
					else
						optionsForOtherHB[0] = ctx.mkLt(innerAliasClk, aliasClk);
					
					if(thisEvent.getSExpr().equals(innerAliasClk.getSExpr()))
						optionsForOtherHB[1] = ctx.mkBool(true);
					else
						optionsForOtherHB[1] = ctx.mkLt(thisEvent, innerAliasClk);

					varAssign_hb_otherHbs.add(ctx.mkOr(optionsForOtherHB));
				}
			}
			BoolExpr[] varAssign_hbArr = returnArray(varAssign_hb_otherHbs);
			eqs.add(ctx.mkAnd(varAssign_hbArr));
		}
		BoolExpr[] eqsArr = returnArray(eqs);
		BoolExpr orEqs = ctx.mkOr(eqsArr); // Or of {v' = vi & HB(ti, t)}
		log("~~~~~~~~~~~~Adding : " + orEqs);
		solver.add(orEqs);
	}
	
	private boolean isSharedVariable(Pair<String, String> var) {
		boolean shared = false;
		// Check whether this variable is in the list of shardVariables
		Iterator<util.Pair<String, String>> sharedVariablesIt = sharedVariables.iterator();
		while(sharedVariablesIt.hasNext()) {
			util.Pair<String, String> sharedVar = sharedVariablesIt.next();
			if(sharedVar.first.equals(var._1) && sharedVar.second.equals(var._2))
				return true;
		}
		return shared;
	}
	
	private IntExpr createExprForOperand(String op, IntExpr thisEvent) {
		IntExpr intExpr = null;
		if(op.startsWith("pi") && piAndTheirExpressions.keySet().contains(op)) {
			return piAndTheirExpressions.get(op);
		}
		else if(op.startsWith("pi")) {
			// Search if the root variable of this "pi" is an escaping variable
			Iterator<Pi> pisAlongPath = path.getPisAlongPath().iterator();
			while(pisAlongPath.hasNext()) {
				Pi pi = pisAlongPath.next();
				if(!pi.getPiName().equals(op))
					continue;
				if(pi.getVarNameType()._1.contains("assertionsDisabled"))
					break;
				
				boolean isSharedVariable = isSharedVariable(pi.getVarNameType());
				
				if(!isSharedVariable) { // If the pi's root variable is not a shared variable
					String varName = pi.getvariablesToChoseFrom().get(pi.getvariablesToChoseFrom().size()-1)._1;
					intExpr = ctx.mkIntConst(/*p.getId() + "_" + */ varName);
					break;
				}
				else { // If the root of this pi variable is a shared variable  
					
					// Form selection rule --> select one variable out of the aliases for selected variable
					intExpr = ctx.mkIntConst(op); // Using pi name here
					if(pi.getVarNameType()._1.contains("_lock__"))
						formSelectionRules(intExpr, pi, thisEvent, true);
					else
						formSelectionRules(intExpr, pi, thisEvent, false);
					break;
					// TODO: If not already there, should we keep each variable in the hashmaps mapping variables to their respective symbolic counterparts?   
				}
			}
		}
		else {
			intExpr = isInteger(op);
			if(intExpr == null)
				intExpr = isFloat(op);
		}
		
		piAndTheirExpressions.put(op, intExpr);
		return intExpr;
	}
	
	private boolean isBinaryOperator(String op) {
		// TODO: Add more operators
		if(op.equals("ISUB") || op.equals("IADD") || op.equals("IMUL") || op.equals("IDIV") || op.equals("IOR") || op.equals("IAND"))
			return true;
		else if(op.equals("LSUB") || op.equals("LADD") || op.equals("LMUL") || op.equals("LDIV") || op.equals("LOR") || op.equals("LAND"))
			return true;
		else if(op.equals("FSUB") || op.equals("FADD") || op.equals("FMUL") || op.equals("FDIV"))
			return true;
		else if(op.equals("DADD") || op.equals("DSUB") || op.equals("DMUL") || op.equals("DDIV"))
			return true;
		else
			return false;
	}
	
	private boolean isUnaryOperator(String op) {
		if(op.equals("INEG") || op.equals("FNEG"))
			return true;
		else return false;
	}
	
	private Expr createExpr(String exprString, IntExpr thisEvent) {		
		StringTokenizer str = new StringTokenizer(exprString, " ()"); // This will give only operands and operators
		Stack<String> postfixExpr = new Stack<String>(); // Reverse of the prefix expression
		// The topmost element in the stack will be the last token of the expression, 
		// and hence when we start popping the elements out of the stack, we get a postfix expression 
		
		while(str.hasMoreTokens()) {
			postfixExpr.push(str.nextToken());
		}
		
		Stack<Object> workSetStack = new Stack<Object>();
		while(!postfixExpr.isEmpty()) {
			String operator = postfixExpr.pop();
			if(isBinaryOperator(operator)) {
				IntExpr expr1, expr2;
				
				Object op2 = workSetStack.pop();
				if(op2 instanceof String)
					expr2 = createExprForOperand((String) op2, thisEvent);
				else
					expr2 = (IntExpr) op2;
				
				Object op1 = workSetStack.pop();
				if(op1 instanceof String)
					expr1 = createExprForOperand((String) op1, thisEvent);
				else
					expr1 = (IntExpr) op1;
				
				if(operator.equals("ISUB") || operator.equals("LSUB") || operator.equals("FSUB") || operator.equals("DSUB")) {
					ArithExpr subExpr = ctx.mkSub(expr1, expr2);
					workSetStack.push(subExpr);
				}
				else if(operator.equals("IADD") || operator.equals("LADD") || operator.equals("FADD") || operator.equals("DADD")) {
					ArithExpr addExpr = ctx.mkAdd(expr1, expr2);
					workSetStack.push(addExpr);
				}
				else if(operator.equals("IMUL") || operator.equals("LMUL") || operator.equals("FMUL") || operator.equals("DMUL")) {
					ArithExpr mulExpr = ctx.mkMul(expr1, expr2);
					workSetStack.push(mulExpr);
				}
				else if(operator.equals("IAND") || operator.equals("LAND")) {
					BitVecExpr andExpr = ctx.mkBVAND(ctx.mkInt2BV(8, expr1), ctx.mkInt2BV(8, expr2));
					ArithExpr arth = ctx.mkBV2Int(andExpr, true);
					workSetStack.push(arth);
				}
				else if(operator.equals("IOR") || operator.equals("LOR")) {
					BitVecExpr andExpr = ctx.mkBVOR(ctx.mkInt2BV(8, expr1), ctx.mkInt2BV(8, expr2));
					ArithExpr arth = ctx.mkBV2Int(andExpr, true);
					workSetStack.push(arth);
				}
			}
			else if(isUnaryOperator(operator)) {
				// TODO: 
			}
			else { // operand
				workSetStack.push(operator);				
			}
		}
		
		return (Expr) workSetStack.pop();
	}
	
	private IntExpr formWaitNotifyRules(Pi pi, Envelope waitEvent) {
		IntExpr intExpr = null;
		String op = pi.getPiName();
		// Search if the root variable of this "pi" is a shared variable
		boolean isShared  = isSharedVariable(pi.getVarNameType());
		 
		if(isShared) { // If pi's root variable is a shared variable
			// Form selection rule --> select one variable out of the aliases for selected variable from m2 and pi's lastVariableToChoseFrom
			intExpr = ctx.mkIntConst(op); // Using pi name here
			
			IntExpr thisEvent = (waitEvent.getClk() == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+waitEvent.getClk());
			formSelectionRules(intExpr, pi, thisEvent, true);
			// TODO: If not already there, should we keep each variable in the hashmaps mapping variables to their respective symbolic counterparts?
			
			// For this encountered wait event in the path, find all the notify events which are called by the same caller 
			Iterator<Integer> threadsIt = path.getEventsAlongPath().keySet().iterator();
			ArrayList<Envelope> notifies = new ArrayList<Envelope>();
			while(threadsIt.hasNext()) {
				int thread = threadsIt.next();
				if(thread ==  waitEvent.getThreadId())
					continue; // We want notify from the other thread
				ArrayList<Envelope> eventsAlongThread = path.getEventsAlongPath().get(thread);
				Iterator<Envelope> eventsAlongThreadIt = eventsAlongThread.iterator();
				while(eventsAlongThreadIt.hasNext()) {
					Envelope event = eventsAlongThreadIt.next();
					if(event.getAction() == Envelope.NOTIFY && event.getWait_notifyMonitorName().equals(waitEvent.getWait_notifyMonitorName()) 
							&& event.getWait_notifyMonitorType().equals(waitEvent.getWait_notifyMonitorType())) {
						notifies.add(event);
					}
				}
			}
			Iterator<Envelope> notifiesIt = notifies.iterator();
			ArrayList<SVar> matchesForThisWaitEvent = new ArrayList<SVar>();				
			while(notifiesIt.hasNext()) {
				Envelope notifyEvent = notifiesIt.next();
				SVar sVar = new SVar();
				sVar.s = ctx.mkBoolConst("s_"+waitEvent.getClk()+notifyEvent.getClk());
				sVar.waitEvent = waitEvent;
				sVar.notifyEvent = notifyEvent;
				matchesForThisWaitEvent.add(sVar);
			}
			BoolExpr[] sVarMatchesForThisWaitEvent = new BoolExpr[matchesForThisWaitEvent.size()];
			for(int i=0; i< matchesForThisWaitEvent.size(); ++i) {
				sVarMatchesForThisWaitEvent[i] = matchesForThisWaitEvent.get(i).s;
			}
			// sVar_i --> !sVar_j
			for(int i=0; i<matchesForThisWaitEvent.size(); ++i) {
				BoolExpr andSvars = ctx.mkBoolConst("true");
				for(int j=0; j<matchesForThisWaitEvent.size(); ++j) {
					if(j!=i)
						andSvars = ctx.mkAnd(andSvars, ctx.mkNot(sVarMatchesForThisWaitEvent[j]));
				}
				solver.add(ctx.mkImplies(sVarMatchesForThisWaitEvent[i], andSvars));
			}
			
			// sVar_i --> wait_m and notify_m
			for(int i=0; i<matchesForThisWaitEvent.size(); ++i) {
				BoolExpr andMs = ctx.mkBoolConst("true");
				Iterator<Envelope> envelopeIt = mVars_For_Events.keySet().iterator();
				while(envelopeIt.hasNext()) {
					Envelope e = envelopeIt.next();
					if(e.getClk() == waitEvent.getClk() || e.getClk() == notifies.get(i).getClk())
						andMs = ctx.mkAnd(andMs, mVars_For_Events.get(e));
				}
				solver.add(ctx.mkImplies(sVarMatchesForThisWaitEvent[i], andMs));
			}
		}
		return intExpr;
	}
	
	private BoolExpr convertPathConditionStrToExpr(String pathConditionStr) {
		BoolExpr pathConditionExpr = ctx.mkBoolConst("true");
		
		StringTokenizer str = new StringTokenizer(pathConditionStr, "\n&&");
		str.nextToken(); // Discard the first line, eg: constraint # = 3
		while(str.hasMoreTokens()) {
			String constraint = str.nextToken();
			BoolExpr constraintExpr = convertConstraintStrToExpr(constraint);
			pathConditionExpr = ctx.mkAnd(pathConditionExpr, constraintExpr);
		}
		return pathConditionExpr;
	}
	
	// TODO: This method needs improvement in interpreting the path condition's constraints.
	private BoolExpr convertConstraintStrToExpr(String constraint) {
		// Convert the string into an infix expression, and form the rule.
		ArrayList<String> assumeExpr = new ArrayList<String>();
		StringTokenizer str = new StringTokenizer(constraint, " ");
		while(str.hasMoreTokens()) {
			assumeExpr.add(str.nextToken());
		}
		//log("This is a path condition constraint: " + assumeExpr);
		IntExpr intExpr1 = null, intExpr2 = null;
		String operator = "";
		
		while(!assumeExpr.isEmpty()) {
			String nextToken = assumeExpr.remove(0);
			if(nextToken.equals("<=") || nextToken.equals(">=") || nextToken.equals("<") || nextToken.equals(">")
					|| nextToken.equals("==") || nextToken.equals("!=")) // Found the operator
				operator = nextToken;
			else {
				IntExpr thisEvent = null;
				if(nextToken.startsWith("pi")) {
					Iterator<Pi> pisAlongPath = path.getPisAlongPath().iterator();
					while(pisAlongPath.hasNext()) {
						Pi pi = pisAlongPath.next();
						if(!pi.getPiName().equals(nextToken))
							continue;
						thisEvent = (pi.getClk() == -1) ? ctx.mkIntConst("start") : ctx.mkIntConst("e_"+pi.getClk());
						break;
					}
				}
				IntExpr intExpr = createExprForOperand(nextToken, thisEvent);
				if(operator.equals(""))
					intExpr1 = intExpr;
				else
					intExpr2 = intExpr;
			}
		}
		
		if(operator.equals("<="))
			return ctx.mkLe(intExpr1, intExpr2);
		else if(operator.equals("<"))
			return ctx.mkLt(intExpr1, intExpr2);
		else if(operator.equals(">="))
			return ctx.mkGe(intExpr1, intExpr2);
		else if(operator.equals(">"))
			return ctx.mkGt(intExpr1, intExpr2);
		else if(operator.equals("=="))
			return ctx.mkEq(intExpr1, intExpr2);
		else if(operator.equals("!="))
			return ctx.mkNot(ctx.mkEq(intExpr1, intExpr2));
		return ctx.mkBoolConst("true");
	}
	
	private void formPropertyRule(BoolExpr pathConditionExpr) {
		BoolExpr orNotMs = ctx.mkBoolConst("false");
		
		// Prop1: For all the notify calls 'm', m <--> g(t), g(t) being the path condition of the notify trace.
		Iterator<Envelope> m_Iterator = mVars_For_Events.keySet().iterator();
		while(m_Iterator.hasNext()) {
			Envelope e = m_Iterator.next();
			if(e.getAction() == Envelope.WAIT || e.getAction() == Envelope.NOTIFY) {
				BoolExpr mVar = mVars_For_Events.get(e);
				solver.add(ctx.mkAnd(ctx.mkImplies(mVar, pathConditionExpr), ctx.mkImplies(pathConditionExpr, mVar)) );
				log("~~~~~~~~~~~~Adding implies data for first prop : " + ctx.mkAnd( ctx.mkImplies(mVar, pathConditionExpr), ctx.mkImplies(pathConditionExpr, mVar)) );
				orNotMs = ctx.mkOr(orNotMs, ctx.mkNot(mVar));
			}
		}
		log("~~~~~~~~~~~~Adding 1st property : " + orNotMs);
		BoolExpr prop1 = orNotMs; 
		
		BoolExpr[] timeExpr = new BoolExpr[sVars.size()];
		Iterator<SVar> sVarsIt = sVars.iterator();
		int i=0;
		while(sVarsIt.hasNext()) {
			SVar s = sVarsIt.next();
			timeExpr[i] = ctx.mkNot(ctx.mkLt(ctx.mkIntConst("e_"+s.waitEvent.getClk()), ctx.mkIntConst("e_"+s.notifyEvent.getClk())));
			i++;
		}
		// Prop2: For any s var variable, the time of notify event is less than the time of the wait event
		log("~~~~~~~~~~~~Adding 2nd property : " + ctx.mkOr(timeExpr));
		BoolExpr prop2 = ctx.mkOr(timeExpr);
		
		BoolExpr[] props = new BoolExpr[2];
		props[0] = prop1;
		props[1] = prop2;
		solver.add(ctx.mkOr(props)); 
	}	
	
	private void scanThroughEvents(ArrayList<Envelope> events) {
		Iterator<Envelope> eventsIt = events.iterator();
		IntExpr start = ctx.mkIntConst("start");
		IntExpr prev = start;
		outer: while(eventsIt.hasNext()) {
			Envelope e = eventsIt.next();
			
			log(e);
			// Create a happens before relation rule between the previous and this event.
			IntExpr thisEvent = (e.getClk() == -1) ? start : ctx.mkIntConst("e_"+e.getClk());
			solver.add(ctx.mkGt(thisEvent, ctx.mkIntConst("0")));
			if(!thisEvent.getSExpr().equals("start")) { // For stopping a rule like start < start
				//log("~~~~~~~~~~~~Adding : " + prev.toString() + " < " + thisEvent.toString());
				solver.add(ctx.mkLt(prev, thisEvent));
			}
			prev = thisEvent;
			
			if(e.getAction() == Envelope.STORE) {
				//log("It's a store event: " + e.getStoreVariableName() + " " + e.getStoreVariableType() + " " + e.getExpr());
				if(e.getExpr() == null) {
					// This might be because of a function call outside the SUT. Since, we do not handle function invocations, 
					// the expression to be stored in a variable might be null.
					continue outer;
				}
				StringTokenizer str = new StringTokenizer(e.getExpr(), " ");
				String token = str.nextToken(); // for the bracket
				if(token.equals("(")) {
					token = str.nextToken();
					if(token.equals("new") || token.equals("newarray")) {
						// Initialize the variable with a fresh value
						BoolExpr eqExpr = ctx.mkEq(ctx.mkIntConst(e.getStoreVariableName()), ctx.mkInt(1)); // TODO: Initializing with 0 in case of new
						log("~~~~~~~~~~~~Adding : " + e.getStoreVariableName() + " = " + 1);
						solver.add(eqExpr);
					}
					else {
						Expr ex = createExpr(e.getExpr(), thisEvent);
						BoolExpr eqExpr = ctx.mkEq(ctx.mkIntConst(e.getStoreVariableName()), ex);
						log("~~~~~~~~~~~~Adding : " + e.getStoreVariableName() + " = " + ex.toString());
						solver.add(eqExpr);
					}
				}
				else { // The expression is not in the brackets, hence it must be an assignment statement.
					BoolExpr eqExpr;
					IntExpr intExpr = createExprForOperand(token, thisEvent);
					if(intExpr == null) { // It can be null if it is not really an intExpr. Example: STORE this.serializer2 null
						eqExpr = ctx.mkEq(ctx.mkIntConst(e.getStoreVariableName()), ctx.mkInt(-1)); // Initializing with -1 in case of null
						log("~~~~~~~~~~~~Adding : " + e.getStoreVariableName() + " = " + -1);
					}
					else {
						eqExpr = ctx.mkEq(ctx.mkIntConst(e.getStoreVariableName()), intExpr);
						log("~~~~~~~~~~~~Adding : " + e.getStoreVariableName() + " = " + intExpr);
					}
					solver.add(eqExpr);
				}
			}
			else if(e.getAction() == Envelope.ASSUME) { 
				// Convert the reverse prefix expression into infix expression, and form the rule. The encoding of 'pi' variables remains same as above
				String assumeExpr = e.getAssume();
				Stack<String> reverseAssumeExpr = new Stack<String>();
				StringTokenizer str = new StringTokenizer(assumeExpr, " ( )");
				while(str.hasMoreTokens()) {
					reverseAssumeExpr.push(str.nextToken());
				}
				//log("This is an assume event: " + reverseAssumeExpr);
				Stack<Expr> operands = new Stack<Expr>();
				
				while(!reverseAssumeExpr.isEmpty()) {
					String nextToken = reverseAssumeExpr.pop();
					if(isBinaryOperator(nextToken)) {
						IntExpr intExpr1 = (IntExpr) operands.pop();
						IntExpr intExpr2 = (IntExpr) operands.pop();
						if(nextToken.equals("ISUB") || nextToken.equals("LSUB"))
							operands.push(ctx.mkSub(intExpr1, intExpr2));
						else if(nextToken.equals("IADD") || nextToken.equals("LADD"))
							operands.push(ctx.mkAdd(intExpr1, intExpr2));
						else if(nextToken.equals("IMUL") || nextToken.equals("LMUL"))
							operands.push(ctx.mkMul(intExpr1, intExpr2));
						else if(nextToken.equals("IDIV") || nextToken.equals("LDIV"))
							operands.push(ctx.mkDiv(intExpr1, intExpr2));
					}
					else if(nextToken.equals("<=") || nextToken.equals(">=") || nextToken.equals("<") || nextToken.equals(">")
							|| nextToken.equals("==") || nextToken.equals("!=")) {
						IntExpr intExpr1 = (IntExpr) operands.pop();
						IntExpr intExpr2 = (IntExpr) operands.pop();
						if(nextToken.equals("<="))
							operands.push(ctx.mkLe(intExpr1, intExpr2));
						else if(nextToken.equals("<"))
							operands.push(ctx.mkLt(intExpr1, intExpr2));
						else if(nextToken.equals(">="))
							operands.push(ctx.mkGe(intExpr1, intExpr2));
						else if(nextToken.equals(">"))
							operands.push(ctx.mkGt(intExpr1, intExpr2));
						else if(nextToken.equals("==")) {
							if(intExpr1.toString().equals("1") && intExpr2.toString().equals("0"))
								continue outer; // This is for events like ASSUME (== 1 0)
							operands.push(ctx.mkEq(intExpr1, intExpr2));
						}
						else if(nextToken.equals("!="))
							operands.push(ctx.mkNot(ctx.mkEq(intExpr1, intExpr2)));
					}
					else {
						IntExpr expression = createExprForOperand(nextToken, thisEvent);
						if(expression == null) // createExprForOperand will return null only when the operand is assertionsDisabled
							continue outer; // We need to get out of scanning through this ASSUME once we know that it deals with assertionsDisabled
						else
							operands.push(expression);
					}
				}
				BoolExpr assumeExpression = (BoolExpr) operands.pop();
				log("~~~~~~~~~~~~Adding : " + assumeExpression);
				solver.add(assumeExpression);
			}
			else if(e.getAction() == Envelope.WAIT) {
				mVars_For_Events.put(e, ctx.mkBoolConst("m_"+e.getClk()));
				formWaitNotifyRules(e.getWaitNotifyPi(), e);
			}
			else if(e.getAction() == Envelope.NOTIFY) {
				mVars_For_Events.put(e, ctx.mkBoolConst("m_"+e.getClk()));
			}
		}
	}
	
	/**
	 * Invokes z3 solver to verify the interleavings of the trace.
	 * @return Model if the formula is SAT.
	 */
	 public Model checkCompatibility() {
		// Read each event of all the threads and form rules for those events.
		HashMap<Integer, ArrayList<Envelope>> eventsForAllThreads = path.getEventsAlongPath(); // Events corresponding to each thread
		
		log("Events");
		Iterator<Integer> threadIDs = eventsForAllThreads.keySet().iterator();
		while(threadIDs.hasNext()) {
			int threadId = threadIDs.next();
			ArrayList<Envelope> events = eventsForAllThreads.get(threadId);
			scanThroughEvents(events);
		}
		BoolExpr pathConditionExpr = convertPathConditionStrToExpr(path.getPathCondition());
		solver.add(pathConditionExpr);
		
		formPropertyRule(pathConditionExpr);
		Status status = solver.check();
		if(status == Status.SATISFIABLE)
			return solver.getModel();
		
		return null;
	}
}