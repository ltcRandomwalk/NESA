package chord.analyses.provenance.kobj.incremental;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import static chord.util.ExceptionUtil.fail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import chord.project.Config;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.LookUpRule;
import chord.project.analyses.provenance.Model;
import chord.project.analyses.provenance.ParamTupleConsHandler;
import chord.project.analyses.provenance.Tuple;
import chord.util.ProcessExecutor;
//import chord.util.Timer;
import chord.util.tuple.object.Pair;

/**
 * Generate the constraint file within one iteration. TODO: implement the method
 * to merge constraints across iterations together
 * 
 * @author xin
 * 
 */
public class MaxSatGenerator extends chord.project.analyses.provenance.MaxSatGenerator {
	private String configFiles[];
	private String queryR;
	private List<LookUpRule> rules;
	public static boolean DEBUG = false;
	int iterCount = 0;
	// The way to get the correct constaintItem is consPool.get(idx)
//	private TObjectIntMap<ConstraintItem> consDic;
//	private ArrayList<ConstraintItem> consPool;
	private TObjectIntMap<Tuple> tupleDic;
//	private Set<ConstraintItem> consPool;
	private ArrayList<Tuple> tuplePool;
	private ParamTupleConsHandler paramHandler;
	private Map<Tuple, Set<Integer>> consMap;
	private boolean tuplePoolChanged = false;
	private int queryWeight;
	private String solverPath;
	
	private Set<FormatedConstraint> consCache;
	private Set<Tuple> queryCache;
	private int qw = -1;
	
	
	
	private static int iter = 0;
	private static int emptyCt = 0;
	private static int deltaHardCount = 0;
	private static int deltaSoftCount = 0;
	private Set<FormatedConstraint> prevQuery;
	private List<FormatedConstraint> queryInOrder;

	
	// model: It points to a model that will put a bias in our MaxSat encoding.
	// Intuitively, this model object identifies derived tuples that are likely to hold.
	// This information is used to bias a solution that our MaxSat solver will find.
	private Model model; 
	
	public final static int QUERY_HARD = -1;
	public final static int QUERY_MAX = 0;

	public MaxSatGenerator(String configFiles[], String queryR, ParamTupleConsHandler paramHandler, Model model, int queryWeight) {
		super(configFiles, queryR, paramHandler, model, queryWeight);
		this.queryR = queryR;
		this.configFiles = configFiles;
		tupleDic = new TObjectIntHashMap<Tuple>();
//		consDic = new TObjectIntHashMap<ConstraintItem>();
		consMap = new HashMap<Tuple, Set<Integer>>();
//		consPool = new ArrayList<ConstraintItem>();
//		consPool = new HashSet<ConstraintItem>();
		tuplePool = new ArrayList<Tuple>();
//		consPool.add(null);// add null to make the index aligned with the Dic
		tuplePool.add(null);// add null to make the index aligned with the Dic
		this.paramHandler = paramHandler;
		this.model = model;
		this.queryWeight = queryWeight;
		
		queryInOrder = new LinkedList<FormatedConstraint>();
		prevQuery = new HashSet<FormatedConstraint>();

		//String mifuFileName = System.getProperty("chord.provenance.mifu", "mifumax");
		//this.solverPath = System.getenv("CHORD_MAIN") + File.separator + "src" + File.separator +
		//		"chord" + File.separator + "project" + File.separator + "analyses" + File.separator +
		//		"provenance" + File.separator + mifuFileName;

		this.solverPath = System.getProperty("maxsat_path");
		File f = new File(this.solverPath);
		if(! f.exists() ) { 
			throw new RuntimeException(this.solverPath + " seems not existed!");
		}
		
		this.consCache = new HashSet<FormatedConstraint>();
		this.queryCache = new HashSet<Tuple>();
	}
	
