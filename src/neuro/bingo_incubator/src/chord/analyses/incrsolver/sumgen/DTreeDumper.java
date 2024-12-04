package chord.analyses.incrsolver.sumgen;

import static chord.util.ExceptionUtil.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.LookUpRule;
import chord.project.analyses.provenance.Tuple;

/**
*
* -Dchord.incrsolver.summaryDir
* 
* Dump the reverted derivation tree for the current analysis in the  
* @author Ravi
*
*/
@Chord(name = "incr-dtreedumper")
public class DTreeDumper extends JavaAnalysis {
	List<ITask> tasks;
	HashMap<String, HashMap<String, Integer>> stringToDomIdx;
	String[] configFiles;
	HashMap<String, int[]> mark = new HashMap<String, int[]>();

//	ConstraintGenerator cg;
	SimplePTHandler ptHandler;
	List<LookUpRule> rules;

	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String fundamentalTypePrefix = "(boolean|byte|char|double|float|int|long|short|void|slib).*";
	private String summaryDir;
	
	PrintWriter summWriter;
	PrintWriter frontierWriter;
	PrintWriter outSetWriter;

	public void run() {
		// Perform initial tasks
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("incr-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("VCfilter-dlog"));
		tasks.add(ClassicProject.g().getTask("retCopy-dlog"));
		tasks.add(ClassicProject.g().getTask("preload-java"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-incrinit-dlog"));
		tasks.add(ClassicProject.g().getTask("cspa-kobj-summary-dlog_XZ90_"));
		System.setProperty("chord.ctxt.kind", "co");
		String chordIncubator = System.getenv("CHORD_INCUBATOR");
		String kobjConfig = chordIncubator + File.separator + "/src/chord/analyses/derivsz/cspa-kobj-summary-dlog_XZ90_.config";
		configFiles = new String[]{ kobjConfig };

		rules = new ArrayList<LookUpRule>();
        runAllTasks();
		markDoms();
		initRules();
		ptHandler = new SimplePTHandler(getAxioms(), mark);
		ptHandler.init(rules);
//		cg = new ConstraintGenerator(ptHandler);
//		cg.update(rules);
		
		summaryDir = System.getProperty("chord.incrsolver.summaryDir", Config.outDirName);
		File sumDir = new File(summaryDir);
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("Summary dir " + summaryDir + " does not exist - unable to create it.");
		     }        
		}
		printGroundedConstraints();
	}
	
	protected void printGroundedConstraints() {
		try {
			summWriter = new PrintWriter(new File(summaryDir + File.separator + "summary_constraints"));
			frontierWriter = new PrintWriter(new File(summaryDir + File.separator + "frontier_constraints"));
			outSetWriter = new PrintWriter(new File(summaryDir + File.separator + "outset_constraints"));
			List<LookUpRule> rules = this.rules;
			for (LookUpRule rule : rules) {
				Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
				while (iter.hasNext()) {
					ConstraintItem ci = iter.next();
					StringBuilder sb = new StringBuilder();
					Tuple head = ptHandler.transform(ci.headTuple);
					if (head == null) fail("Only parameters should be reset by PTHandler");

					if (ptHandler.belongsToLib(head)) {
						StringBuilder sbSub = new StringBuilder();
						boolean isFrontier = false;
						for (Tuple sub : ci.subTuples) {
							if (ptHandler.belongsToLib(sub)) {
								Tuple t = ptHandler.transform(sub);
								if (t == null) continue;
								sbSub.append("NOT ");
								sbSub.append(sub);
								sbSub.append(", ");
							} else {
								isFrontier = true;
								break;
							}
						}
						if (!isFrontier) sb.append(sbSub.toString());
						else frontierWriter.println(head);

						sb.append(head);
						summWriter.println(sb);
					} else {
						for (Tuple sub : ci.subTuples) {
							if (ptHandler.belongsToLib(sub)) {
								Tuple t = ptHandler.transform(sub);
								if (t == null) continue;
								outSetWriter.println(t);
							}
						}
					}
				}
			}
			outSetWriter.flush();
			outSetWriter.close();
			summWriter.flush();
			summWriter.close();
			frontierWriter.flush();
			frontierWriter.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private List<Tuple> getAxioms() {
		ProgramRel reachableCM = (ProgramRel) ClassicProject.g().getTrgt("reachableCM");
		ProgramRel rootCM = (ProgramRel) ClassicProject.g().getTrgt("rootCM");
		int[] a1 = { 0, 0 };
		Tuple t1 = new Tuple(reachableCM, a1);
		int[] a2 = { 0, 0 };
		Tuple t2 = new Tuple(rootCM, a2);
		List<Tuple> axioms = new ArrayList<Tuple>();
		axioms.add(t1);
		axioms.add(t2);
		return axioms;
	}
	
	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	private void initRules() {
		for (String conFile : configFiles) {
			try {
				Scanner sc = new Scanner(new File(conFile));
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (!line.equals("")) {
						LookUpRule rule = new LookUpRule(line);
						rules.add(rule);
					}
				}
				sc.close();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
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
}
