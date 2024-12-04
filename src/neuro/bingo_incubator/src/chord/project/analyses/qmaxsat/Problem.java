package chord.project.analyses.qmaxsat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Problem {
	private Map<Object, Integer> varMap;
	private List<Object> revVarMap;
	private Map<Object, String> metaInfo;
	public static int hardWeight = Integer.MAX_VALUE;
	private Set<Clause> clauses;
	private String desc = "MaxSAT problem";
	
	public Problem(String desc){
		this();
		this.desc = desc;
	}
	
	public Problem(){
		varMap = new HashMap<Object, Integer>();
		revVarMap = new ArrayList<Object>();
		revVarMap.add(null);
		metaInfo = new HashMap<Object, String>();
		clauses = new HashSet<Clause>();
	}
	
	public int registerVar(Object o, String mi){
		if(!varMap.containsKey(o)){
			varMap.put(o, revVarMap.size());
			revVarMap.add(o);
			metaInfo.put(o, mi);
		}
		return varMap.get(o);
	}
	
	public void registerConstraint(int w, List<Object> posLit, List<Object> negLit){
		Clause nc = new Clause();
		nc.weight = w;
		nc.posLiterals = new ArrayList<Integer>(posLit.size());
		for(Object po : posLit){
			Integer var = varMap.get(po);
			if(var == null)
				throw new RuntimeException("Unregistered object: "+po+".");
			nc.posLiterals.add(var);
		}
		nc.negLiterals = new ArrayList<Integer>(negLit.size());
		for(Object no : negLit){
			Integer var = varMap.get(no);
			if(var == null)
				throw new RuntimeException("Unregistered object: "+no+".");
			nc.negLiterals.add(var);
		}
		if(!clauses.add(nc)){
			System.err.println("Warning: ignore redundant constraint "+nc+".");
		}
	}
	
	public void storeAsDimacs(String path){
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			pw.println("c "+desc);
			int sumOfWeights = this.sumOfSoftWeight();
			hardWeight = sumOfWeights+1;
			pw.println("p wcnf "+(this.revVarMap.size()-1)+" "+this.clauses.size()+" "+hardWeight);
			for(Clause c : clauses)
				pw.println(c.toDimacsString());
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void storeVarDes(String path){
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			for(int i = 1; i < revVarMap.size(); i++){
				pw.println(i+": "+metaInfo.get(revVarMap.get(i)));
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}	
	}
	
	public int sumOfSoftWeight(){
		int ret = 0;
		for(Clause c : clauses)
			if(!c.isHardClause())
				ret += c.weight;
		return ret;
	}
}
