package chord.analyses.ursa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * 
 * -Dchord.ursa.classifierFile=<external classifier file path>
 * -Dchord.ursa.labelFile=<null>
 * 
 */
public abstract class URSAAnalysisDriver extends JavaAnalysis {
										// on the query weights
	protected List<ITask> tasks;
	private String classifierPath = null;
	private String labelFile = null;
	
	protected abstract Set<String> getDerivedRelations();

	protected abstract Set<String> getDomains();

	protected abstract Set<String> getInputRelations();

	protected abstract String getQueryRelation();

	protected abstract String[] getConfigFiles();

	protected abstract void genTasks();

	protected void throwUnknownClientException() {
		throw new RuntimeException("Unknown query relation");
	}

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

	protected abstract void runBaseCase();

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

	/**
	 * Generate edb file for MLN
	 * 
	 * @param evidenceFile
	 */
	protected void generateProblem(String evidenceFile) {
		try {
			PrintWriter pw = new PrintWriter(new File(evidenceFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Input tuples: ");
			for (String irs : this.getInputRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					pw.println(t);
				}
			}
			pw.println("// Domains: ");
			for (String irs : this.getDomains()) {
				ProgramDom pr = (ProgramDom) ClassicProject.g().getTrgt(irs);
				for (Object o : pr) {
					String str = "dom_" + irs + "(" + pr.indexOf(o) + ")";
					pw.println(str);
				}
			}

			pw.println();
			pw.println("// Query tuples to enforce least fixpoint: ");
			pw.println("// Total query number: " + qtsSize);
			// for(Tuple t : qTs)
			// pw.println(-1+" "+t);
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Generate scope file for MLN
	 * 
	 * @param scopeFile
	 */
	protected void generateScope(String scopeFile) {
		try {
			// if (areCurrentRelsOracle) throw new RuntimeException("Run base
			// analysis first");

			PrintWriter pw = new PrintWriter(new File(scopeFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Derived tuples: ");
			for (String irs : this.getDerivedRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t);
				}
			}
			pw.println();
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Generate verbose version of scope file for MLN
	 * 
	 * @param scopeFile
	 */
	protected void generateScopeVerbose(String scopeFile) {
		try {
			// if (areCurrentRelsOracle) throw new RuntimeException("Run base
			// analysis first");

			PrintWriter pw = new PrintWriter(new File(scopeFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Input tuples: ");
			for (String irs : this.getInputRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t.toVerboseString());
				}
			}

			pw.println("// Derived tuples: ");
			for (String irs : this.getDerivedRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t.toVerboseString());
				}
			}
			pw.println();
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected abstract List<Tuple> getAxiomTuples();

	protected void generateGroundedConstraints(String constraintFile) {
		try {
			PrintWriter pw = new PrintWriter(new File(constraintFile));
			List<LookUpRule> rules = this.getRules();
			for (LookUpRule rule : rules) {
				Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
				while (iter.hasNext()) {
					this.printConstraint(pw, iter.next());
				}
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void printConstraint(PrintWriter pw, ConstraintItem ci) {
		StringBuilder sb = new StringBuilder();
		sb.append(1+ ": ");

		// Order in which predicates appear in the ground clause should be same
		// as the order
		// in which they appear in the corresponding MLN rule in the mln file.
		// This is because
		// the MLN solver expects the predicates in the ground clauses to appear
		// in the same order.
		for (int i = 0; i < ci.getSubTuples().size(); ++i) {
			Tuple sub = ci.getSubTuples().get(i);
			Boolean sign = ci.getSubTuplesSign().get(i);
			if (sign) {
				sb.append("NOT ");
			}
			sb.append(sub.toString());
			sb.append(", ");
		}

		Tuple head = ci.getHeadTuple();
		Boolean headSign = ci.getHeadTupleSign();
		if (!headSign) {
			sb.append("NOT ");
		}
		sb.append(head.toString());

		pw.println(sb);
	}

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
		this.classifierPath = System.getProperty("chord.ursa.classifierFile");
		this.labelFile = System.getProperty("chord.ursa.labelFile");
	}
	
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath){}
	
	protected void generateAppScope(String fileName){}

	@Override
	public void run() {
		ProgramRel relMV = (ProgramRel) ClassicProject.g().getTrgt("MV");
		ClassicProject.g().runTask(relMV);
		relMV.load();
		Tuple.relMV = relMV;
		
		this.readSettings();
		this.genTasks();
		this.runBaseCase();

		Set<ConstraintItem> provenance = new HashSet<ConstraintItem>();
		List<LookUpRule> rules = this.getRules();
		for (LookUpRule r : rules) {
			Iterator<ConstraintItem> iter = r.getAllConstrIterator();
			while (iter.hasNext()) {
				provenance.add(iter.next());
			}
		}
		Set<Tuple> tuples = new HashSet<Tuple>();
		tuples.addAll(this.loadTuples(false));
		tuples.addAll(this.loadTuples(true));
		this.predict(tuples, provenance, this.classifierPath);
		
		Set<Tuple> base_queries = this.generateFinalQueries(Config.outDirName + File.separator + "base_queries.txt");
		
		Set<Tuple> falseTuples = this.loadLabels();

		this.generateProblem(Config.outDirName + File.separator + "base_problem.edb");
		this.generateScope(Config.outDirName + File.separator + "base_scope.edb");
		this.generateAppScope(Config.outDirName + File.separator + "app_base_scope.edb");
//		this.generateScopeVerbose(Config.outDirName + File.separator + "base_scope_verbose.edb");
		this.generateGroundedConstraints(Config.outDirName + File.separator + "cons_all.txt");
		Set<Tuple> derivedTuples = this.loadTuples(true);
		Set<Tuple> inputTuples = this.loadTuples(false);
		this.generateFeedback(Config.outDirName + File.separator + "label_derived.edb", derivedTuples, falseTuples);
		this.generateFeedback(Config.outDirName + File.separator + "label_input.edb", inputTuples, falseTuples);
	}

	private Set<Tuple> loadLabels() {
		Set<Tuple> ret = new HashSet<Tuple>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(this.labelFile));
			String line;
			while((line = br.readLine())!=null){
				if(line.startsWith("//"))
					continue;
				if(line.startsWith("!")){//a tuple that is spurious
					line = line.substring(1);
					String tokens[] = line.split("\\(");
					String relName = tokens[0];
					ProgramRel rel = (ProgramRel)ClassicProject.g().getTrgt(relName);
					Dom doms [] = rel.getDoms();
					int indices[] = new int[doms.length];
					for(int i = 0 ; i < doms.length; i++){
						line = br.readLine().trim();
						for(int j = 0 ; j < doms[i].size(); j++){
							String dstr = doms[i].toUniqueString(j).trim();
							if(line.equals(dstr)){
								indices[i] = j;
								break;
							}
						}
					}
					ret.add(new Tuple(rel,indices));
				}
			}
			br.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return ret;
	}

	protected void generateFeedback(String feedbackFile, Set<Tuple> baseTuples, Set<Tuple> falseTuples) {
		try {
			PrintWriter pw = new PrintWriter(new File(feedbackFile));
			pw.println("// Feedback");
			for (Tuple t : baseTuples) {
				boolean ifSurvive = !falseTuples.contains(t);
				if (ifSurvive)
					pw.println(t);
				else
					pw.println("!" + t);
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
