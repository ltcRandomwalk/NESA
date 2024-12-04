package chord.analyses.bigcode.connection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import joeq.Class.jq_Field;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class VariablesPartitionNullness extends VariablesPartition {

	static final int NULL_INT = 300000;
	public static final Variable NULL_VAR = LocalVariable.getNewLocalVariable(NULL_INT);

	
	public VariablesPartitionNullness(Set<Set<Variable>> p) { 
		super(p);
	}

	public VariablesPartitionNullness(VariablesPartitionNullness thatVP) {
		super(thatVP);
	}
	
	public static VariablesPartitionNullness createDinit(Set<Integer> lvSet, Set<Integer> stSet) {
		if (lvSet == null) lvSet = new HashSet<Integer>();
		if (stSet == null) stSet = new HashSet<Integer>();
		Set<Set<Variable>> p = new HashSet<Set<Variable>>(lvSet.size() + stSet.size());
		for (int v : lvSet) {
			Set<Variable> set = getBasicSet(v, false);
			p.add(set);
		}
		for (int v : stSet) {
			Set<Variable> set = getBasicSet(v, true);
			p.add(set);
		}
		Set<Variable> set = getSingleSet(NULL_VAR);
		p.add(set);
		
		return new VariablesPartitionNullness(p);
	}

	public static VariablesPartitionNullness createDinit() {
		Set<Set<Variable>> p = new HashSet<Set<Variable>>(); 
		Set<Variable> set = new HashSet<Variable> (getSingleSet(NULL_VAR));
		p.add(set);
		return new VariablesPartitionNullness(p);
	}

	public static VariablesPartitionNullness createInit(Set<Integer> lvSet, Set<Integer> stSet) {
		VariablesPartitionNullness p = createDinit();
		if (lvSet != null)
			for (int v : lvSet) {
				p.addLocal(NULL_VAR, LocalVariable.getNewLocalVariable(v));
			}
		if (stSet != null)
			for (int v : stSet) {
				p.addLocal(NULL_VAR, StaticVariable.getNewStaticVariable(v));
			}
		return new VariablesPartitionNullness(p);
	}
	
	public VariablesPartitionNullness setNull(Variable v) {
		VariablesPartitionNullness p = (VariablesPartitionNullness) remove(v);
		return (VariablesPartitionNullness) p.merge(v, NULL_VAR);
	}
	
	public boolean isNull(Variable v){
		for (Set<Variable> set : this.partition) {
			if (set.contains(v))
				return (set.contains(NULL_VAR));
		}
		return false;
	}
	
	@Override
	public VariablesPartition merge(Variable v1, Variable v2) {
		
		if (v1 == null || v2 ==  null) return this;
		Set<Variable> set1 = null, set2 = null;
		Set<Set<Variable>> oldP = this.partition;
		for (Set<Variable> set : oldP) {
			if (set.contains(v1))
				set1 = set;
			if (set.contains(v2)) 
				set2 = set;
			if (set1 != null && set2 != null)
				break;
		}
		if (set1 == null)
			set1 = VariablesPartitionNullness.getBasicSet(v1.index, v1.isStatic);
		if (set2 == null)
			set2 = VariablesPartitionNullness.getBasicSet(v2.index, v2.isStatic);
		if (set1.equals(set2))
			return this;
		Set<Set<Variable>> newP = new HashSet<Set<Variable>>(oldP.size());
		for (Set<Variable> set : oldP) {
			if (set != set1 && set != set2)
				newP.add(set);
		}
		tmpSet.clear();
		tmpSet.addAll(set1);
		tmpSet.addAll(set2);
		
		if (tmpSet.contains(NULL_VAR) || !optimizeTmpSet()) newP.add(copyTmpSet());
		return new VariablesPartitionNullness(newP);
	}

	@Override
	public VariablesPartition remove(Variable v) {
		Set<Variable> set = getSet(v);
		if (set == null)
			set = getBasicSet(v.index, v.isStatic);
		if (set.size() == 1)
			return this;
		Set<Set<Variable>> oldP = partition;
		Set<Set<Variable>> newP = new HashSet<Set<Variable>>(oldP.size() + 1);
		for (Set<Variable> s : oldP) {
			if (s != set) newP.add(s);
		}
		newP.add(getSingleSet(v));
		tmpSet.clear();
		tmpSet.addAll(set);
		tmpSet.remove(v);
		int n = tmpSet.size();
		if (n > 0 && (tmpSet.contains(NULL_VAR) || !optimizeTmpSet()))	newP.add(copyTmpSet());
		return new VariablesPartitionNullness(newP);
	}

//	public <T extends VariablesPartitionNullness> T foo(T t) {
//		return null;
//	}
	
	@Override
	public VariablesPartition merge(VariablesPartition oldVP) {
		VariablesPartitionNullness newVP = this;
		
		Set<Variable> nullSet1 = getSet(NULL_VAR);
		Set<Variable> nullSet2 = oldVP.getSet(NULL_VAR);
		Set<Variable> nullSetMerge = new HashSet<Variable>();  
		
		for (Variable v : nullSet1){
			if (nullSet2.contains(v)){
				nullSetMerge.add(v);
			}
		}
		//removing everything which is not in the intersection
		for (Variable v : nullSet1){
			if (!nullSetMerge.contains(v)){
				newVP = (VariablesPartitionNullness) newVP.remove(v);
			}
		}
		for (Variable v : nullSet2){
			if (!nullSetMerge.contains(v)){
				oldVP = oldVP.remove(v);
			}
		}
		
		for (Set<Variable> set : oldVP.partition) {
			Variable v1 = null;
			if (set.contains(NULL_VAR)) continue;
			for (Variable v2 : set) {
				newVP = (VariablesPartitionNullness) newVP.merge(v1, v2);
				v1 = v2;
			}
		}
		for (Set<Variable> set : newVP.partition) {
			for (Variable v2 : set) {
				if (v2 != NULL_VAR && !v2.isOld()){	
					if (oldVP.getSet(v2) == null){
						newVP = (VariablesPartitionNullness) newVP.merge(v2, v2.isStatic()? StaticVariable.getNewStaticVariable(v2.index + MAX_VARS) :  LocalVariable.getNewLocalVariable(v2.index + MAX_VARS));
					}
				}
			}
		}
		return newVP;
	}

	
	public void compare(VariablesPartitionNullness that) {
		for (Set<Variable> set : this.partition) {
			if (!that.partition.contains(set))
				System.out.println("partition 2 doesn't contain:" + set + "of partition 1");
		}
		for (Set<Variable> set : that.partition) {
			if (!this.partition.contains(set))
				System.out.println("partition 1 doesn't contain:" + set + "of partition 2");
		}
	}

	public boolean contains(VariablesPartitionNullness that) {
		for (Set<Variable> set : that.partition) {
			if (!partition.contains(set)) {
				boolean found = false;
				for (Set<Variable> mySet : partition) {
					if (mySet.containsAll(set)) {
						found = true;
						break;
					}
				}
				if (!found)
					return false;
			}
		}
		return true;
	}

	

	
	/********************************************************************************/
	// The dreaded operations: equals and hashCode
	/********************************************************************************/
	
	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof VariablesPartitionNullness)) return false;
		VariablesPartitionNullness that = (VariablesPartitionNullness) o;
		return this.partition.equals(that.partition);
	}

	@Override
	public int hashCode() {
		return partition.hashCode();
	}
	
	@Override
	public String toString() {
		String s = "{";
		for (Set<Variable> set : partition) {
			boolean isEmpty = true;
			for (Variable v : set) {
				if (isEmpty) {
					s += "{";
					isEmpty = false;
				}
				String s1 = "";
				if (v == NULL_VAR)
				{
					s1 = "NULL";
				}
				else if (v.isOld()) {
					if (v.isStatic()) {
						if (isEmpty) {
							s += "{";
							isEmpty = false;
						}
						jq_Field f = domF.get(v.index - MAX_VARS);
						s1 = "AUX-" + f.getName().toString();
					} else {
						Register r = domV.get(v.index - MAX_VARS);
						String str = domV.getMethod(r).getName() + "-"
								+ r.toString();
						if (str != null) {
							if (isEmpty) {
								s += "{";
								isEmpty = false;
							}
							s1 = "AUX-" + str;
						}
					}
				} else {
					if (v.isStatic) {
						jq_Field f = domF.get(v.index);
						if (isEmpty) {
							s += "{";
							isEmpty = false;
						}
						s1 = f.getName().toString() + "-" + v.index;

					} else {
						Register r = domV.get(v.index);
						s1 = domV.getMethod(r).getName() + "-" + r.toString()
								+ "-" + v.index;
						if (s1 != null) {
							if (isEmpty) {
								s += "{";
								isEmpty = false;
							}
						}
					}
				}
				s += (s1 != null) ? s1 + "," : "";
			}
			if (!isEmpty) {
				s += "}";
			}
		}
		s += "}";
		return s;
	}

	public int getRelativeSizeNullness(List<Register> list)  {	
		Set<Set<Variable>> part = new HashSet<Set<Variable>>();
		Set<Variable> nullSet = getSet(NULL_VAR);
		for (Register r: list) {
			Set<Variable> varSet = getSet(LocalVariable.getNewLocalVariable(domV.indexOf(r)));
			if (varSet == null)
				part.add(getBasicSet(domV.indexOf(r), false));
			else if (varSet == nullSet)
				part.add(getSingleSet(LocalVariable.getNewLocalVariable(domV.indexOf(r))));
			else
				part.add(varSet);
		}
		return part.size();
	}
}

