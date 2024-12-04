package chord.project.analyses.composat;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.project.Config;
import chord.util.ArraySet;
import chord.util.ProcessExecutor;
import chord.util.StringUtil;
import chord.util.Timer;

public class MinSharedVarBinaryPartitioner implements Partitioner {
	int setDiff = 1;
	String biParSolverPath;
	
	public MinSharedVarBinaryPartitioner(int setDiff){
		this.setDiff = setDiff;
		String biParSolverName = System.getProperty("chord.composat.meti", "shmetis");
		biParSolverPath = System.getenv("CHORD_INCUBATOR") + File.separator + "lib" + File.separator + biParSolverName;	
	}

	@Override
	public List<Problem> parition(Problem p) {
//		return this.ilpPartition(p);
		return this.balanceCut(p);
	}
	
	private List<Problem> balanceCut(Problem p){
		//Indexable version of constraints
		List<Constraint> consArray = new ArrayList<Constraint>(p.getConstraints());
		Map<Constraint,Integer> consMap = new HashMap<Constraint,Integer>();
		for(int i = 0 ; i < consArray.size() ; i++)
			consMap.put(consArray.get(i), i+1);
		
		Map<Integer, Set<Integer>> vcMap = new HashMap<Integer,Set<Integer>>();
		for(Constraint c : p.getConstraints()){
			int cidx = consMap.get(c);
			for(Integer v : c.getLiterals()){
				v = Math.abs(v);
				Set<Integer> cons = vcMap.get(v);
				if(cons == null){
					cons = new HashSet<Integer>();
					vcMap.put(v, cons);
				}
				cons.add(cidx);
			}
		}

		int numCons = p.getNumConstrs();

		int numVers = numCons;
		int numEdges = vcMap.size();
		try {
			File consFile = new File(Config.outDirName+File.separator+"p"+p.getId()+".gf");
			PrintWriter pw = new PrintWriter(consFile);
			pw.println(numEdges+" "+numVers);
			for(Set<Integer> edge : vcMap.values()){
				pw.println(StringUtil.join(edge, " "));
			}
			pw.flush();
			pw.close();
			String cmd[] = new String[4];
			File result = new File(Config.outDirName + File.separator + "metis_log");
			cmd[0] = this.biParSolverPath;
			cmd[1] = consFile.getAbsolutePath();
			cmd[2] = "2";
			cmd[3] = setDiff+"";
			System.out.println("Start the solver.");	

			Timer t = new Timer("METIS timer");
			t.init();
			if (ProcessExecutor.executeWithRedirect(cmd, result, -1) < 1) {
				throw new RuntimeException("The solver did not terminate normally.");
			}
			t.done();
			System.out.println("METIS solver time: "+t.getExclusiveTimeStr());
			
			File out = new File(consFile.getAbsolutePath()+".part.2");
			Problem p1 = new Problem();
			Problem p2 = new Problem();
			List<Problem> ret = new ArrayList<Problem>();
			int idx = 0;
			BufferedReader br = new BufferedReader(new FileReader(out));
			while(true){
				String line = br.readLine();
				if(line == null)
					break;
				if(line.trim().equals("1")){
					p1.addConstraint(consArray.get(idx));
				}else
					p2.addConstraint(consArray.get(idx));
				idx++;
			}
			br.close();
			ret.add(p1);
			ret.add(p2);
			return ret;
		} catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private List<Problem> balanceCutFake(Problem p){
		Map<Integer, Set<Constraint>> vcMap = new HashMap<Integer,Set<Constraint>>();
		for(Constraint c : p.getConstraints()){
			for(Integer v : c.getLiterals()){
				v = Math.abs(v);
				Set<Constraint> cons = vcMap.get(v);
				if(cons == null){
					cons = new HashSet<Constraint>();
					vcMap.put(v, cons);
				}
				cons.add(c);
			}
		}

		int numCons = p.getNumConstrs();
		//Indexable version of constraints
		List<Constraint> consArray = new ArrayList<Constraint>(p.getConstraints());
		Map<Constraint,Integer> consMap = new HashMap<Constraint,Integer>();
		for(int i = 0 ; i < consArray.size() ; i++)
			consMap.put(consArray.get(i), i+1); //shift by one

		// Count number of edges
		List<Set<Integer>> adjNodeList = new ArrayList<Set<Integer>>();
		for(int i = 0 ; i < consArray.size() ; i++){
			Set<Integer> adjSet = new ArraySet<Integer>();
			adjNodeList.add(adjSet);
		}

		for(Set<Constraint> cons : vcMap.values()){
			for(Constraint con1 : cons){
				int idx1 = consMap.get(con1);
				Set<Integer> ans = adjNodeList.get(idx1-1);
				for(Constraint cons2 : cons){
					int idx2 = consMap.get(cons2);
					if(idx1 != idx2){
						ans.add(idx2);
					}
				}
			}
		}
		
		int numEdges = 0;
		for(Set<Integer> ans : adjNodeList)
			numEdges += ans.size();
		
		numEdges /=2;
		
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName+File.separator+"p"+p.getId()+".gf"));
			pw.println(numCons+" "+numEdges);
			for(Set<Integer> ans : adjNodeList){
				pw.println(StringUtil.join(ans, " "));
			}
			pw.flush();
			pw.close();
			throw new RuntimeException("Unfinished");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private List<Problem> ilpPartition(Problem p){
		Map<Integer, Set<Constraint>> vcMap = new HashMap<Integer,Set<Constraint>>();
		for(Constraint c : p.getConstraints()){
			for(Integer v : c.getLiterals()){
				v = Math.abs(v);
				Set<Constraint> cons = vcMap.get(v);
				if(cons == null){
					cons = new HashSet<Constraint>();
					vcMap.put(v, cons);
				}
				cons.add(c);
			}
		}

		int numCons = p.getNumConstrs();
		//Indexable version of constraints
		List<Constraint> consArray = new ArrayList<Constraint>(p.getConstraints());
		Map<Constraint,Integer> consMap = new HashMap<Constraint,Integer>();
		for(int i = 0 ; i < consArray.size() ; i++)
			consMap.put(consArray.get(i), i);
		List<Integer> vars = new ArrayList<Integer>(vcMap.keySet());
		int numVars = vars.size();
		Map<Integer,Integer> varIdxMap = new HashMap<Integer,Integer>();
		for(int i = 0 ; i < vars.size() ; i++)
			varIdxMap.put(vars.get(i), i);
		Timer ilpTimer = new Timer("composat-part-ilp");
		GRBEnv env;
		try {
			env = new GRBEnv("composat_gurobi.log");
			env.set(GRB.DoubleParam.NodefileStart, SatConfig.ilpMemory);
			GRBModel model = new GRBModel(env);
			GRBVar[] cVars = model.addVars(numCons, GRB.BINARY);
			GRBVar[] a1Vars = model.addVars(numVars, GRB.BINARY);
			GRBVar[] a2Vars = model.addVars(numVars, GRB.BINARY);
			model.update();
			// objective function
			GRBLinExpr objExpr = new GRBLinExpr();
			if(a1Vars == null)
				System.out.println("ouch");
			for(GRBVar v : a1Vars)
				objExpr.addTerm(1.0, v);
			for(GRBVar v : a2Vars)
				objExpr.addTerm(-1.0, v);
			model.setObjective(objExpr, GRB.MINIMIZE);

			//Helper constraint to encode the number of shared variables between two partitions
			for(Map.Entry<Integer, Set<Constraint>> entry : vcMap.entrySet()){
				GRBLinExpr cons1 = new GRBLinExpr();
				int varIdx = varIdxMap.get(entry.getKey());
				int consNum = entry.getValue().size();
				cons1.addTerm(consNum, a1Vars[varIdx]);
				GRBLinExpr cons2 = new GRBLinExpr();
				cons2.addTerm(consNum, a2Vars[varIdx]);
				for(Constraint con : entry.getValue()){
					int conIdx = consMap.get(con);
					cons1.addTerm(1, cVars[conIdx]);
					cons2.addTerm(1, cVars[conIdx]);
				}
				model.addConstr(cons1, GRB.GREATER_EQUAL, consNum, "vp"+entry.getKey());
				model.addConstr(cons2, GRB.LESS_EQUAL, consNum, "vn"+entry.getKey());
			}

			//Constraint to minimize the size diff
			GRBLinExpr partSizeCons = new GRBLinExpr();
			for(GRBVar cv : cVars){
				partSizeCons.addTerm(1, cv);
			}
			int minSizeDiff = (int)(setDiff/100.0 * numCons);
			if(minSizeDiff == 0)
				minSizeDiff = 1;
			model.addConstr(partSizeCons, GRB.LESS_EQUAL, numCons/2+minSizeDiff, "ub");
			model.addConstr(partSizeCons, GRB.GREATER_EQUAL, numCons/2-minSizeDiff, "lb");	
			ilpTimer.init();
			model.optimize();
			ilpTimer.done();
			System.out.println("ILP time: "+ilpTimer.getExclusiveTimeStr());
			if (model.get(GRB.IntAttr.Status) != GRB.Status.OPTIMAL) {
				throw new RuntimeException("ILP solver failed!");
			}
			double solution[] = model.get(GRB.DoubleAttr.X, cVars);
			Problem p1 = new Problem();
			Problem p2 = new Problem();
			for (int i = 0; i < solution.length; i++) {
				if (solution[i] > 0.5) {// Gurobi can return value like
					// 0.99999999
					p1.addConstraint(consArray.get(i));
				}else{
					p2.addConstraint(consArray.get(i));
				}
			}
			List<Problem> ret = new ArrayList<Problem>();
			ret.add(p1);
			ret.add(p2);
			double obj = model.get(GRB.DoubleAttr.ObjVal);
//			model.write(Config.outDirName+File.separator+p.getId()+".lp");
			System.out.println("The number of shared vars: "+obj);
			return ret;
		} catch (GRBException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}	
	}
}
