package chord.analyses.incrsolver;

import chord.project.analyses.JavaAnalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import chord.bddbddb.Dom;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.var.DomV;
import chord.analyses.derivsz.TupleStats;
import chord.analyses.field.DomF;
import chord.analyses.type.DomT;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;


@Chord(name = "incr-libaxiomdumper")
public class LibAxiomDumper extends JavaAnalysis {		
	String[] configFiles;
	HashMap<String, int[]> mark = new HashMap<String, int[]>();
	List<ITask> tasks;
	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	private String sep = "####";
	private String summaryDir;
	private String appName;
	
	@Override
	public void run() {
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("incr-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-incrinit-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-summary-dlog_XZ90_"));
		System.setProperty("chord.ctxt.kind", "co");
		String chordMain = System.getenv("CHORD_MAIN");
		String kobjConfig = chordMain + File.separator + "../incubator/src/chord/analyses/derivsz/cspa-kobj-summary-dlog_XZ90_.config";
		configFiles = new String[]{ kobjConfig };

		summaryDir = System.getProperty("chord.incrsolver.summaryDir");
		appName = System.getProperty("chord.incrsolver.appName");
		runAllTasks();
		markDoms();
		printIpRelsForPreload();
	}
	
	
	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	
	private static Set<Tuple> tuples(final ProgramRel r) {
		Set<Tuple> ts = new HashSet<Tuple>();
		for (int[] args : r.getAryNIntTuples())
			ts.add(new Tuple(r, args));
		return ts;
	}
	
	
	private boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		for (int i = 0; i < dArr.length; i++) {
			if (mark.containsKey(dArr[i].getName()))
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
		
	private void markDoms()
	{
		List<Dom> domArray = new ArrayList<Dom>();
		domArray.add((DomI) ClassicProject.g().getTrgt("I"));
		domArray.add((DomH) ClassicProject.g().getTrgt("H"));
		domArray.add((DomM) ClassicProject.g().getTrgt("M"));
		domArray.add((DomC) ClassicProject.g().getTrgt("C"));
		domArray.add((DomV) ClassicProject.g().getTrgt("V"));
		domArray.add((DomF) ClassicProject.g().getTrgt("F"));
		domArray.add((DomT) ClassicProject.g().getTrgt("T"));
		
		for(Dom dom : domArray) {
			if (!mark.containsKey(dom.getName())) {
				int[] markVec = new int[dom.size()];
				markDomain(dom, markVec);
				mark.put(dom.getName(), markVec);
			}
		}	
	}
	
	private void markDomain(Dom dom, int[] vec) {
		
		if(dom.getName().equals("I")) {
			DomI domI = (DomI)dom;
			for (int i = 0; i < domI.size(); i++) {
				Quad q = (Quad) domI.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}	
		else if(dom.getName().equals("H")) {
			DomH domH = (DomH)dom;
			vec[0] = 2;  // Library
			for (int i = 1; i < domH.size(); i++) {
				Quad q = (Quad) domH.get(i);
				jq_Class cl = q.getMethod().getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("M")){
			DomM domM = (DomM)dom;
			vec[0] = 4;
			for (int i = 1; i < domM.size(); i++) { 
				jq_Class cl = domM.get(i).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("C")){
			DomC domC = (DomC)dom;
			vec[0] = 2; //Library
			for (int i = 1; i < domC.size(); i++) {
				Ctxt ctxt = (Ctxt) domC.get(i);
				int cnt = ctxt.length();
				Quad[] qArr;
				boolean valid;
				if (cnt > 0) {
					qArr = ctxt.getElems();
					valid = true;
					for (int j = 0; j < cnt; j++) {
						jq_Class cl = qArr[j].getMethod().getDeclaringClass();
						if (!cl.getName().matches(libraryPrefix)) {
							valid = false;
							break;
						}
					}
				} else {
					qArr = null;
					valid = true;
				}
				if (valid)
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
				
			}
		}
		else if (dom.getName().equals("V")) {
			DomV domV = (DomV)dom;
			for (int i = 0; i < domV.size(); i++) { 
				Register v = domV.get(i);
				jq_Class cl = domV.getMethod(v).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("F")){
			DomF domF = (DomF)dom;
			vec[0] = 2;
			for (int i = 1; i < domF.size(); i++) {
				jq_Class cl = domF.get(i).getDeclaringClass();
				String clName = cl.toString();
				if (clName.matches(libraryPrefix))
					vec[i] = 2;   // Library
				else 
					vec[i] = 4;   // Application
			}
		}
		else if (dom.getName().equals("T")){
			vec[0] = 2;
			DomT domT = (DomT)dom;
			for (int i = 1; i < domT.size(); i++) {
				if (domT.get(i).getName().matches(libraryPrefix))
					vec[i] = 2;   // Library
				else if (domT.get(i).getName().matches(fundamentalTypePrefix))
					vec[i] = 2;   // Library
				else
					vec[i] = 4;   // Application
			}
		}
	}
	
	private void printIpRelsForPreload() {
		PrintWriter currPW;
		ArrayList<String> relList = getIpRels();
		boolean reorder = Boolean.getBoolean("chord.incrsolver.reorderDoms");
		String relDirName = summaryDir + "/preload";
		File relDir = new File(relDirName);
		if (!relDir.exists()) {
			try{
		        relDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("preload dir " + relDirName + " does not exist - unable to create it.");
		     }        
		}
		for (String relName : relList) {
			ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
			rel.load();
			Set<Tuple> relTuples = tuples(rel);
			String fName = relDirName + File.separator + appName + "_" + relName + ".txt";
			File relFile = new File(fName);
			try {
				relFile.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try {
				currPW = new PrintWriter(relFile);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			for (Tuple t : relTuples) {
				if (belongsToLib(t)) {
					if (reorder)
						currPW.println(t.toString());
					else
						currPW.println(t.toSummaryString(sep));
				}
			}
			currPW.flush();
			currPW.close();
		}
	}
	
	private ArrayList<String> getIpRels() {
		ArrayList<String> preloadRelList = new ArrayList<String>();
		preloadRelList.add("HT");
		preloadRelList.add("cha");
		preloadRelList.add("sub");
		preloadRelList.add("MI");
		preloadRelList.add("statIM");
		preloadRelList.add("specIM");
		preloadRelList.add("virtIM");
		preloadRelList.add("MobjValAsgnInst");
		preloadRelList.add("MobjVarAsgnInst");
		preloadRelList.add("MgetInstFldInst");
		preloadRelList.add("MputInstFldInst");
		preloadRelList.add("MgetStatFldInst");
		preloadRelList.add("MputStatFldInst");
		preloadRelList.add("clsForNameIT");
		preloadRelList.add("objNewInstIH");
		preloadRelList.add("objNewInstIM");
		preloadRelList.add("conNewInstIH");
		preloadRelList.add("conNewInstIM");
		preloadRelList.add("aryNewInstIH");
		preloadRelList.add("classT");
		preloadRelList.add("staticTM");
		preloadRelList.add("staticTF");
		preloadRelList.add("clinitTM");
		preloadRelList.add("MmethArg");
		preloadRelList.add("MspcMethArg");
		preloadRelList.add("IinvkArg");
		preloadRelList.add("IinvkArg0");
		preloadRelList.add("IinvkRet");
		preloadRelList.add("argCopy");
		preloadRelList.add("retCopy");
		preloadRelList.add("VCfilter");
		preloadRelList.add("CC");
		preloadRelList.add("CH");
		preloadRelList.add("CI");
		preloadRelList.add("epsilonM");
		preloadRelList.add("kobjSenM");
		preloadRelList.add("ctxtCpyM");
		preloadRelList.add("IM");
		preloadRelList.add("VH");
		return preloadRelList;
	}
}

