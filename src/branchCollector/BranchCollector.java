package branchCollector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.EqExpr;
import soot.jimple.Expr;
import soot.jimple.GeExpr;
import soot.jimple.GtExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.LeExpr;
import soot.jimple.LtExpr;
import soot.jimple.NeExpr;
import soot.jimple.ParameterRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.jimple.internal.AbstractBinopExpr;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ArraySparseSet;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;

class Condition {
	String op1;
	String op2;
	String oper;
	Condition(String op1, String op2, String oper) {
		this.op1 = op1;
		this.op2 = op2;
		this.oper = oper;
	}
	Condition() {}
	
	public String toString() {
		return op1 + " " + oper + " " + op2;
	}
}

public class BranchCollector extends ForwardBranchedFlowAnalysis<FlowSet> {
	Body b;
	ExceptionalUnitGraph ug;
	FlowSet inval, outval;
	SootMethod currentMethod;
	Map<String, Value> mapTempVariableToCondition;
	
	BranchCollector(UnitGraph g) {
		super(g);
		ug = (ExceptionalUnitGraph)g;
		b = ug.getBody();
		currentMethod = b.getMethod();
		mapTempVariableToCondition = new HashMap<String, Value>();
		doAnalysis();
	}
			
	@Override
	protected void copy(FlowSet src, FlowSet dest) {
		// TODO Auto-generated method stub
		FlowSet srcSet = (FlowSet)src;
		FlowSet destSet = (FlowSet)dest;
		srcSet.copy(destSet);
	}
	
	@Override
	protected void flowThrough(FlowSet in, Unit unit, List fallOut, List branchOuts) {
		// TODO Auto-generated method stub
		
		inval = (FlowSet<Condition>)in;
		FlowSet outFall = new ArraySparseSet<Condition>();
		FlowSet outBranch = new ArraySparseSet<Condition>();
		copy(inval, outFall);
		copy(inval, outBranch);
		
//		System.out.println("Inval: ");
//		Iterator invalIt = inval.iterator();
//		while(invalIt.hasNext()) {
//			System.out.println(invalIt.next() + " ");
//		}
		
		Stmt s = (Stmt) unit;
//		System.out.println("Statement: " + s);
		
		if(s instanceof AssignStmt) {
			AssignStmt assign = (AssignStmt) s;
			Value v = assign.getRightOp();
			String var = assign.getLeftOp().toString();
			mapTempVariableToCondition.put(var, v); // If the key already exists, then it is replaced.
		}
		
		if(s instanceof IdentityStmt) {
			IdentityStmt assign = (IdentityStmt) s;
			Value v = assign.getRightOp();				
			String var = assign.getLeftOp().toString();
			mapTempVariableToCondition.put(var, v); // If the key already exists, then it is replaced.
		}
		
		if(s instanceof IfStmt) {
			IfStmt ifStmt = (IfStmt) s;
			handleIfStmt(ifStmt, in, outFall, outBranch);
		}
		
//		if(s instanceof SwitchStmt) {
//		SwitchStmt switchStmt = (SwitchStmt) s;
//		System.out.println("Statement: " + s);
//		System.out.println("Successors: ");
//		System.out.println(ug.getSuccsOf(unit) + "\n" + switchStmt.getTargets());
//		List<Unit> targets = switchStmt.getTargets();
//		Iterator<Unit> targetsIt = targets.iterator();
//		while(targetsIt.hasNext()) {
//			Unit u = targetsIt.next();
//			System.out.println((Stmt)u);
//		}	
//	}
		
		if(s instanceof ThrowStmt) {
			System.out.println("Path condition of the throw " + s + ": ");
			printPathCondition(inval);
		}
		
		// Now copy the computed info to all successors
	    for (Iterator<FlowSet> it = fallOut.iterator(); it.hasNext();) {
	      copy(outFall, it.next());
	    }
	    for (Iterator<FlowSet> it = branchOuts.iterator(); it.hasNext();) {
	      copy(outBranch, it.next());
	    }
	}
	
	private void handleIfStmt(IfStmt ifStmt, Object in, FlowSet outFall, FlowSet outBranch) {
		Value condition = ifStmt.getCondition();
		
		if(condition instanceof AbstractBinopExpr) {
			Condition fall = null;
			Condition branch = null;
			
			if (condition instanceof EqExpr) {
				EqExpr expr = (EqExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "!=");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "==");
			}
			else if(condition instanceof NeExpr) {
				NeExpr expr = (NeExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "==");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "!=");
			}
			else if(condition instanceof LtExpr) {
				LtExpr expr = (LtExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), ">=");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "<");
			}
			else if(condition instanceof LeExpr) {
				LeExpr expr = (LeExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), ">");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "<=");
			}
			else if(condition instanceof GeExpr) {
				GeExpr expr = (GeExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "<");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), ">=");
			}
			else if(condition instanceof GtExpr) {
				GtExpr expr = (GtExpr) condition;
				fall = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "<");
				branch = new Condition(expr.getOp1().toString(), expr.getOp2().toString(), "<=");
			}
			outFall.add(fall);
			outBranch.add(branch);
		}
	}
	
	
	private void printPathCondition(FlowSet inval) {
		Iterator<Condition> invalIt = inval.iterator();
		while(invalIt.hasNext()) {
			Condition cond = invalIt.next();
			System.out.print("[" + rollout(cond.op1) + " " + cond.oper + " " + rollout(cond.op2) + "] ");
		}
		System.out.println();
	}
	
	private String rollout(String op) {
		Value val = mapTempVariableToCondition.get(op);
		if(val==null)
			return op;
		if(val instanceof StaticFieldRef || val instanceof Local)
			return val.toString();
		if(val instanceof ParameterRef)
			return op;
		if(val instanceof Expr) {
			if(val instanceof BinopExpr) {
				BinopExpr binopExpr = (BinopExpr) val;
				return "(" + rollout(binopExpr.getOp1().toString()) + binopExpr.getSymbol() + rollout(binopExpr.getOp2().toString()) + ")";
			}
		}
		return "null";
	}
	
	@Override
	protected void merge(FlowSet in1, FlowSet in2, FlowSet out) {
		// TODO Auto-generated method stubs
		FlowSet inval1 = (FlowSet)in1;
		FlowSet inval2 = (FlowSet)in2;
		FlowSet outSet = (FlowSet)out;
		inval1.union(inval2, outSet);
	}
	
	@Override
	protected FlowSet entryInitialFlow() {
		// TODO Auto-generated method stub
		FlowSet inval = new ArraySparseSet();
		return inval;
	}

	@Override
	protected FlowSet newInitialFlow() {
		// TODO Auto-generated method stub
		return new ArraySparseSet();
	}
}