package chord.analyses.ursa.classifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.mln.ConstraintItem;
import chord.analyses.mln.LookUpRule;
import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * -Dchord.ursa.classifier.savepath=<String> 
 * -Dchord.ursa.relevantSample=<False>
 * @author Xin
 */
public abstract class ClassifierTrainer extends JavaAnalysis {
	private String classifierPath;
	private boolean relevantSampling;

	protected List<ITask> tasks;

	protected abstract Set<String> getDerivedRelations();

	protected abstract Set<String> getDomains();

	protected abstract Set<String> getInputRelations();

	protected abstract String getQueryRelation();

	protected abstract String[] getConfigFiles();

	protected abstract void genTasks();
	
	protected abstract void trainAndSaveClassifier(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap,
			Set<ConstraintItem> provenance, Set<Tuple> relevantTuples, String classifierPath2);
	
	protected void throwUnknownClientException() {
		throw new RuntimeException("Unknown query relation");
	}

	protected abstract void runOracle();

	protected Set<Tuple> loadTuples(boolean useDerivedRels) {
		Set<Tuple> ret = new HashSet<Tuple>();
		Set<String> rels = useDerivedRels ? this.getDerivedRelations() : this.getInputRelations();
		for (String s : rels) {
			ProgramRel r = (ProgramRel) ClassicProject.g().getTrgt(s);
			r.load();
			for (int[] vals : r.getAryNIntTuples()) {
				Tuple temp = new Tuple(r, vals);
				ret.add(temp);
			}
		}
		return ret;
	}

	/**
	 * Load tuples from executed analyses. Note any tuple containing elements in
	 * DomC is projected.
	 * 
	 * @return
	 */
	protected Set<Tuple> loadProjectedTuples(boolean useDerivedRels) {
		Set<Tuple> ret = new HashSet<Tuple>();
		Set<String> rels = useDerivedRels ? this.getDerivedRelations() : this.getInputRelations();
		for (String s : rels) {
			ProgramRel r = (ProgramRel) ClassicProject.g().getTrgt(s);
			r.load();
			for (int[] vals : r.getAryNIntTuples()) {
				Tuple temp = new Tuple(r, vals);
				for (Tuple t : this.project(temp))
					ret.add(t);
			}
		}
		return ret;
	}

	protected abstract void runBaseCase();

	protected abstract Set<Tuple> project(Tuple t);

	protected Set<Tuple> getQueryTuples() {
		Set<Tuple> ret = new HashSet<Tuple>();
		String qs = this.getQueryRelation();
		ProgramRel qrel = (ProgramRel) ClassicProject.g().getTrgt(qs);
		qrel.load();
		Dom doms[] = qrel.getDoms();
		if (doms.length == 2) {
			// take the cross product of domains
			for (int i = 0; i < doms[0].size(); i++)
				for (int j = 0; j < doms[1].size(); j++) {
					int tempIdx[] = new int[2];
					tempIdx[0] = i;
					tempIdx[1] = j;
					ret.add(new Tuple(qrel, tempIdx));
				}
			return ret;
		} else if (doms.length == 1) {
			// take the cross product of domains
			for (int i = 0; i < doms[0].size(); i++) {
				int tempIdx[] = new int[1];
				tempIdx[0] = i;
				ret.add(new Tuple(qrel, tempIdx));
			}
			return ret;
		} else
			this.throwUnknownClientException();
		return null;
	}

	protected int getQueryTupleNum() {
		String qs = this.getQueryRelation();
		ProgramRel qrel = (ProgramRel) ClassicProject.g().getTrgt(qs);
		qrel.load();
		Dom doms[] = qrel.getDoms();
		if (doms.length == 2) {
			// take the cross product of domains

			return doms[0].size() * doms[1].size();
		} else if (doms.length == 1) {
			// take the cross product of domains
			return doms[0].size();
		} else
			this.throwUnknownClientException();
		return -1;
	}

	protected abstract List<Tuple> getAxiomTuples();

	/*
	 * Get all the tuples involved in the derivation of given query tuple
	 * queryT.
	 */
	protected Set<Tuple> getProvenance(Tuple queryT) {
		List<LookUpRule> rules = this.getRules();
		Set<Tuple> provTuples = new HashSet<Tuple>();
		Set<ConstraintItem> constraints = new HashSet<ConstraintItem>();
		constraints = lookup(queryT, provTuples, rules);
		Set<ConstraintItem> currentConstraints = new HashSet<ConstraintItem>(constraints);
		Set<ConstraintItem> consToAdd = new HashSet<ConstraintItem>();
		boolean changed = false;
		do {
			changed = false;
			consToAdd.clear();
			for (ConstraintItem ci : currentConstraints) {
				for (Tuple t : ci.getSubTuples()) {
					Set<ConstraintItem> subCons = lookup(t, provTuples, rules);
					if (!subCons.isEmpty()) {
						changed = true;
						consToAdd.addAll(subCons);
					}
				}
			}
			currentConstraints.clear();
			currentConstraints.addAll(consToAdd);
			// currentConstraints.removeAll(constraints);
			constraints.addAll(consToAdd);
		} while (changed);
		return provTuples;
	}

