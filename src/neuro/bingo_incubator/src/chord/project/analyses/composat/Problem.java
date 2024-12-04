package chord.project.analyses.composat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.util.StringUtil;


/**
 * The MaxSAT problem in CNF format.
 * @author xin
 *
 */
public class Problem {
	private Set<Constraint> constrs;
	private List<Hint> hints;
	private int id;
	private Set<Integer> domain;
	
	public Problem(){
		this.constrs = new HashSet<Constraint>();
		this.hints = new ArrayList<Hint>();
		this.id = SatConfig.problemId;
		SatConfig.problemId++;
	}
	
	public Problem(String path){
		this();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
			while(true){
				String line = br.readLine();
				if(line == null)
					break;
				line = line.trim();
				if(line.equals(""))
					continue;
				this.constrs.add(new Constraint(line));
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public void addHint(Hint hint){
		this.hints.add(hint);
	}
	
	public void addConstraint(Constraint cons){
		this.constrs.add(cons);
	}
	
	public Set<Constraint> getConstraints(){
		return this.constrs;
	}
	
	public List<Hint> getHints(){
		return hints;
	}
	
	public int getNumVars(){
		Set<Integer> d = this.getAtomDomain();
		return d.size();
	}
	
	public int getNumConstrs(){
		return this.constrs.size();
	}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((constrs == null) ? 0 : constrs.hashCode());
		result = prime * result + ((domain == null) ? 0 : domain.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Problem other = (Problem) obj;
		if (domain == null) {
			if (other.domain != null)
				return false;
		} else if (!domain.equals(other.domain))
			return false;
		if (constrs == null) {
			if (other.constrs != null)
				return false;
		} else if (!constrs.equals(other.constrs))
			return false;
		return true;
	}

	public int getId(){
		return this.id;
	}
	
	private void buildAtomDomain(){
		this.domain = new HashSet<Integer>();
		for(Constraint cons : this.constrs){
			for(Integer i : cons.getLiterals()){
				this.domain.add(Math.abs(i));
			}
		}
	}
	
	public void resetAtomDomain(){
		this.domain = null;
	}
	
	public Set<Integer> getAtomDomain(){
		if(domain == null)
			this.buildAtomDomain();
		return this.domain;
	}
	
	public List<Constraint> checkViolation(Set<Integer> posLiters, Set<Integer> negLiters){
		List<Constraint> ret = new ArrayList<Constraint>();
		for(Constraint cons : this.constrs){
			if(!cons.check(posLiters, negLiters))
				ret.add(cons);
		}
		return ret;
	}
	
	/*
	 * Evaluate the value of a certain solution. Any variable not contained in the solution is
	 * set to false by default.
	 */
	public double evaluate(Set<Integer> posLiterals){
		double ret = 0;
		Set<Integer> negLiterals = new HashSet<Integer>(this.getAtomDomain());
		negLiterals.removeAll(posLiterals);
		for(Constraint con : this.constrs){
			if(con.check(posLiterals,negLiterals)){
				if(!con.isHardConstraint())
					ret+=con.getWeight();
			}else
				if(con.isHardConstraint())
					return -1;
		}
		return ret;
	}
	
	@Override
	public String toString(){
		return StringUtil.join(constrs, "/\\");
	}
	
	/**
	 * Dump the constraint in a graph format. Currently only work for Horn MaxSAT
	 * @param path
	 */
	public void writeToDot(String path){
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			pw.println("digraph P"+id+" {");
			pw.println("T [style=filled, color=blue];");
			pw.println("F [style=filled, color=red];");
			Set<Integer> domain = this.getAtomDomain();
			for(int i : domain){
				pw.println("v"+i+" [style=filled, color = green];");
			}
			
			int counter = 0;
			for(Constraint c : this.getConstraints()){
				String cn = "c"+counter;
				pw.println(cn+" [label= \"" + cn + ", w = " + c.getWeight()+"\"];"); 
				List<Integer> posI = new ArrayList<Integer>();
				List<Integer> negI = new ArrayList<Integer>();
				for(int i : c.getLiterals())
					if(i > 0)
						posI.add(i);
					else
						negI.add(-i);
				for(int i : negI)
					pw.println("v"+i + " -> "+cn+";");
				if(negI.size() == 0)
					pw.println("T -> "+cn+";");
				for(int i : posI)
					pw.println(cn+" -> "+"v"+i+";");
				if(posI.size() == 0)
					pw.println(cn + " -> F;");
				counter++;
			}	
			pw.println("}");
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public void save(String path){
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			for(Constraint cons : this.constrs)
				cons.write(pw);
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
