package chord.analyses.bigcode.connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import joeq.Class.jq_Field;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.var.DomV;
import chord.util.ArraySet;

public class VariablesPartition {
	public static final int MAX_VARS = 100000;
	public static final VariablesPartition EMPTY_PARTITION = new VariablesPartition(new HashSet<Set<Variable>>());
	public static boolean DEBUG = false;
	public final Set<Set<Variable>> partition;
	static DomV domV;
	static DomF domF;

	public static void setVarDom(DomV d) { domV = d; }

	public static void setStaticFieldsDom(DomF d) { domF = d; }
	
	public static Set<Variable> removeVariables(Set<Variable> set, List<Register> liveRefVars) {
		Set<Variable> newSet = new HashSet<Variable>();
		for (Variable v : set) {
			if (v instanceof LocalVariable) {
				Register r = VariablesPartition.domV.get(v.index);
				if (liveRefVars.contains(r))
					newSet.add(v);
			} else
				newSet.add(v);
		}
		return newSet;
	}
	
	public VariablesPartition(Set<Set<Variable>> p) { partition = p;}

	public VariablesPartition(VariablesPartition thatVP) {
		Set<Set<Variable>> thatP = thatVP.partition;
		partition = new HashSet<Set<Variable>>(thatP.size());
		for (Set<Variable> set : thatP) {
			Set<Variable> newSet = new HashSet<Variable>(set);
			partition.add(newSet);
		}
	}

	static public VariablesPartition createDinit(Set<Integer> lvSet, Set<Integer> stSet) {
		// TODO: Ghila, I removed your createBottom(lvSet, stSet) as it is same as this method, can you check?
		// also, I changed your below code, please check if it is correct.
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
		return new VariablesPartition(p);
	}

	public static VariablesPartition createDinit() { return EMPTY_PARTITION; }

	
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
			set1 = VariablesPartition.getBasicSet(v1.index, v1.isStatic);
		if (set2 == null)
			set2 = VariablesPartition.getBasicSet(v2.index, v2.isStatic);
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
		if (!optimizeTmpSet()) newP.add(copyTmpSet());
		return new VariablesPartition(newP);
	}

	public void addLocal(Variable v1, Variable v2) {
		
		Set<Variable> set1 = null, set2 = null;
		Set<Set<Variable>> oldP = this.partition;
		
		for (Set<Variable> set : oldP) {
			if (set.contains(v1)){
				set1 = set;
			}
			if (set1 != null)
				break;
		}
		if (set1 == null){
			set1 = new HashSet<Variable>(VariablesPartition.getBasicSet(v1.index, v1.isStatic));
			oldP.add(set1);
		}		
		set2 = VariablesPartition.getBasicSet(v2.index, v2.isStatic);
		set1.addAll(set2);
	}
	
//	//this function is used in order to do the merge locally on the partition and not to create a new one each time. Used when m
//	//merging a lot (for example in getTargetPart or getInitpart)
//	public boolean mergeLocal(Variable v1, Variable v2) {
//		if (v1 == null || v2 == null)  return false;
//		Set<Variable> set1 = null, set2 = null;	
//		Set<Set<Variable>> oldP = this.partition;
//	
//		for (Set<Variable> set : oldP) {
//			if (set.contains(v1))
//				set1 = set;
//			if (set.contains(v2)) 
//				set2 = set;
//			if (set1 != null && set2 != null)
//				break;
//		}
//		if (set1 == null)
//			set1 = VariablesPartition.getBasicSet(v1.index, v1.isStatic);
//		if (set2 == null)
//			set2 = VariablesPartition.getBasicSet(v2.index, v2.isStatic);
//		if (set1.equals(set2))
//			return false;
//					
//		oldP.remove(set1);
//		oldP.remove(set2);
//		
//		tmpSet.clear();
//		tmpSet.addAll(set1);
//		tmpSet.addAll(set2);
//		if (!optimizeTmpSet()) oldP.add(copyTmpSet());
//		return true;
//	}
	