	protected Set<ConstraintItem> lookup(Tuple t, Set<Tuple> provTuples, List<LookUpRule> rules) {
		Set<ConstraintItem> ret = new HashSet<ConstraintItem>();
		if (provTuples.contains(t))// Some optimization, we don't want to query
									// the tuple again
			return ret;
		else
			provTuples.add(t);
		for (LookUpRule rule : rules) {
			if (rule.match(t)) {
				List<ConstraintItem> items = rule.lookUp(t);
				if (items != null)
					ret.addAll(items);
			}
		}
		return ret;
	}

	protected List<LookUpRule> getRules() {
		List<LookUpRule> rules = new ArrayList<LookUpRule>();
		rules = new ArrayList<LookUpRule>();
		for (String conFile : this.getConfigFiles()) {
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
		return rules;
	}

	protected Set<Tuple> generateFinalQueries(String queryFile) {
		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			PrintWriter pw = new PrintWriter(new File(queryFile));
			String queryRel = getQueryRelation();
			ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(queryRel);
			pr.load();
			for (int[] indices : pr.getAryNIntTuples()) {
				Tuple t = new Tuple(pr, indices);
				pw.println(t);
				queries.add(t);
			}
			pw.flush();
			pw.close();
			return queries;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected Set<Tuple> readOracleQueries(String oraclePath) {
		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(oraclePath)));
			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().equals("")) {
					continue;
				}
				if (line.startsWith("//")) {
					continue;
				}
				if (line.startsWith("!")) {
					br.close();
					throw new RuntimeException("Result produced by the MLN engine should not contain negated tuples");
				} else {
					Tuple current = new Tuple(line);
					queries.add(current);
				}
			}
			br.close();
			return queries;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void readSettings() {
		this.classifierPath = System.getProperty("chord.ursa.classifier.savepath");
		this.relevantSampling = Boolean.getBoolean("chord.ursa.relevantSample");
	}

	@Override
	public void run() {
		ProgramRel relMV = (ProgramRel) ClassicProject.g().getTrgt("MV");
		ClassicProject.g().runTask(relMV);
		relMV.load();
		Tuple.relMV = relMV;

		this.readSettings();
		this.genTasks();
		this.runBaseCase();
		Set<Tuple> baseTuplesIDB = this.loadTuples(true);
		Set<Tuple> baseTuplesEDB = this.loadTuples(false);
		Set<ConstraintItem> provenance = new HashSet<ConstraintItem>();
		List<LookUpRule> rules = this.getRules();
		for(LookUpRule r : rules){
			Iterator<ConstraintItem> iter = r.getAllConstrIterator();
			while(iter.hasNext()){
				provenance.add(iter.next());
			}
		}

		this.runOracle();
		Set<Tuple> oracleTuplesIDB = this.loadProjectedTuples(true);
		Set<Tuple> oracleTuplesEDB = this.loadProjectedTuples(false);
		
		Map<Tuple,Boolean> edbLabelMap = new HashMap<Tuple,Boolean>();
		Map<Tuple,Boolean> idbLabelMap = new HashMap<Tuple,Boolean>();
		
		for(Tuple t : baseTuplesEDB)
			if(oracleTuplesEDB.contains(t))
				edbLabelMap.put(t, true);
			else{
				edbLabelMap.put(t, false);
			}
		
		for(Tuple t : baseTuplesIDB)
			if(oracleTuplesIDB.contains(t))
				idbLabelMap.put(t, true);
			else
				idbLabelMap.put(t, false);
		Set<Tuple> relevantTuples = new HashSet<Tuple>();
		if(this.relevantSampling){
			Map<Tuple, Set<Tuple>> headToBody = new HashMap<Tuple,Set<Tuple>>();
			for(ConstraintItem ci : provenance){
				Tuple h = ci.getHeadTuple();
				Set<Tuple> ebs = headToBody.get(ci);
				if(ebs == null){
					ebs = new HashSet<Tuple>();
					headToBody.put(h, ebs);
				}
				ebs.addAll(ci.getSubTuples());
			}
			Set<Tuple> queries = this.getQueryTuples();
			relevantTuples.addAll(queries);
			Queue<Tuple> workList = new LinkedList<Tuple>();
			workList.addAll(queries);
			while(!workList.isEmpty()){
				Tuple t = workList.remove();
				if (headToBody.containsKey(t))
					for (Tuple t1 : headToBody.get(t))
						if (relevantTuples.add(t1))
							workList.add(t1);
			}
		}else{
			relevantTuples.addAll(edbLabelMap.keySet());
			relevantTuples.addAll(idbLabelMap.keySet());
		}
		
		trainAndSaveClassifier(edbLabelMap, idbLabelMap, provenance, relevantTuples, this.classifierPath);
	}

}
