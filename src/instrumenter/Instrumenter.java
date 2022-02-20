package instrumenter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import soot.Body;
import soot.BodyTransformer;
import soot.BooleanType;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.VoidType;
import soot.JastAddJ.AssertStmt;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.EqExpr;
import soot.jimple.Expr;
import soot.jimple.FieldRef;
import soot.jimple.GeExpr;
import soot.jimple.GtExpr;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LeExpr;
import soot.jimple.LtExpr;
import soot.jimple.NeExpr;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.OrExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.ThrowStmt;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JInvokeStmt;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph.ExceptionDest;
import soot.toolkits.graph.UnitGraph;
import soot.util.Chain;

public class Instrumenter extends BodyTransformer 
{
	public static int[] linenos;
	Body body;
	LocalGenerator generator;
	
	Instrumenter(int[] linenos) {
		Instrumenter.linenos = linenos;
	}
	
	@Override
	protected void internalTransform(Body methodBody, String arg1, Map arg2) {
		body = methodBody;
		generator = new LocalGenerator(body);
		SootMethod sootMethod = body.getMethod();
		if(!sootMethod.getName().contains("$")) {
			UnitGraph ug = new ExceptionalUnitGraph(sootMethod.getActiveBody());

			SootMethod method = body.getMethod();
		    // debugging
		    System.out.println("instrumenting method : " + method.getSignature());
		    // get body's unit as a chain
		    Chain units = body.getUnits();
		    // get a snapshot iterator of the unit since we are going to
		    // mutate the chain when iterating over it.
		    
		    Iterator stmtIt = units.snapshotIterator();
		    // typical while loop for iterating over each statement
		    while (stmtIt.hasNext()) {
		    	// cast back to a statement.
		    	Unit unit = (Unit) stmtIt.next();
		    		    	
		    	Stmt s = (Stmt)unit;

		    	int lineno = s.getTag("LineNumberTag")!=null ? Integer.parseInt(s.getTag("LineNumberTag").toString()) : 0;
		    			    	
		    	if(s instanceof JInvokeStmt) {
					System.out.println("~~~~~~~~~~Invoke statement: " + ((JInvokeStmt)s).getInvokeExpr().getMethod().getDeclaration());
					SootMethod m = ((JInvokeStmt)s).getInvokeExpr().getMethod();
					if(Driver.benchmark.startsWith(m.getDeclaringClass().getPackageName())) { // Method from the same library but different class
						// I have taken library name from the user in the form of first few words of the package name: example, org.commons.pool

						// Add this class in a queue. You have to analyze this class next
						Driver.toBeAnalyzed.add(m.getDeclaringClass().toString());		
					}
					else if(!Driver.benchmark.startsWith(m.getDeclaringClass().getPackageName())) { // Invoked method is from a different library 
						// Check the method signature if available
						String decl = m.getDeclaration();
//						System.out.println("Method declaration: " + decl);
						int index = decl.indexOf("throws");
						if(index != -1) {
							String exceptions = decl.substring(index+7); // 'throws' is a 7 letter word
							StringTokenizer str = new StringTokenizer(exceptions, " ");
							if(str.hasMoreTokens()) {
								String exception = str.nextToken();
								System.out.println("~~~~~~~~~~Exception: " + exception);
								// instrument the throw here
								instrument(exception, s, lineno);
							}
						}
					}
				}
//		    	System.out.println("Statement: " + s);
//				Collection<ExceptionDest> l = ((ExceptionalUnitGraph) ug).getExceptionDests(unit);
//				//List<Unit> l = ug.getExceptionalSuccsOf((Unit)unit);
//				Iterator<ExceptionDest> i = l.iterator();
//				while(i.hasNext()) {
//					ExceptionDest e = (ExceptionDest) i.next();
//					//Unit e = (Unit) i.next();
//					if(e.getTrap()!=null)
//					{	
//						System.out.println("Trap::::::::::::::: " + e.getTrap().getException());
//						System.out.println("Begin unit: " + e.getTrap().getBeginUnit());
//						System.out.println("End unit: " + e.getTrap().getEndUnit() + "\n");
//					}
//				}
		    }	
		}
	}
	
