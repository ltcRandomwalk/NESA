package chord.analyses.incrsolver;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.incrsolver.Node;
import chord.bddbddb.Dom;
import chord.project.analyses.ProgramRel;
import chord.util.Timer;

import com.google.common.primitives.Ints;

import chord.project.ClassicProject;
import chord.project.analyses.provenance.Tuple;
import chord.project.analyses.ProgramRel;

public class SummaryHandler {

	private String summaryDir;
	private HashMap<String, HashMap<String, Integer>> stringToDomIdx;
	private HashMap<Tuple, Boolean> condTupleInFrontier;
	HashMap<String, ProgramRel> relNameToPgmRel;
	private Node rootNode;
	private ArrayList<String> summariesToLoad;
	private String sep = "####";
	private String sepReorder1 = "\\(";
	private String sepReorder2 = ",";
	Timer timer1;
	Timer timer2;
	Timer timer3;
	boolean reorder;
	int prevSummariesLoaded = 1;  // initialisation to reflect that unconditional summary is loaded, to go thro' the first iteration.
	String unconditionalSummaryFile;
	
	public SummaryHandler(String summaryDir, HashMap<String, HashMap<String, Integer>> stringToDomIdx) {
		this.stringToDomIdx = stringToDomIdx;
		this.summaryDir = summaryDir + "/";
		this.condTupleInFrontier = new HashMap<Tuple, Boolean>();
		this.rootNode = new Node();
		summariesToLoad = new ArrayList<String>();
		reorder = Boolean.getBoolean("chord.incrsolver.reorderDoms");
		createConditionTree();
	}
	
