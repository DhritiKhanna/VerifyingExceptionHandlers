package branchCollector;

import soot.SootClass;
import soot.Scene;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import soot.G;
import soot.Pack;
import soot.PackManager;
import soot.Transform;
import soot.options.Options;

public class Driver {
	
	public static String className;
	public static String benchmark;
	
	public static Set<String> toBeAnalyzed = new HashSet<String>(); // Names of the classes to be analyzed
	public static Set<String> alreadyAnalyzed = new HashSet<String>(); // Names of the classes already analyzed
	
	public Driver(String className, String benchMark) {
		Driver.className = className;
		Driver.benchmark = benchMark;
	}
	
	public void run() {
		
		G.reset();
		
		String path = Scene.v().getSootClassPath();
		System.out.println("\npath:" + path + "\n");
		  
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Pack jtp = PackManager.v().getPack("jtp");
		jtp.add(new Transform("jtp.instrumenter", new Analyzer()));
		Options.v().set_output_format(Options.output_format_jimple);
		Options.v().set_output_dir("/home/dhriti/Dropbox/new-workspace/Vehdic/sootOutput");
		 
		Scene.v().extendSootClassPath(System.getProperty("java.class.path"));
		System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Analyzing class: " + className);
		SootClass sootClass = Scene.v().loadClassAndSupport(className);
		alreadyAnalyzed.add(className);
		
		List<String> sootArgs = new LinkedList<String>(Arrays.asList(className));
		  
		//enable points-to analysis
		sootArgs.add("-p");
		sootArgs.add("cg");
		sootArgs.add("enabled:true");
		//enable Spark
		sootArgs.add("-p");
		sootArgs.add("cg.spark");
		sootArgs.add("enabled:true");
		//enable use-original names
		sootArgs.add("-p");
		sootArgs.add("jb");
		sootArgs.add("use-original-names:true");
		
		String[] argsArray = (String[]) sootArgs.toArray(new String[sootArgs.size()]);
		
		soot.Main.main(argsArray);
		  
		while(!toBeAnalyzed.isEmpty()) {
		  
			G.reset();
			  
		    String classToBeAnalyzed = toBeAnalyzed.iterator().next();
			toBeAnalyzed.remove(classToBeAnalyzed);
			while(alreadyAnalyzed.contains(classToBeAnalyzed)) {
				classToBeAnalyzed = toBeAnalyzed.iterator().next();
				toBeAnalyzed.remove(classToBeAnalyzed);
			}
			alreadyAnalyzed.add(classToBeAnalyzed);
			  
			Scene.v().setSootClassPath(path);
			Scene.v().extendSootClassPath(System.getProperty("java.class.path"));
			sootClass = Scene.v().loadClassAndSupport(classToBeAnalyzed);
			System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Analyzing class: " + classToBeAnalyzed);
			Options.v().setPhaseOption("jb", "use-original-names:true");
			jtp = PackManager.v().getPack("jtp");
			jtp.add(new Transform("jtp.instrumenter", new Analyzer()));
			Options.v().set_output_format(Options.output_format_jimple);
			Options.v().set_output_dir("/home/dhriti/Dropbox/new-workspace/Vehdic/sootOutput");
			Options.v().setPhaseOption("cg.spark", "on");
			Scene.v().loadNecessaryClasses();
		  
			sootArgs = new LinkedList<String>(Arrays.asList(classToBeAnalyzed));
		  
			//enable points-to analysis
			sootArgs.add("-p");
			sootArgs.add("cg");
			sootArgs.add("enabled:true");
			//enable Spark
			sootArgs.add("-p");
			sootArgs.add("cg.spark");
			sootArgs.add("enabled:true");
			//enable use-original names
			sootArgs.add("-p");
			sootArgs.add("jb");
			sootArgs.add("use-original-names:true");
			
			argsArray = (String[]) sootArgs.toArray(new String[sootArgs.size()]);
		  
			soot.Main.main(argsArray);	
		}
	}
}