	/**
	 * Instrument a throw clause before a statement and comment out that statement
	 * @param exception the exception to be thrown using throw clause
	 * @param s the statement before which an exception is to be thrown
	 * @param methodBody insert the statement inside this methodBody
	 */
	private void instrument(String exceptionType, Stmt s, int lineno) {
		// Add if(false) {
		Value linenumber = generator.generateLocal(IntType.v());
		AssignStmt assignStmt = new JAssignStmt(linenumber, IntConstant.v(lineno));
		body.getUnits().insertBefore(assignStmt, s);
		
		NopStmt nopBeforeIfs = Jimple.v().newNopStmt();
		body.getUnits().insertBefore(nopBeforeIfs, s);
		NopStmt nopBeforeException = Jimple.v().newNopStmt();
		body.getUnits().insertAfter(nopBeforeException, nopBeforeIfs);
		NopStmt nopEnd = Jimple.v().newNopStmt();
		body.getUnits().insertBefore(nopEnd, s);		
		
		int numberOfValues = linenos.length;
		List<Unit> newUnits = new ArrayList<Unit>();
		for(int i=0; i<numberOfValues-1; ++i) {
			IfStmt ifStmt = Jimple.v().newIfStmt(Jimple.v().newEqExpr(linenumber, IntConstant.v(linenos[i])), nopBeforeException);
			newUnits.add(ifStmt);
		}
		IfStmt ifStmt = Jimple.v().newIfStmt(Jimple.v().newNeExpr(linenumber, IntConstant.v(linenos[numberOfValues-1])), s);
		newUnits.add(ifStmt);
		
		body.getUnits().insertAfter(newUnits, nopBeforeIfs);
				
		// Add assert false: "point to be hit"
		//createAssert(body, s, nopAfter);
				
		// Add the throw Stmt
		Local l = Jimple.v().newLocal("localToBeThrown", RefType.v(exceptionType));
		body.getLocals().add(l);
		newUnits = new ArrayList<Unit>();
		Unit u1 = Jimple.v().newAssignStmt(l, Jimple.v().newNewExpr(RefType.v(exceptionType)));
		Unit u2 = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(l,
		          			Scene.v().makeMethodRef(Scene.v().getSootClass(exceptionType), "<init>",
		          			Collections.<Type>emptyList(), VoidType.v(), false), Collections.<Value>emptyList()));
		Unit u3 = Jimple.v().newThrowStmt(l);
		newUnits.add(u1);
		newUnits.add(u2);
		newUnits.add(u3);
		body.getUnits().insertAfter(newUnits, nopBeforeException);
		
