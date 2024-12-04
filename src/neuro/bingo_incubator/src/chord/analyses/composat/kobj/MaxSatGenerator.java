package chord.analyses.composat.kobj;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.project.Config;
import chord.project.analyses.composat.BasicSolver;
import chord.project.analyses.composat.BinCompoSolver;
import chord.project.analyses.composat.Constraint;
import chord.project.analyses.composat.MinSharedVarBinaryPartitioner;
import chord.project.analyses.composat.Problem;
import chord.project.analyses.composat.ProvenanceBinaryPartitioner;
import chord.project.analyses.composat.SatConfig;
import chord.project.analyses.composat.Solver;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.LookUpRule;
import chord.project.analyses.provenance.Model;
import chord.project.analyses.provenance.ParamTupleConsHandler;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import chord.util.tuple.object.Pair;

/**
 * Generate the constraint file within one iteration. TODO: implement the method
 * to merge constraints across iterations together
 * 
 * @author xin
 * 
 */
public class MaxSatGenerator extends chord.project.analyses.provenance.MaxSatGenerator{
	private String configFiles[];
	private String queryR;
	private List<LookUpRule> rules;
	public static boolean DEBUG = false;
	int iterCount = 0;
	private TObjectIntMap<Tuple> tupleDic;
	private ArrayList<Tuple> tuplePool;
	private ParamTupleConsHandler paramHandler;
	private Map<Tuple, Set<Integer>> consMap;
	private boolean tuplePoolChanged = false;
	private int queryWeight;
	private String mifuPath;
	
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
		consMap = new HashMap<Tuple, Set<Integer>>();
		tuplePool = new ArrayList<Tuple>();
		tuplePool.add(null);// add null to make the index aligned with the Dic
		this.paramHandler = paramHandler;
		this.model = model;
		this.queryWeight = queryWeight;
		String mifuFileName = System.getProperty("chord.provenance.mifu", "mifumax");
		this.mifuPath = System.getenv("CHORD_MAIN") + File.separator + "src" + File.separator +
				"chord" + File.separator + "project" + File.separator + "analyses" + File.separator +
				"provenance" + File.separator + mifuFileName;
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
					sc.close();
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
		if(DEBUG){
			iterCount++;
		}

