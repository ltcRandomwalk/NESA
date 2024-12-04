package chord.analyses.provenance.kobj.incremental;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import static chord.util.ExceptionUtil.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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
//import chord.util.ProcessExecutor;
//import chord.util.Timer;
import chord.util.tuple.object.Pair;

/**
 * Generate the constraint file within one iteration. TODO: implement the method
 * to merge constraints across iterations together
 * 
 * @author xin
 * @author Xujie Si
 * 
 */
public class IncrementalMaxSatGenerator extends MaxSatGenerator {
	
	private Set<FormatedConstraint> prevQuery;
	private List<FormatedConstraint> queryInOrder;
	
	private PrintWriter queryPipe;
	private BufferedReader answerPipe;
	private String dir_working;
	
	private static int iter = 0;
	private static int emptyCt = 0;
	private static int deltaHardCount = 0;
	private static int deltaSoftCount = 0;
	
	
	
	
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
	private String mifuPath;
	
	private Set<FormatedConstraint> consCache;
	private Set<Tuple> queryCache;
	private int qw = -1;
	
	// model: It points to a model that will put a bias in our MaxSat encoding.
	// Intuitively, this model object identifies derived tuples that are likely to hold.
	// This information is used to bias a solution that our MaxSat solver will find.
	private Model model; 
	
	public final static int QUERY_HARD = -1;
	public final static int QUERY_MAX = 0;
	
	
	public static void createFifoPipe(String fifoPathName) {
	    Process process = null;
	    String[] command = new String[] {"mkfifo", fifoPathName};
	    try {
			process = new ProcessBuilder(command).start();
		    if(process.waitFor() != 0){
		    	System.out.println("Failed to create fifo: " + fifoPathName);
		    	throw new RuntimeException("Failed to create fifo: " + fifoPathName);
		    }
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			//System.exit(-1);
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			//System.exit(-1);
			throw new RuntimeException(e);
		}
	}
	

	
	public static void startSolverThread(String inputPipe, String outputPipe) {
		final String prog =  System.getProperty("maxsat_path");
		if(prog == null) {
			System.out.println("maxsat_path is not set!!");
			throw new RuntimeException("maxsat_path is not set!!");
		}
		
		File f = new File(prog);
		if(! f.exists() ) { 
			throw new RuntimeException(prog + " seems not existed!");
		}
		
	    final String[] command = new String[] { prog,
				inputPipe, outputPipe};

		(new Thread() {			
			public void run() {
				try {
					new ProcessBuilder(command).start();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
					throw new RuntimeException("solver process got some errors");
				}
			}
		}).start();
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


	public IncrementalMaxSatGenerator(String configFiles[], String queryR, ParamTupleConsHandler paramHandler, Model model, int queryWeight) {
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
		String mifuFileName = System.getProperty("chord.provenance.mifu", "mifumax");
		this.mifuPath = System.getenv("CHORD_MAIN") + File.separator + "src" + File.separator +
				"chord" + File.separator + "project" + File.separator + "analyses" + File.separator +
				"provenance" + File.separator + mifuFileName;

		this.consCache = new HashSet<FormatedConstraint>();
		this.queryCache = new HashSet<Tuple>();
		
		
		try{
			queryInOrder = new LinkedList<FormatedConstraint>();
			prevQuery = new HashSet<FormatedConstraint>();
			
			// create named pipe
			
			dir_working =  System.getProperty("chord.maxsat.dir", "/tmp/maxsat/");
			
			String answerPipePathName = dir_working + File.separator + "answerPipe";
			createFifoPipe(answerPipePathName);
			//System.out.println("Created name pipe: " + answerPipePathName);
			
			String queryPipePathName = dir_working + File.separator + "queryPipe";
			createFifoPipe(queryPipePathName);
			//System.out.println("Created name pipe: " + queryPipePathName);
			//System.out.flush();
			
			startSolverThread(queryPipePathName,  answerPipePathName);
			
			// the open order really matters, since it may cause deadlock if the order is different
			// from the open order of underlying incremental maxsat solver
			queryPipe =  new PrintWriter( new File(queryPipePathName) );
			//System.out.println("query Pipe is opened.");
			
			//answerPipe = new BlockedReader( new FileInputStream( answerPipeName) );
			answerPipe = new BufferedReader(new FileReader(answerPipePathName));
			//System.out.println("answer Pipe is opened.");
			
			System.out.println("Incremental Solver initialization is done.");
			
		}
		catch(IOException ex) {
			System.err.println("Got an error when open pipe");
			ex.printStackTrace();
			System.exit(-1);
		}

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
		try {
			Set<Tuple> paramSet = new HashSet<Tuple>();//parameterized tuples

			for(LookUpRule r : rules){
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while (iter.hasNext()) {
					ConstraintItem it = iter.next();
					for (Tuple st : it.subTuples) {
						Pair<Tuple, Boolean> st1 = paramHandler.transform(st);
						if (st1 != null) {
							if (paramHandler.isParam(st1.val0))
								paramSet.add(st1.val0);
						}
					}
				}
			}
			Set<FormatedConstraint> paramCons = paramHandler.getHardCons(0, paramSet, this);// This is to specify constraints
																							// like !(k=1 and k=0)
			
			model.build(rules);
			
			this.queryCache.addAll(tSet);
			for(Tuple t : this.queryCache){
				int[] qCons = new int[1];
				qCons[0] = (0 - getOrAddTupleIdx(t));
				FormatedConstraint cons = new FormatedConstraint(qw,qCons);
				this.addCons(cons);
			}
			
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
			
//			queryPipe.println("c " + tSet.toString());
//			if(DEBUG)
//			pw1.println("Queries: " + tSet.toString());
			queryPipe.print("p wcnf");
//			if(DEBUG)
//			pw1.println("=================================");
			queryPipe.print(" " + (this.tuplePool.size()-1));
//			pw.print(" " + (hardConsNum + softConsNum));
			queryPipe.print(" " + deltaCons.size());
			queryPipe.println(" " + top);

			for(FormatedConstraint con : deltaCons){
				if(con.weight > 0)
					queryPipe.print(con.weight);
				else
					queryPipe.print(top);
				for(int l : con.constraint){
					queryPipe.print(" "+l);
				}
				queryPipe.println(" 0");
			}
			
			// mark end of this phase
			queryPipe.println("e");
			
			// clear the buffer
			queryPipe.flush();


			
			//String cmd[] = new String[2];
			//File result = new File(Config.outDirName + File.separator + "result");
//			cmd[0] = System.getenv("CHORD_INCUBATOR") + File.separator + "src" + File.separator + "chord" + File.separator + "project" + File.separator + "analyses" + File.separator + "provenance" + File.separator + "mifumax";
			//cmd[0] = mifuPath;
			//cmd[1] = consFile.getAbsolutePath();
			// cmd[2] = "&>";
			// cmd[3] = result.getAbsolutePath();
			
			
			
			

			//if (ProcessExecutor.executeWithRedirect(cmd, result, -1) != 0) {
			//	fail("The solver did not terminate normally.");
			//}
			Set<Tuple> res = interpreteResult(tSet);
			
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

	private Set<Tuple> interpreteResult(Set<Tuple> queries) {
		try {
			Set<Tuple> ret = new HashSet<Tuple>();
			String line;
			while ((line = answerPipe.readLine()) != null) {
				if (line.startsWith("e") || line.startsWith("Q") ) {
					break;
				}
				
				if(line.startsWith("c ")) {
						// c  Nb (UN)SAT calls:
						// c  CS:  
						System.out.println(line.substring(3));
				}
				
				
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
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

}
