package instrumenter;

import soot.SootClass;
import soot.SootField;
import soot.SootResolver;
import soot.Scene;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
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
import soot.util.Chain;
import util.Pair;
import main.Main;

public class Driver {
	
	public static int[] linenos;
	public static String benchmark;
	public static final String pathToProject = "/home/dhriti/Dropbox/new-workspace/VerifyingExceptionHandlers/";
	
	public static Set<String> toBeAnalyzed = new HashSet<String>(); // Names of the classes to be analyzed
	public static Set<String> alreadyAnalyzed = new HashSet<String>(); // Names of the classes already analyzed
	
	public Driver(int[] linenos) {
		Driver.linenos = linenos;
	}
	
	public ArrayList<Pair<String, String>> run() {
		String path = Scene.v().getSootClassPath();
		System.out.println("\npath:" + path + "\n");
		  
		Options.v().setPhaseOption("jb", "use-original-names:true");
		Pack jtp = PackManager.v().getPack("jtp");
		jtp.add(new Transform("jtp.instrumenter", new Instrumenter(linenos)));
		//Options.v().set_output_format(Options.output_format_jimple); // We need the class file, not Jimple
		Options.v().set_output_dir(pathToProject + "sootOutput");
		Options.v().set_keep_line_number(true);
		//Options.v().set_whole_program(true); // Opened for PAH2
				 
		Scene.v().extendSootClassPath(System.getProperty("java.class.path"));
		System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Analyzing class: " + Main.className);
		SootClass sootClass = Scene.v().loadClassAndSupport(Main.className);
		//Scene.v().loadNecessaryClasses();
		
		Chain <SootField> fields = sootClass.getFields();
		ArrayList<Pair<String, String>> classFields = new ArrayList<Pair<String, String>>();
		for (SootField f : fields) {
			classFields.add(new Pair<String, String>(f.getName(), f.getType().toString()));
		}
		
		alreadyAnalyzed.add(Main.className);
		benchmark = sootClass.getPackageName(); // TODO: Or sootClass.getJavaPackageName(); 
		
		List<String> sootArgs = new LinkedList<String>(Arrays.asList(Main.className));
		  
		//sootArgs.add("-w");
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
		//Scene.v().addBasicClass("java.io.IOException", SootClass.SIGNATURES);
		
		soot.Main.main(argsArray);
		
		// Move the class files from sootOutput folder to bin folder after static instrumentation is done.
		String sourcePath = pathToProject + "sootOutput/" + Main.className.replace('.', '/') + ".class";
		File file = new File(sourcePath);
		String destPath = pathToProject + "binInstrumented/" + Main.className.replace('.', '/') + ".class";
		file.renameTo(new File(destPath));

		// I comment the below loop for eclipse PAH2
		while(!toBeAnalyzed.isEmpty()) {
			
		    String classToBeAnalyzed = toBeAnalyzed.iterator().next();
			toBeAnalyzed.remove(classToBeAnalyzed);
			if(alreadyAnalyzed.contains(classToBeAnalyzed)) 
				continue;
			
			alreadyAnalyzed.add(classToBeAnalyzed);
			
			G.reset();
			
			path = Scene.v().getSootClassPath();
			System.out.println("\npath:" + path + "\n");
			
			fields = sootClass.getFields();
			for (SootField f : fields) {
				classFields.add(new Pair<String, String>(f.getName(), f.getType().toString()));
			}
			
			System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~Analyzing class: " + classToBeAnalyzed);
			Options.v().setPhaseOption("jb", "use-original-names:true");
			jtp = PackManager.v().getPack("jtp");
			jtp.add(new Transform("jtp.instrumenter", new Instrumenter(linenos)));
			//Options.v().set_output_format(Options.output_format_jimple); // We want the class file and not Jimple
			Options.v().set_output_dir(pathToProject + "sootOutput");
			Options.v().set_keep_line_number(true);
			Options.v().setPhaseOption("cg.spark", "on");
			//Options.v().set_whole_program(true);
			
			Scene.v().extendSootClassPath(System.getProperty("java.class.path"));
			
			sootClass = Scene.v().loadClassAndSupport(classToBeAnalyzed);
			//Scene.v().loadNecessaryClasses();
		  
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
		  
			//Scene.v().addBasicClass("java.io.IOException", SootClass.SIGNATURES);
			soot.Main.main(argsArray);
			
			// Move the class files from sootOutput folder to bin folder after static instrumentation is done.
			sourcePath = pathToProject + "sootOutput/" + classToBeAnalyzed.replace('.', '/') + ".class"; 
			file = new File(sourcePath);
			destPath = pathToProject + "binInstrumented/" + classToBeAnalyzed.replace('.', '/') + ".class";
			file.renameTo(new File(destPath));
		}
		return classFields;
	}
}