		try {
			Set<Tuple> paramSet = new HashSet<Tuple>();//parameterized tuples
			Set<Tuple> tupleSet = new HashSet<Tuple>();//all the tuples
			for(LookUpRule r : rules){
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while (iter.hasNext()) {
					ConstraintItem it = iter.next();
					Pair<Tuple, Boolean> ht = paramHandler.transform(it.headTuple);
					if (ht != null)
						tupleSet.add(ht.val0);
					for (Tuple st : it.subTuples) {
						Pair<Tuple, Boolean> st1 = paramHandler.transform(st);
						if (st1 != null) {
							tupleSet.add(st1.val0);
							if (paramHandler.isParam(st1.val0))
								paramSet.add(st1.val0);
						}
					}
				}
			}

			Set<FormatedConstraint> paramCons = paramHandler.getHardCons(0, paramSet, this);// This is to specify constraints
																							// like !(k=1 and k=0)
			int softSum = 0;
			for (Tuple t : paramSet) {
				softSum += paramHandler.getWeight(t);
			}
			
			int qw = 0;
			
			if(queryWeight != QUERY_HARD) {//Whether encode query as hard constraints
				if(queryWeight == QUERY_MAX) {
					qw = softSum +1;
				}				
				else 
					qw = queryWeight;
				softSum += qw*tSet.size();
			}
			
			model.build(rules);
			softSum += model.getTotalWeight();
			
			int top = -1;

			if(queryWeight == QUERY_HARD)
				qw = top;
			
			// Start problem generation	
			Problem p = new Problem();

			// First, query constraints
			for (Tuple t : tSet) {
				Set<Integer> literals = new ArraySet<Integer>();
				literals.add(0 - getOrAddTupleIdx(t));
				Constraint cons = new Constraint(qw, literals);
				p.addConstraint(cons);
			}
			
			// Second, hard constraints on provenance
			for (LookUpRule r : rules) {
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while (iter.hasNext()) {
					Set<Integer> liters = new ArraySet<Integer>();
					ConstraintItem it = iter.next();
					Pair<Tuple, Boolean> tht = paramHandler.transform(it.headTuple);
					int hidx = getOrAddTupleIdx(tht.val0);
					if (!tht.val1)
						hidx = 0 - hidx;
					liters.add(hidx);
					for (Tuple st : it.subTuples) {
						Pair<Tuple, Boolean> tst = paramHandler.transform(st);
						if (tst != null) {
							int sidx = getOrAddTupleIdx(tst.val0);
							if (!tst.val1)
								sidx = 0 - sidx;
							liters.add(0-sidx);
						}
					}
					Constraint cons = new Constraint(-1, liters);
					p.addConstraint(cons);
				}
			}		
			
			// Third, constraints among input tuples
			for (FormatedConstraint con : paramCons) {
				Set<Integer> liters = new ArraySet<Integer>();
				for(Integer i : con.getConstraint())
					liters.add(i);
				p.addConstraint(new Constraint(top,liters));
				
			}

			// Fourth, soft constraints from the model
			for (Pair<Tuple,Integer> pair : model.getWeightedTuples()) {
				Tuple t = pair.val0;
				int w = pair.val1;
				int at = getOrAddTupleIdx(t);
				Set<Integer> liters = new ArraySet<Integer>();
				liters.add(at);
				Constraint cons = new Constraint(w, liters);
				p.addConstraint(cons);
			}

			// Final, soft constraints from the parameter cost
			for (Tuple t : paramSet) {
				int w = paramHandler.getWeight(t);
				Set<Integer> liters = new ArraySet<Integer>();
				liters.add(getOrAddTupleIdx(t));
				Constraint cons = new Constraint(w, liters);
				p.addConstraint(cons);
			}

			if(SatConfig.VIS_PROBLEM){
				String outPath = Config.outDirName+File.separator+"p"+p.getId()+".dot";
				p.writeToDot(outPath);
			}
			
			if(SatConfig.SAVE_PROBLEM){
				String outPath = Config.outDirName+File.separator+"p"+p.getId()+".pl";
				p.save(outPath);			
			}
			
			Solver solver = null;
			// Random partitioner + naive CEGAR solver
//			solver = new BinCompoSolver(new RandomBinaryPartitioner(), new BasicSolver());
			
//			// Provenance partitioner + naive CEGAR solver
//			{
//				Set<Integer> seeds = new HashSet<Integer>();
//				for (Tuple t : tSet) {
//					seeds.add(getOrAddTupleIdx(t));
//				}	
//				solver = new BinCompoSolver(new ProvenanceBinaryPartitioner(seeds),new BasicSolver());
//			}
			
			// L1: Provenance partitioner + naive CEGAR solver
			// L2: MinShardVar partitioner + naive CEGAR solver
			{
				Solver l2Solver = new BinCompoSolver(new MinSharedVarBinaryPartitioner(5), new BasicSolver());
				Set<Integer> seeds = new HashSet<Integer>();
				for (Tuple t : tSet) {
					seeds.add(getOrAddTupleIdx(t));
				}	
				solver = new BinCompoSolver(new ProvenanceBinaryPartitioner(seeds),l2Solver);				
			}

			Set<Integer> varTrue = solver.solve(p,1);
			Set<Tuple> ret = new HashSet<Tuple>();
			
			for(int i = 1; i < this.tuplePool.size(); i++){
				Tuple t = tuplePool.get(i);
				if(!varTrue.contains(i)){
					if(paramSet.contains(t))
						ret.add(t);
				}else
					if(tSet.contains(t)){
						if(queryWeight == QUERY_HARD)
							throw new RuntimeException("Check the query encoding, it is supposed to be hard constraints");
						ret.add(t);
					}

			}
			return ret;
		} catch (Throwable e) {
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