	public boolean areSummariesAvailable() {
		boolean summariesPres = rootNode.children.size() > 0;
		boolean changed = (prevSummariesLoaded > 0);
		boolean ret = summariesPres && changed;
		System.out.println("IS: Num root node summaries: " + rootNode.children.size() + "prev loaded " + prevSummariesLoaded + "   returning " + ret);
		return (ret);
	}
	
	
	public void loadUnconditionalSummary(HashMap<String, ProgramRel> relNameToPgmRel) {
		System.out.println("IS: LOADING SUMMARIES FROM: " + unconditionalSummaryFile);
		timer3 = new Timer("Unconditional Summary Loading");
		timer3.init();
		try {
			File f = new File(summaryDir + unconditionalSummaryFile);
			if (f.exists()) {
				Scanner sc = new Scanner(f);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					Tuple t = createTuple(line);
					if (t == null) continue;
					if (relNameToPgmRel.containsKey(t.getRelName())) {
						ProgramRel pRel = (ProgramRel)relNameToPgmRel.get(t.getRelName());
						int[] indices = t.getIndices();
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
				}
				sc.close();
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		for(String relName: relNameToPgmRel.keySet()) {
			((ProgramRel)relNameToPgmRel.get(relName)).save();
		}
		timer3.done();
		long time3 = timer3.getInclusiveTime();
		System.out.println("IS: EXECTIME: Unconditional Summary Loading: "+ Timer.getTimeStr(time3));
	}
	
	public void loadConditionalSummaries(HashMap<String, ProgramRel> relNameToPgmRel) {
		condTupleInFrontier.clear();
		this.relNameToPgmRel = relNameToPgmRel;
		timer1 = new Timer("Condition Matching");
		timer1.init();
		matchCondition(rootNode);
		collectSummariesToLoad(rootNode);
		timer1.done();
		timer2 = new Timer("Summary Loading");
		timer2.init();
		for (String summaryFile: summariesToLoad) {
			System.out.println("IS: LOADING SUMMARIES FROM: " + summaryFile);
			try {
				File f = new File(summaryDir + summaryFile);
				if (f.exists()) {
					Scanner sc = new Scanner(f);
					while (sc.hasNext()) {
						String line = sc.nextLine().trim();
						Tuple t = createTuple(line);
						if (t == null) continue;
						if (relNameToPgmRel.containsKey(t.getRelName())) {
							ProgramRel pRel = (ProgramRel)relNameToPgmRel.get(t.getRelName());
							int[] indices = t.getIndices();
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
					}
					sc.close();
				}
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		for(String relName: relNameToPgmRel.keySet()) {
			((ProgramRel)relNameToPgmRel.get(relName)).save();
		}
		prevSummariesLoaded = summariesToLoad.size();
		summariesToLoad.clear();
		timer2.done();
		long time1 = timer1.getInclusiveTime();
		long time2 = timer2.getInclusiveTime();
		System.out.println("IS: EXECTIME: Condition Matching:" + Timer.getTimeStr(time1) +" Summary Loading:"+ Timer.getTimeStr(time2));
	}
	
	private void createConditionTree() {
		HashMap<Integer, HashSet<Node>> condSizeToNodeMap;
		condSizeToNodeMap = new HashMap<Integer, HashSet<Node>>();
		List<Integer> szList = new ArrayList<Integer>();
		
		String condFileList = getListOfCondFiles();
		String[] condFiles = condFileList.split(" ");
		for (int i = 0; i < condFiles.length; i++) {
			Node condNode = createConditionNode(condFiles[i]);
			if (condNode != null) {
				if (condNode.condition.size() == 0) {
					unconditionalSummaryFile = condNode.summaryFile;
				} else {
					Integer condSz = new Integer(condNode.condition.size());
					HashSet<Node> nodeSet;
					if (condSizeToNodeMap.containsKey(condSz)) {
						nodeSet = (HashSet<Node>)condSizeToNodeMap.get(condSz);
						nodeSet.add(condNode);
					} else {
						szList.add(condNode.condition.size());
						nodeSet = new HashSet<Node>();
						nodeSet.add(condNode);
						condSizeToNodeMap.put(new Integer(condNode.condition.size()),nodeSet);
					}
				}
			}
		}
		
		int[] szArr = Ints.toArray(szList);
		Arrays.sort(szArr);
		
		for (int i = szArr.length - 1; i >= 0; i--) {
			HashSet<Node> nodeSet = (HashSet<Node>)condSizeToNodeMap.get(szArr[i]);
			for (Node n : nodeSet) {
				addNodeToTree(rootNode, n);
			}
		}
	}
	
	private void addNodeToTree(Node curr, Node toAdd) {
		if (!curr.children.isEmpty()) {
			boolean addedToSubtree = false;
			for (Node n : curr.children) {
				if (n.condition.containsAll(toAdd.condition)) {
					addNodeToTree(n, toAdd);
					addedToSubtree = true;
					break;
				}
			}
			if (! addedToSubtree) {
				curr.children.add(toAdd);
				toAdd.parent = curr;
			}
		} else {
			curr.children.add(toAdd);
			toAdd.parent = curr;
		}
	}
	
	private Node createConditionNode(String condFileName) {
		Node curr = null;
		boolean err = false;
		//System.out.println("IS: CONDITION FILE: " + condFileName);
		try {
			File f = new File(summaryDir + condFileName);
			if (f.exists()) {
				curr = new Node();
				Scanner sc = new Scanner(f);
				int lineCnt = 0;
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (lineCnt == 0) {
						curr.summaryFile = line;
					} else {
						Tuple t = createTuple(line);
						if (t == null) 
							err = true;
						else
							curr.condition.add(t);
					}
					lineCnt++;
				}
				sc.close();
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		//System.out.println ("IS: created cond node for: " + curr.summaryFile + "  " + curr.condition.toString());
		if (err)
			return null;
		else
			return curr;
	}
	
	private Tuple createTuple(String s) {
		Tuple t;
		if (reorder)
			t = createTupleFromNdx(s);
		else
			t = createTupleFromStr(s);
		return t;
	}
	
	
	private Tuple createTupleFromNdx(String s) {
		Tuple t;
		String[] tupleParts = s.split(sepReorder1);
		String ndxStr = tupleParts[1].substring(0, tupleParts[1].length() - 1);
		String[] atoms = ndxStr.split(sepReorder2);
		ProgramRel relation = (ProgramRel)ClassicProject.g().getTrgt(tupleParts[0]);
		int[] domIndices = new int[atoms.length];
		for(int i = 0; i < atoms.length; i++) {
			domIndices[i] = Integer.parseInt(atoms[i]);
		}
		t = new Tuple(relation, domIndices);
		return t;
	}
	
	
	private Tuple createTupleFromStr(String s) {
		Tuple t;
		String[] atoms = s.split(sep);
		ProgramRel relation = (ProgramRel)ClassicProject.g().getTrgt(atoms[0]);
		Dom[] domains = relation.getDoms();
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
			t = new Tuple(relation, domIndices);
		} else {
			System.out.println("IS:Unable to create tuple for: " + s + " because of atom id " + atomId);
			t = null;
		}
		return t;
	}
	
	private String getListOfCondFiles() {
		StringBuilder sb = new StringBuilder();
		String unionFile = summaryDir + "/union_condition_files";
		File unionf = new File(unionFile);
		if (unionf.exists()) {
			try {
				Scanner sc = new Scanner(unionf);
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					sb.append(line + " ");
				}
				sc.close();
				
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		} else {
			String trainingAppsList = System.getenv("INCRSOLVER_TRAINING_APPS");
	    	String apps[];
	    	if (trainingAppsList != null && !trainingAppsList.equals(""))
	    		apps = trainingAppsList.split(",");
	    	else {
	    		apps = new String[1];
	    		apps[0] = System.getProperty("chord.incrsolver.appName");
	    	}
	    	for (int j = 0; j < apps.length; j++) {
	    		String afile = summaryDir + "/" + apps[j] + "_condition_files";
	    		File appf = new File(afile);
	    		if (appf.exists()) {
	    			try {
    					Scanner sc = new Scanner(appf);
    					while (sc.hasNext()) {
    						String line = sc.nextLine().trim();
    						sb.append(line + " ");
    					}
    					sc.close();
	    			} catch (FileNotFoundException e) {
	    				throw new RuntimeException(e);
	    			}
	    		}
	    	}
		}
		return sb.toString();
	}
	
	/*****
	private void collectSummariesToLoad(Node n) {
		HashSet<Node> toRemove = new HashSet<Node>();
		for (Node child: n.children) {
			if (child.presInFrnt) {
				summariesToLoad.add(child.summaryFile);
				toRemove.add(child);
			} else if (!child.presInFrnt) {
				collectSummariesToLoad(child);
			}
		}
		n.children.removeAll(toRemove);
	}
	*****/
	
	
	private void collectSummariesToLoad(Node n) {
		HashSet<Node> toRemove = new HashSet<Node>();
		for (Node child: n.children) {
			if (child.presInFrnt) {
				summariesToLoad.add(child.summaryFile);
				toRemove.add(child);
			} 
			collectSummariesToLoad(child);
		}
		n.children.removeAll(toRemove);
	}
	
	private boolean matchCondition(Node n) {
		boolean match = true;
		//System.out.println("IS: calling matchCondition: " + " for " + n.condition.toString());
		if (!n.children.isEmpty()) {
			for (Node child: n.children) {
					boolean ret = matchCondition(child);
					match = match && ret;
			}
			if (match) {
				if (n != rootNode)
					match = match && checkFrontier(n);
			}
		} else {
			match = checkFrontier(n);
		}
		n.presInFrnt = match;
		return match;
	}
	
	private boolean checkFrontier(Node n) {
		boolean inFrontier = true;
		for (Tuple t : n.condition) {
			if (condTupleInFrontier.containsKey(t)) {
				boolean inRel = (Boolean)condTupleInFrontier.get(t);
				inFrontier = inFrontier && inRel;
			} else {
				String relName = t.getRelName();
				ProgramRel r = (ProgramRel) relNameToPgmRel.get(relName);
				int[] indices = t.getIndices();
				boolean inRel = false;
	    		switch (indices.length) {
	    		case 1:
	    			inRel = r.contains(indices[0]);
	    			inFrontier = inFrontier && inRel;
	    			break;
	    		case 2:
	    			inRel = r.contains(indices[0], indices[1]);
	    			inFrontier = inFrontier && inRel;
	    			break;
	    		case 3:
	    			inRel = r.contains(indices[0], indices[1], indices[2]);
	    			inFrontier = inFrontier && inRel;
	    			break;
	    		case 4:
	    			inRel = r.contains(indices[0], indices[1], indices[2], indices[3]);
	    			inFrontier = inFrontier && inRel;
	    			break;
	    		default:
	    			break;
	    		}
	    		condTupleInFrontier.put(t, new Boolean(inRel));
			}
			if (!inFrontier) break;
		}
		//System.out.println("IS: Returning " + inFrontier + " for frontier: " + n.condition.toString() + " with summary " + n.summaryFile);
		return inFrontier;
	}
}
