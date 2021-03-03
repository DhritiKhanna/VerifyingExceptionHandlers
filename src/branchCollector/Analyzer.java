package branchCollector;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.SootMethod;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class Analyzer extends BodyTransformer 
{	
	@Override
	protected void internalTransform(Body body, String arg1, Map arg2) {
		SootMethod sootMethod = body.getMethod();
		if(!sootMethod.getName().contains("$")) {
			System.out.println("\n\nAnalyzing for method : " + sootMethod.getName());
			UnitGraph g = new ExceptionalUnitGraph(sootMethod.getActiveBody());
			BranchCollector branchCollector = new BranchCollector(g);
		}
	}
}