		// Comment out the function call
		//body.getUnits().remove(s);
	}
	
	// Code inspired from https://www.javatips.net/api/soot-master/src/soot/javaToJimple/JimpleBodyBuilder.java 
	private void createAssert(Body body, Stmt s, Stmt nop) {
	    // check if assertions are disabled
		Local testLocal = generator.generateLocal(BooleanType.v());
	    soot.SootFieldRef assertField = soot.Scene.v().makeFieldRef(body.getMethod().getDeclaringClass(), "$assertionsDisabled", soot.BooleanType.v(), true);
	    FieldRef assertFieldRef = Jimple.v().newStaticFieldRef(assertField);
	    AssignStmt fieldAssign = Jimple.v().newAssignStmt(testLocal, assertFieldRef);
	    body.getUnits().insertBefore(fieldAssign, s);
	    
	    ConditionExpr cond1 = Jimple.v().newNeExpr(testLocal, IntConstant.v(0));
	    IfStmt testIf = Jimple.v().newIfStmt(cond1, nop);
	    body.getUnits().insertBefore(testIf, s);
	    // assertion failure code
	    Local failureLocal = generator.generateLocal(soot.RefType.v("java.lang.AssertionError"));
	    NewExpr newExpr = Jimple.v().newNewExpr(soot.RefType.v("java.lang.AssertionError"));
	    AssignStmt newAssign = Jimple.v().newAssignStmt(failureLocal, newExpr);
	    body.getUnits().insertBefore(newAssign, s);
	    
	    soot.SootMethodRef methToInvoke;
	    ArrayList paramTypes = new ArrayList();
	    ArrayList params = new ArrayList();
	    
        soot.Value errorExpr = StringConstant.v("point to be hit");
        if (errorExpr instanceof ConditionExpr) {
            errorExpr = handleCondBinExpr((ConditionExpr) errorExpr);
        }
        paramTypes.add(soot.RefType.v("java.lang.Object"));
        params.add(errorExpr);
	  
	    methToInvoke = soot.Scene.v().makeMethodRef(soot.Scene.v().getSootClass("java.lang.AssertionError"), "<init>", paramTypes, soot.VoidType.v(), false);
	    SpecialInvokeExpr invokeExpr = Jimple.v().newSpecialInvokeExpr(failureLocal, methToInvoke, params);
	    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(invokeExpr);
	    body.getUnits().insertBefore(invokeStmt, s);
	    
	    ThrowStmt throwStmt = Jimple.v().newThrowStmt(failureLocal);
	    body.getUnits().insertBefore(throwStmt, s);
	}
	
	private soot.Local handleCondBinExpr(ConditionExpr condExpr) {
	    
        soot.Local boolLocal = Jimple.v().newLocal("boolLocal", soot.BooleanType.v());
        body.getLocals().add(boolLocal);

        Stmt noop1 = Jimple.v().newNopStmt();
            
        soot.Value newVal;
       
        newVal = reverseCondition(condExpr);
        newVal = handleDFLCond((ConditionExpr)newVal);

        Stmt ifStmt = Jimple.v().newIfStmt(newVal, noop1);
        body.getUnits().add(ifStmt);

        body.getUnits().add(Jimple.v().newAssignStmt(boolLocal, IntConstant.v(1)));

        Stmt noop2 = Jimple.v().newNopStmt();
        
        Stmt goto1 = Jimple.v().newGotoStmt(noop2);

        body.getUnits().add(goto1);

        body.getUnits().add(noop1);
        
        body.getUnits().add(Jimple.v().newAssignStmt(boolLocal, IntConstant.v(0)));

        body.getUnits().add(noop2);

        return boolLocal;
	}
	
	/**
     * in bytecode and Jimple the conditions in conditional binary 
     * expressions are often reversed
     */
    private soot.Value reverseCondition(ConditionExpr cond) {
    
        ConditionExpr newExpr;
        if (cond instanceof EqExpr) {
            newExpr = Jimple.v().newNeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof NeExpr) {
            newExpr = Jimple.v().newEqExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof GtExpr) {
            newExpr = Jimple.v().newLeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof GeExpr) {
            newExpr = Jimple.v().newLtExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof LtExpr) {
            newExpr = Jimple.v().newGeExpr(cond.getOp1(), cond.getOp2());
        }
        else if (cond instanceof LeExpr) {
            newExpr = Jimple.v().newGtExpr(cond.getOp1(), cond.getOp2());
        }
        else {
            throw new RuntimeException("Unknown Condition Expr");
        }


        newExpr.getOp1Box().addAllTagsOf(cond.getOp1Box());
        newExpr.getOp2Box().addAllTagsOf(cond.getOp2Box());
        return newExpr;
    }
    
    /**
     * Special conditions for doubles and floats and longs
     */
    private soot.Value handleDFLCond(ConditionExpr cond){
        soot.Local result = Jimple.v().newLocal("tempInHandleDFLCond", soot.ByteType.v());
        body.getLocals().add(result);
        
        Expr cmExpr = null;
        if (isDouble(cond.getOp1()) || isDouble(cond.getOp2()) || isFloat(cond.getOp1()) || isFloat(cond.getOp2())) {
            // use cmpg and cmpl
            if ((cond instanceof GeExpr) || (cond instanceof GtExpr)) {
                // use cmpg
                cmExpr = Jimple.v().newCmpgExpr(cond.getOp1(), cond.getOp2());
            }
            else {
                // use cmpl
                cmExpr = Jimple.v().newCmplExpr(cond.getOp1(), cond.getOp2());
            }
        }
        else if (isLong(cond.getOp1()) || isLong(cond.getOp2())) {
            // use cmp
            cmExpr = Jimple.v().newCmpExpr(cond.getOp1(), cond.getOp2());
        }
        else {
            return cond;
        }
        Stmt assign = Jimple.v().newAssignStmt(result, cmExpr);
        body.getUnits().add(assign);

        if (cond instanceof EqExpr){
	        cond = Jimple.v().newEqExpr(result, IntConstant.v(0));
		}
		else if (cond instanceof GeExpr){
			cond = Jimple.v().newGeExpr(result, IntConstant.v(0));
		}
		else if (cond instanceof GtExpr){
			cond = Jimple.v().newGtExpr(result, IntConstant.v(0));
		}
		else if (cond instanceof LeExpr){
		    cond = Jimple.v().newLeExpr(result, IntConstant.v(0));
		}
		else if (cond instanceof LtExpr){
		    cond = Jimple.v().newLtExpr(result, IntConstant.v(0));
		}
		else if (cond instanceof NeExpr){
	        cond = Jimple.v().newNeExpr(result, IntConstant.v(0));
		}
        else {
            throw new RuntimeException("Unknown Comparison Expr");
        }
    
        return cond;
    }
    
    private boolean isDouble(soot.Value val) {
        if (val.getType() instanceof soot.DoubleType) return true;
        return false;
    }
    
    private boolean isFloat(soot.Value val) {
        if (val.getType() instanceof soot.FloatType) return true;
        return false;
    }
    
    private boolean isLong(soot.Value val) {
        if (val.getType() instanceof soot.LongType) return true;
        return false;
    }
}