	private void initRules() {
		if (rules == null) {
			rules = new ArrayList<LookUpRule>();
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
				} catch (FileNotFoundException e) {
					throw new RuntimeException(e);
				}
			}
			paramHandler.init(rules);
		}
	}

	public void setParamHandler(ParamTupleConsHandler softWeight) {
		this.paramHandler = softWeight;
	}

	public void update(Set<Tuple> queryTs) {
		initRules();
		for (LookUpRule r : rules) {
			r.update();
//			Iterator<ConstraintItem> iter = r.getAllConstrIterator();
//			while (iter.hasNext()) {
//				ConstraintItem it = iter.next();
//				int consIndex = getOrAddConstraintIndex(it);
//					for (Tuple t : queryTs) {
//						Set<Integer> consSet = consMap.get(t);
//						if (consSet == null) {
//							consSet = new HashSet<Integer>();
//							consMap.put(t, consSet);
//						}
//						consSet.add(consIndex);
//					}
//				consPool.add(it);
//			}
		}
	}
	
	public Set<Tuple> solve(Set<Tuple> tSet, String dFPost) {
		
		String tm = System.getProperty("maxsat_timeout", "3666");
		long max_to = Long.parseLong(tm);
		
		if(max_to > 0){
			final Set<Tuple> tmp_tSet = tSet;
			final String tmp_dFPost = dFPost;
			Callable<Set<Tuple>> task = new Callable<Set<Tuple>>() {
			   public Set<Tuple> call() {
			      return solve_internal(tmp_tSet, tmp_dFPost);
			   }
			};
			ExecutorService executor =
					Executors.newSingleThreadExecutor();
			Future<Set<Tuple>> future = executor.submit(task);
			try {
			   return future.get(max_to, TimeUnit.SECONDS); 
			} catch (TimeoutException ex) {
				throw new RuntimeException("MAXSAT Solver "+this+" time out!");
			} catch (InterruptedException e) {
				throw new RuntimeException("MAXSAT Solver "+this+" interrupted!");
			} catch (ExecutionException e) {
				throw new RuntimeException("MAXSAT Solver +"+this+" execution errors!");
			} 
		}else{
			return this.solve_internal(tSet, dFPost);
		}
	}

	public Set<Tuple> solve_internal(Set<Tuple> tSet, String dFPost) {
		if(DEBUG){
			iterCount++;
		}
		File consFile = new File(Config.outDirName + File.separator + "all.maxsat"+(DEBUG?dFPost:""));
//		File expConsFile = new File(Config.outDirName + File.separator + "all.explicit"+(DEBUG?dFPost:""));
//		File paramFiles = new File(Config.outDirName+File.separator+"all.params"+(DEBUG?dFPost:""));
		try {
//			Set<Integer> allCons = new HashSet<Integer>();
//			for (Tuple t1 : tSet) {
//				Set<Integer> tCons = consMap.get(t1);
//				allCons.addAll(tCons);
//			}
//			int hardConsNum = allCons.size();
//			int hardConsNum = consPool.size();
//			int hardConsNum = 0;
			Set<Tuple> paramSet = new HashSet<Tuple>();//parameterized tuples
//			Set<Tuple> tupleSet = new HashSet<Tuple>();//all the tuples
//			for (ConstraintItem it : consPool) {
//				ConstraintItem it = consPool.get(i);
			for(LookUpRule r : rules){
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while (iter.hasNext()) {
					ConstraintItem it = iter.next();
//					Pair<Tuple, Boolean> ht = paramHandler.transform(it.headTuple);
//					if (ht != null)
//						tupleSet.add(ht.val0);
					for (Tuple st : it.subTuples) {
						Pair<Tuple, Boolean> st1 = paramHandler.transform(st);
						if (st1 != null) {
//							tupleSet.add(st1.val0);
							if (paramHandler.isParam(st1.val0))
								paramSet.add(st1.val0);
						}
					}
//					hardConsNum++;
				}
			}
//			}
			Set<FormatedConstraint> paramCons = paramHandler.getHardCons(0, paramSet, this);// This is to specify constraints
																							// like !(k=1 and k=0)
			
//			hardConsNum += paramCons.size();
//			if(queryWeight == QUERY_HARD)
//				hardConsNum += tSet.size();// Constraints to specify the query.
//			int softConsNum = paramSet.size();
//			if(queryWeight != QUERY_HARD)
//				softConsNum += tSet.size();
//			int softSum = 0;
//			for (Tuple t : paramSet) {
//				softSum += paramHandler.getWeight(t);
//			}
			
//			int qw = 0;
			
//			if(queryWeight != QUERY_HARD) {//Whether encode query as hard constraints
//				if(queryWeight == QUERY_MAX) {
//					qw = softSum +1;
//				}				
//				else 
//					qw = queryWeight;
//				softSum += qw*tSet.size();
//			}
//			
			model.build(rules);
//			softSum += model.getTotalWeight();
//			softConsNum += model.getNumConstraints();
			
//			int top = softSum + 1;

//			if(queryWeight == QUERY_HARD)
//				qw = -1;
//			
			// First, query constraints
//			int consNumberPrinted = 0;
			
			this.queryCache.addAll(tSet);
			for(Tuple t : this.queryCache){
				int[] qCons = new int[1];
				qCons[0] = (0 - getOrAddTupleIdx(t));
				FormatedConstraint cons = new FormatedConstraint(qw,qCons);
				this.addCons(cons);
			}
			
//			for (Tuple t : tSet) {
//				pw.println(qw + " " + (0 - getOrAddTupleIdx(t)) + " 0");
//				if(DEBUG)
//				pw1.println("Query: " + t);
//				consNumberPrinted++;
//			}

			// Second, normal hard constraints
//			for (ConstraintItem it : consPool) {
//				ConstraintItem it = consPool.get(i);
			for (LookUpRule r : rules) {
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while (iter.hasNext()) {
					ConstraintItem it = iter.next();
					List<Integer> liters = new ArrayList<Integer>();
//					pw.print(top + " ");
					Pair<Tuple, Boolean> tht = paramHandler.transform(it.headTuple);
					int hidx = getOrAddTupleIdx(tht.val0);
					if (!tht.val1)
						hidx = 0 - hidx;
//					pw.print(hidx);
					liters.add(hidx);
					for (Tuple st : it.subTuples) {
						Pair<Tuple, Boolean> tst = paramHandler.transform(st);
						if (tst != null) {
							int sidx = getOrAddTupleIdx(tst.val0);
							if (!tst.val1)
								sidx = 0 - sidx;
//							pw.print(" " + (0 - sidx));
							liters.add(0-sidx);
						}
					}
					this.addCons(new FormatedConstraint(-1,this.toIntList(liters)));
				}
			}
			
			// Third, constraints among input tuples
			for (FormatedConstraint con : paramCons) {
//				con.weight = top;
				con = new FormatedConstraint(-1, con.getConstraint());
				this.addCons(con);
//				pw.println(con);
//				if (DEBUG) pw1.println("Constraint among parameters: " + con.toExplicitString(tuplePool));
//				consNumberPrinted++;
			}
			// Fourth, soft constraints from the model
			for (Pair<Tuple,Integer> pair : model.getWeightedTuples()) {
				Tuple t = pair.val0;
				int w = pair.val1;
				int lits[] = new int[1];
				lits[0] = this.getOrAddTupleIdx(t);
				this.addCons(new FormatedConstraint(w, lits));
//				pw.println(w + " " + getOrAddTupleIdx(t) + " 0");
//				if (DEBUG) pw1.println("Model tuple: " + t + " with weight " + w);
//				consNumberPrinted++;
			}
			// Final, soft constraints
			for (Tuple t : paramSet) {
				int lits[] = new int[1];
				lits[0] = getOrAddTupleIdx(t);
//				pw.println(paramHandler.getWeight(t) + " " + getOrAddTupleIdx(t) + " 0");
				this.addCons(new FormatedConstraint(paramHandler.getWeight(t), lits));
//				if (DEBUG) {
//					pw1.println("Input tuple: " + t);
//					pw2.println(getOrAddTupleIdx(t));
//				}
//				consNumberPrinted++;
			}
			
			
			
			
			
			List<FormatedConstraint> deltaCons = computeDeltaConstraints(consCache);
			System.out.println("delta size: " + deltaCons.size());
			System.out.println("#Hard: " + deltaHardCount);
			System.out.println("#Soft: " + deltaSoftCount);

			int top = this.genTop();
			
			String dir_working = System.getProperty("chord.maxsat.dir", "/tmp/maxsat/");
			// log MaxSAT instances			
			String logFile = dir_working + File.separator
					+ this.hashCode() + "_inc." + iter + ".wcnf";
			String deltaFile = dir_working + File.separator
					+ this.hashCode() + "_delta.wcnf." + iter + ".iter";

			LogInstance.logClauses(queryInOrder, logFile,top);
			LogInstance.logClauses(deltaCons, deltaFile,top);
			++iter;
			
			for(FormatedConstraint e : deltaCons) {
				prevQuery.add(e);
			}

			System.out.println("Start the solver.");	
			String maxsatTimer = "MAXSAT";
			Timer.start(maxsatTimer);

			//int top = this.genTop();
			// Start file generation
			PrintWriter pw = new PrintWriter(consFile);			
//			PrintWriter pw1 = new PrintWriter(expConsFile);
//			PrintWriter pw2 = new PrintWriter(paramFiles);
//			pw.println("c " + tSet.toString());
//			if(DEBUG)
//			pw1.println("Queries: " + tSet.toString());
			pw.print("p wcnf");
//			if(DEBUG)
//			pw1.println("=================================");
			pw.print(" " + (this.tuplePool.size()-1));
//			pw.print(" " + (hardConsNum + softConsNum));
			//pw.print(" " + this.consCache.size());
			pw.print(" " + this.queryInOrder.size());
			
			pw.println(" " + top);

			for(FormatedConstraint con : this.queryInOrder){
				if(con.weight > 0)
					pw.print(con.weight);
				else
					pw.print(top);
				for(int l : con.constraint){
					pw.print(" "+l);
				}
				pw.println(" 0");
			}

//			if (hardConsNum + softConsNum != consNumberPrinted)
//				throw new RuntimeException("Number of constraints does not match.");
			pw.flush();
			pw.close();
//			pw1.flush();
//			pw1.close();
//			pw2.flush();
//			pw2.close();
			
//			if(DEBUG && tuplePoolChanged){
//				File tupleFile = new File(Config.outDirName + File.separator + "tuple.map");
//				PrintWriter pwTemp = new PrintWriter(tupleFile);
//				for(Tuple t : tuplePool)
//					if(t!=null)
//					   pwTemp.println(t.toVerboseString());
//				pwTemp.flush();
//				pwTemp.close();
//				tuplePoolChanged = false;
//			}
			
			String cmd[] = new String[2];
			File result = new File(Config.outDirName + File.separator + "result");
//			cmd[0] = System.getenv("CHORD_INCUBATOR") + File.separator + "src" + File.separator + "chord" + File.separator + "project" + File.separator + "analyses" + File.separator + "provenance" + File.separator + "mifumax";
			cmd[0] = solverPath;
			cmd[1] = consFile.getAbsolutePath();
			// cmd[2] = "&>";
			// cmd[3] = result.getAbsolutePath();
			
			
			//Timer t = new Timer();
			//t.init();

			int status = ProcessExecutor.executeWithRedirect(cmd, result, -1);
			if (status != 0 && status != 30) {
				fail("The solver did not terminate normally. status=" + status);
			}

			//t.done();
			//System.out.println("Solver exclusive time: "+t.getExclusiveTimeStr());
		    Set<Tuple> res= interpreteResult(result,tSet);
			Timer.printElapsed(maxsatTimer);
		    
			return res;
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
	public void setQueryWeight(int w){
		this.qw = w;
	}
	
	private int genTop() {
		int ret = 0;
		for(FormatedConstraint con: this.consCache)
			if(con.weight > 0)
				ret += con.weight;
		return ret+1;
	}

	private void addCons(FormatedConstraint cons){
		this.consCache.add(cons);
	}
	
	private int[] toIntList(Collection<Integer> con){
		int[] ret = new int[con.size()];
		int idx = 0;
		for(Integer i : con){
			ret[idx] = i;
			idx++;
		}
		
		return ret;
	}

	private Set<Tuple> interpreteResult(File f, Set<Tuple> queries) {
		try {
			Scanner sc = new Scanner(f);
			Set<Tuple> ret = new HashSet<Tuple>();
			while (sc.hasNext()) {
				String line = sc.nextLine();
				if (line.startsWith("s ")) {
					if (line.startsWith("s UNSATISFIABLE"))
						return null;
					if (!line.startsWith("s OPTIMUM FOUND"))
						throw new RuntimeException("Expecting a solution but got " + line);
				}
				if (line.startsWith("v ")) {
					Scanner lineSc = new Scanner(line);
					String c = lineSc.next();
					if (!c.trim().equals("v"))
						throw new RuntimeException("Expected char of a solution line: " + c);
					while (lineSc.hasNext()) {
						int i = lineSc.nextInt();
						if (i < 0) {
							Tuple t = tuplePool.get(0 - i);
//							System.out.println("========");
//							System.out.println(t.toVerboseString());
							if (paramHandler.isParam(t))
								ret.add(t);
						}
						else{
							Tuple t = tuplePool.get(i);
							if(queries.contains(t)){
								if(queryWeight == QUERY_HARD)
									throw new RuntimeException("Check the query encoding, it is supposed to be hard constraints");
								ret.add(t);
							}
						}
					}
				}
			}
			System.out.println("Tuples to eliminate: "+ret);
			return ret;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public int getOrAddTupleIdx(Tuple t) {
		int ret = tupleDic.get(t);
		if (ret <= 0) {
			ret = tuplePool.size();
			tupleDic.put(t, ret);
			tuplePool.add(t);
			tuplePoolChanged = true;
		}
		return ret;
	}

	private List<FormatedConstraint> computeDeltaConstraints(Set<FormatedConstraint> gcs) {
		deltaHardCount = 0;
		deltaSoftCount = 0;
		
		//if( prevQuery == null || prevQuery.isEmpty()) {
		//	return gcs;
		//}
		
		List<FormatedConstraint> delta = new LinkedList<FormatedConstraint>();
		
		for(FormatedConstraint c : gcs) {
			if ( ! prevQuery.contains(c) ) { // be careful, we may need to override equal method
				delta.add(c);
				queryInOrder.add(c);
				
				if(c.weight > 0){
					++deltaSoftCount;
				}
				else{
					++deltaHardCount;
				}
			}
		}
		
		return delta;
	}

//	private int getOrAddConstraintIndex(ConstraintItem it) {
//		int ret = consDic.get(it);
//		if (ret <= 0) {
//			ret = consPool.size();
//			consDic.put(it, ret);
//			consPool.add(it);
//			Pair<Tuple,Boolean> htp = paramHandler.transform(it.headTuple);
//			getOrAddTupleIdx(htp.val0);
//			for (Tuple t : it.subTuples) {
//				Pair<Tuple,Boolean> stp = paramHandler.transform(t);
//				if (stp != null)
//					this.getOrAddTupleIdx(stp.val0);
//			}
//		}
//		return ret;
//	}

}
