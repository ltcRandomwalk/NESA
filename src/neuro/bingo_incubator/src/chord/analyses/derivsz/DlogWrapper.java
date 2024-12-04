package chord.analyses.derivsz;

import chord.project.analyses.JavaAnalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import chord.bddbddb.Dom;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.point.DomP;
import chord.analyses.provenance.typestate.DomMS;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.var.DomV;
import chord.analyses.field.DomF;
import chord.analyses.type.DomT;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;


/*
*
* A wrapper to invoke a dlog analysis and print some stats  
* @author Sulekha
*
* -Dchord.incrsolver.printRels            - [false] If true, print all the tuples of all relations in a fixed dir (one file per rel)
* -Dchord.incrsolver.appName              - [""] name of the app on which this analysis is acting upon.                                     
*
*/
@Chord(name = "dlogwrapper")
public class DlogWrapper extends JavaAnalysis {

	private HashMap<String, int[]> mark = new HashMap<String, int[]>();
	private List<ITask> tasks;

	private boolean printOutputRels;
	private String selfAppName;
	
	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	
	@Override
	public void run() {
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kcfa-incrinit-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kcfa-v1-dlog"));
		System.setProperty("chord.ctxt.kind", "cs");
		
		printOutputRels = Boolean.getBoolean("chord.incrsolver.printRels");
		selfAppName = System.getProperty("chord.incrsolver.appName");
		
		runAllTasks();
		markDoms();
		printRelations("IDBDir");
		
	}
	
	
	private void printRelations (String prDir) {
		List<ProgramRel> producedTrgts = getProducedTrgts();
		String appDirName = "";
		if (printOutputRels) {
			String benchDir = System.getenv("PJBENCH");
			String dirName = benchDir + "/" + prDir;
			File relDir = new File(dirName);
			if (!relDir.exists()) {
				try{
			        relDir.mkdir();
			     } catch(SecurityException se){
			        System.out.println("incrsolver: printRelations: " + dirName + " does not exist - unable to create it.");
			     }        
			}
			appDirName = dirName + "/" + selfAppName;
			File appDir = new File(appDirName);
			if (!appDir.exists()) {
				try{
			        appDir.mkdir();
			     } catch(SecurityException se){
			        System.out.println("IS: App dir " + appDirName + " does not exist - unable to create it.");
			        return;
			     }        
			}
		}
		int total = 0;
		int inLib = 0;
		int i = 0;
		for (ProgramRel currTgt: producedTrgts) {
			int[] cnts = new int[producedTrgts.size()];
			currTgt.load();
			System.out.println("IS:RELATION TOTAL CNT: " + currTgt.getName() + "    " + currTgt.size());			
			cnts[i] = printRelTuples(currTgt, appDirName);
			System.out.println("IS:RELATION LIB CNT: " + currTgt.getName() + "    " + cnts[i]);
			total += currTgt.size();
			inLib += cnts[i];
			i++;
		}
		System.out.println("IS: TUPLE COUNT: total " + total + "   total lib " + inLib);
	}
	
	
private int printRelTuples(ProgramRel r, String relDirName) {
		
		PrintWriter currPW = null;
		if (printOutputRels) {
			String fName = relDirName + "/" + r.getName() + ".txt";
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
		}
		int cnt = 0;
		
		for (int[] args : r.getAryNIntTuples()) {
			Tuple t = new Tuple(r, args);
			if (belongsToLib(t)) {
				if (printOutputRels) 
					currPW.println(t.toString());
				cnt++;
			}
		}
		if (printOutputRels)
			currPW.close();
		return cnt;
	}
	
	
private List<ProgramRel> getProducedTrgts() {
	List<ProgramRel> relList = new ArrayList<ProgramRel>();
	
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjValAsgnInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RobjVarAsgnInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetInstFldInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RputInstFldInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RgetStatFldInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("RputStatFldInst"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableT"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCI"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("reachableCM"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("CICM"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("kcfaDIC"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("DVDV"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("rootCM"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("CMCM"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("FC"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("CFC"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("CVC"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("DVC"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("DI"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("DIH"));
	relList.add((ProgramRel) ClassicProject.g().getTrgt("DIC"));	

	return relList;		
}


	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	private boolean belongsToLib(Tuple t){
		Dom[] dArr = t.getDomains();
		int[] ndx = t.getIndices();
		int type = 0;

		String r = t.getRelName();
		for (int i = 0; i < dArr.length; i++) {
			String d = dArr[i].getName();
			if (mark.containsKey(d)) {
				type |= ((int[])mark.get(dArr[i].getName()))[ndx[i]];
			}
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
		domArray.add((DomP) ClassicProject.g().getTrgt("P"));
		domArray.add((DomMS) ClassicProject.g().getTrgt("MS"));
		
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
		else if(dom.getName().equals("P")) {
			DomP domP = (DomP)dom;
			for (int i = 0; i < domP.size(); i++) {
				Quad q = (Quad) domP.get(i);
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
		else if (dom.getName().equals("MS")){
			DomMS domMS = (DomMS)dom;
			DomV domV = (DomV)ClassicProject.g().getTrgt("V");
			
			for (int i = 0; i < domMS.size(); i++) { 
				boolean valid = true;
				Set<Register> s = domMS.get(i);
				for (Register v : s) {
					jq_Class cl = domV.getMethod(v).getDeclaringClass();
					String clName = cl.toString();
					if (!clName.matches(libraryPrefix))
						valid = false;
				}
				if (valid)
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
}
