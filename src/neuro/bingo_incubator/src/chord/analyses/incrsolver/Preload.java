package chord.analyses.incrsolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.argret.DomZ;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;


@Chord(name = "preload-java")
public class Preload extends JavaAnalysis {
	String summaryDir;
	HashMap<String, HashMap<String, Integer>> stringToDomIdx;
	ArrayList<String> ctxtPreload;
	ArrayList<String> libAxiomPreload;
	private String sepNoReorder = "####";
	private String sepReorder1 = "\\(";
	private String sepReorder2 = ",";
	boolean reorder = false;

	public void run() {
		summaryDir = System.getProperty("chord.incrsolver.summaryDir", "");
		reorder = Boolean.getBoolean("chord.incrsolver.reorderDoms");
		
		if (!summaryDir.equals("")) {
			String dirName = summaryDir + "/preload";
			System.out.println("PRELOAD: dirname " + dirName);
			File dir = new File(dirName);
			if (dir.exists()) {
				stringToDomIdx = new HashMap<String, HashMap<String, Integer>>();
				libAxiomPreload = new ArrayList<String>();
				if (!reorder)
					createDomainMapForLibAxioms();
				getLibAxiomPreload();
				String trainingAppsList = System.getenv("INCRSOLVER_TRAINING_APPS");
				String testApp = System.getenv("INCRSOLVER_TEST_APP");
		    	String appsList = trainingAppsList + "," + testApp;
		    	String[] apps = appsList.split(",");
		    	String selfAppName = System.getProperty("chord.incrsolver.appName");
		    	for (int j = 0; j < apps.length; j++) {
		    		if (apps[j].equals(selfAppName)) continue;
					for (String relName : libAxiomPreload) {
						String fName = dirName + "/" + apps[j] + "_" + relName + ".txt";
						loadRelation(fName, relName);
					}
		    	}
			}
		}
	}
	
	
	private void getLibAxiomPreload() {
		libAxiomPreload.add("HT");
		libAxiomPreload.add("cha");
		libAxiomPreload.add("sub");
		libAxiomPreload.add("MI");
		libAxiomPreload.add("statIM");
		libAxiomPreload.add("specIM");
		libAxiomPreload.add("virtIM");
		libAxiomPreload.add("MobjValAsgnInst");
		libAxiomPreload.add("MobjVarAsgnInst");
		libAxiomPreload.add("MgetInstFldInst");
		libAxiomPreload.add("MputInstFldInst");
		libAxiomPreload.add("MgetStatFldInst");
		libAxiomPreload.add("MputStatFldInst");
		libAxiomPreload.add("clsForNameIT");
		libAxiomPreload.add("objNewInstIH");
		libAxiomPreload.add("objNewInstIM");
		libAxiomPreload.add("conNewInstIH");
		libAxiomPreload.add("conNewInstIM");
		libAxiomPreload.add("aryNewInstIH");
		libAxiomPreload.add("classT");
		libAxiomPreload.add("staticTM");
		libAxiomPreload.add("staticTF");
		libAxiomPreload.add("clinitTM");
		libAxiomPreload.add("MmethArg");
		libAxiomPreload.add("MspcMethArg");
		libAxiomPreload.add("IinvkArg");
		libAxiomPreload.add("IinvkArg0");
		libAxiomPreload.add("IinvkRet");
		libAxiomPreload.add("argCopy");
		libAxiomPreload.add("retCopy");
		libAxiomPreload.add("VCfilter");
		libAxiomPreload.add("CC");
		libAxiomPreload.add("CH");
		libAxiomPreload.add("CI");
		libAxiomPreload.add("epsilonM");
		libAxiomPreload.add("kobjSenM");
		libAxiomPreload.add("ctxtCpyM");
	}
	