//	private boolean isBasicSet(Set<Variable> set) {
//		if (set.size() != 2 && set.size() != 1)
//			return false;
//		Iterator<Variable> it = set.iterator();
//		Variable vFirst = it.next();		
//		if (getBasicSet(vFirst.index, vFirst.isStatic) == set) return true;
//		if (getSingleSet(vFirst) == set) return true;
//		return false;
//	}
	

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
		if (n > 0 && !optimizeTmpSet())	newP.add(copyTmpSet());
		return new VariablesPartition(newP);
	}
	
	public void removeLocal(Variable v) {
		Set<Variable> set = getSet(v);
		if (set == null)
			set = getBasicSet(v.index, v.isStatic);
		if (set.size() == 1)
			return;
		Set<Set<Variable>> oldP = partition;		
		oldP.add(getSingleSet(v));
		oldP.remove(set);
		tmpSet.clear();
		tmpSet.addAll(set);
		tmpSet.remove(v);
		int n = tmpSet.size();
		if (n > 0 && !optimizeTmpSet()) oldP.add(copyTmpSet());
	}

	public Set<Variable> getSet(Variable v) {
		for (Set<Variable> set : partition) {
			if (set.contains(v)) return set;
		}
		return null;
	}

	public VariablesPartition merge(VariablesPartition oldVP) {
		//VariablesPartition testVP = new VariablesPartition(this);
		VariablesPartition newVP = this;
		for (Set<Variable> set : oldVP.partition) {
			Variable v1 = null;
			for (Variable v2 : set) {
		//		testVP = testVP.merge(v1, v2);
				newVP = newVP.merge(v1, v2);
				v1 = v2;
			}
		}
		for (Set<Variable> set : newVP.partition) {
			for (Variable v2 : set) {
				if (!v2.isOld()){	
					if (oldVP.getSet(v2) == null){
						newVP = newVP.merge(v2, v2.isStatic()? StaticVariable.getNewStaticVariable(v2.index + MAX_VARS) :  LocalVariable.getNewLocalVariable(v2.index + MAX_VARS));
					//	testVP = testVP.merge(v2, v2.isStatic()? StaticVariable.getNewStaticVariable(v2.index + MAX_VARS) :  LocalVariable.getNewLocalVariable(v2.index + MAX_VARS));
					}
				}
			}
		}
		//assert(newVP.equals(testVP));
		return newVP;
	}

	public VariablesPartition removeVariables(List<Register> liveRefVars) {
		Set<Set<Variable>> oldP = this.partition;
		Set<Set<Variable>> newP = new HashSet<Set<Variable>>(oldP.size());
		for (Set<Variable> set : oldP)
			newP.add(removeVariables(set, liveRefVars));
		return new VariablesPartition(newP);
	}

	public Set<Variable> getOthersExternal(Variable var, Set<Integer> invkMethVars) {
		Set<Variable> set = getSet(var);
		if (set == null) 
			set = VariablesPartition.getBasicSet(var.index, var.isStatic);
		Set<Variable> others = new HashSet<Variable>();  // TODO: performance
		for (Variable v : set) {
			if (!v.equals(var)) {
				if (v instanceof LocalVariable) {
					others.add(v);
				} else if (v.isOld() || !invkMethVars.contains(v.index)) {
					others.add(v);
				}
			}
		}
		return others;
	}
	
	public Set<Variable> getOthersLocal(Variable var) {
		Set<Variable> set = getSet(var);
		if (set == null)
			set = VariablesPartition.getBasicSet(var.index, var.isStatic);
		Set<Variable> others = new HashSet<Variable>();  // TODO: performance
		for (Variable v : set) {
			if (!v.equals(var)) {
				if (v.isOld())
					others.add(v);
			}
		}
		return others;
	}
	
	public void compare(VariablesPartition that) {
		for (Set<Variable> set : this.partition) {
			if (!that.partition.contains(set))
				System.out.println("partition 2 doesn't contain:" + set + "of partition 1");
		}
		for (Set<Variable> set : that.partition) {
			if (!this.partition.contains(set))
				System.out.println("partition 1 doesn't contain:" + set + "of partition 2");
		}
	}

	public boolean contains(VariablesPartition that) {
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

	public int getRealSize(List<Register> list, Set<Integer> staticVars) {
		int currentSize = partition.size();
		
		for (Register r: list) {
			if (getSet(LocalVariable.getNewLocalVariable(domV.indexOf(r))) == null)
				currentSize++;
		}
		for (Integer staticIndex: staticVars) {
			if (getSet(StaticVariable.getNewStaticVariable(staticIndex)) == null)
				currentSize++;
		}
		return currentSize;
	}

	
	public int getRelativeSize(List<Register> list)  {	
		Set<Set<Variable>> part = new HashSet<Set<Variable>>();
		for (Register r: list) {
			Set<Variable> varSet = getSet(LocalVariable.getNewLocalVariable(domV.indexOf(r)));
			if (varSet == null)
				part.add(getBasicSet(domV.indexOf(r), false));
			else
				part.add(varSet);
		}
		return part.size();
	}

	/********************************************************************************/
	// Operations on stBasicSetMap, lvBasicSetMap, singleSetMap
	// TODO: Use Collections.unmodifiableSet wrappers for now to make debugging easier.
	// But remove them eventually for performance.
	/********************************************************************************/

	private static final Map<Integer, Set<Variable>> stBasicSetMap = new HashMap<Integer, Set<Variable>>();
	private static final Map<Integer, Set<Variable>> lvBasicSetMap = new HashMap<Integer, Set<Variable>>();
	private static final Map<Variable, Set<Variable>> singleSetMap = new HashMap<Variable, Set<Variable>>();

	public static Set<Variable> getBasicSet(int index, boolean isStatic) {
		Set<Variable> set;
		int realIndex = index < MAX_VARS ? index : index - MAX_VARS;
		if (isStatic) {
			set = stBasicSetMap.get(realIndex);
			if (set == null) {
				set = new ArraySet<Variable>(2);
				set.add(StaticVariable.getNewStaticVariable(realIndex));
				set.add(StaticVariable.getNewStaticVariable(realIndex + MAX_VARS));
				stBasicSetMap.put(realIndex, Collections.unmodifiableSet(set));
			}
		} else {
			set = lvBasicSetMap.get(realIndex);
			if (set == null) {
				set = new ArraySet<Variable>(2);
				set.add(LocalVariable.getNewLocalVariable(realIndex));
				set.add(LocalVariable.getNewLocalVariable(realIndex + MAX_VARS));
				lvBasicSetMap.put(realIndex, Collections.unmodifiableSet(set));
			}
		}
		return set;
	}

	protected static Set<Variable> getSingleSet(Variable v) {
		Set<Variable> set = singleSetMap.get(v);
		if (set == null) {
			set = new ArraySet<Variable>(1);
			set.add(v);
			singleSetMap.put(v, Collections.unmodifiableSet(set));
		}
		return set;
	}
	
	/********************************************************************************/
	// Operations on tmpSet
	/********************************************************************************/
	
	protected static final Set<Variable> tmpSet = new HashSet<Variable>();  // TODO: make this HashSet?
	
	// assumes tmpSet is non-null and valid.
	protected static Set<Variable> copyTmpSet() {
		return (tmpSet.size() < 10) ? new ArraySet<Variable>(tmpSet) : new HashSet<Variable>(tmpSet);
	}

	// assumes tmpSet is non-null and valid.
	protected static boolean optimizeTmpSet() {
		if (tmpSet.size() != 2) return false;
		Iterator<Variable> it = tmpSet.iterator();
		Variable vFirst = it.next();		
		Variable vOther;
		if (vFirst.isOld()) {
			vOther = vFirst.isStatic ? StaticVariable.getNewStaticVariable(vFirst.index - VariablesPartition.MAX_VARS)
				: LocalVariable.getNewLocalVariable(vFirst.index - VariablesPartition.MAX_VARS);
		} else {
			vOther = vFirst.isStatic ? StaticVariable.getNewStaticVariable(vFirst.index + VariablesPartition.MAX_VARS)
				: LocalVariable.getNewLocalVariable(vFirst.index + VariablesPartition.MAX_VARS);
		}
		return (it.next() == vOther);
	}
	
	/********************************************************************************/
	// The dreaded operations: equals and hashCode
	/********************************************************************************/
	
	@Override
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof VariablesPartition)) return false;
		VariablesPartition that = (VariablesPartition) o;
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
				String s1 = "";
				if (v.isOld()) {
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
}