	private void loadRelation(String rFileName, String rName) {
		File f = new File(rFileName);
		if (f.exists()) {
			try {
				ProgramRel relation = (ProgramRel)ClassicProject.g().getTrgt(rName);
				relation.load();
				Dom[] domains = relation.getDoms();
				System.out.println("PRELOAD: loading rel " + rName);
				Scanner sc = new Scanner(f);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();	
					if (reorder) {
						String[] tupleParts = line.split(sepReorder1);
						String ndxStr = tupleParts[1].substring(0, tupleParts[1].length() - 1);
						String[] atoms = ndxStr.split(sepReorder2);
						int[] domIndices = new int[atoms.length];
						for(int i = 0; i < atoms.length; i++){
							domIndices[i] = Integer.parseInt(atoms[i]);
						}
						addToRel(relation, domIndices);
					} else {
						String[] atoms = line.split(sepNoReorder);
						int[] domIndices = new int[atoms.length - 1];
						boolean err = false;
						int atomId = 1;
						for(int i = 1; i < atoms.length; i++){
							String domName = domains[i - 1].getName();
							if (stringToDomIdx.containsKey(domName)) {
								HashMap<String, Integer> nameMap = (HashMap<String, Integer>)stringToDomIdx.get(domName);
								if (nameMap.containsKey(atoms[i])) {
									domIndices[i - 1] = (Integer)nameMap.get(atoms[i]);
								} else {
									err = true;
									atomId = i;
								}
							} else {
								err = true;
							}
						}
						if (!err) {
							addToRel(relation, domIndices);
						} else {
							System.out.println("Preload:Unable to add: " + line + " because of atom id " + atomId);
						}
					}
				}
				sc.close();
				relation.save();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	private void addToRel(ProgramRel pRel, int[] indices) {
		switch (indices.length) {
			case 1:
				pRel.add(indices[0]);
				break;
			case 2:
				pRel.add(indices[0], indices[1]);
				break;
			case 3:
				pRel.add(indices[0], indices[1], indices[2]);
				break;
			case 4:
				pRel.add(indices[0], indices[1], indices[2], indices[3]);
				break;
			default:
				break;
		}
	}
	
	private void createDomainMapForCtxt() {
		
		DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		HashMap<String, Integer> nameMapI = new HashMap<String, Integer>();
		for(int i = 0; i < domI.size(); i++){
			nameMapI.put(domI.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("I", nameMapI);
		
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		HashMap<String, Integer> nameMapH = new HashMap<String, Integer>();
		for(int i = 0; i < domH.size(); i++){
			nameMapH.put(domH.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("H", nameMapH);
		
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		HashMap<String, Integer> nameMapM = new HashMap<String, Integer>();
		for(int i = 0; i < domM.size(); i++){
			nameMapM.put(domM.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("M", nameMapM);
		
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		HashMap<String, Integer> nameMapV = new HashMap<String, Integer>();
		for(int i = 0; i < domV.size(); i++){
			nameMapV.put(domV.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("V", nameMapV);
		
		DomZ domZ = (DomZ) ClassicProject.g().getTrgt("Z");
		HashMap<String, Integer> nameMapZ = new HashMap<String, Integer>();
		for(int i = 0; i < domZ.size(); i++){
			nameMapZ.put(domZ.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("Z", nameMapZ);
	}
	
	private void createDomainMapForLibAxioms() {	
		
		createDomainMapForCtxt();
		
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		HashMap<String, Integer> nameMapC = new HashMap<String, Integer>();
		for(int i = 0; i < domC.size(); i++){
			nameMapC.put(domC.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("C", nameMapC);
		
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		HashMap<String, Integer> nameMapF = new HashMap<String, Integer>();
		for(int i = 0; i < domF.size(); i++){
			nameMapF.put(domF.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("F", nameMapF);
		
		DomT domT = (DomT) ClassicProject.g().getTrgt("T");
		HashMap<String, Integer> nameMapT = new HashMap<String, Integer>();
		for(int i = 0; i < domT.size(); i++){
			nameMapT.put(domT.toUniqueString(i), i);
  	    }
		stringToDomIdx.put("T", nameMapT);
	}
}
