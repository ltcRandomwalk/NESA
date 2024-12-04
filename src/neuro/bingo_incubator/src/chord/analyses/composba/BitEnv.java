package chord.analyses.composba;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BitEnv<E> {

	Map<E, BitSet> envMap;

	public BitEnv(){
		envMap = new HashMap<E, BitSet>();
	}

	public BitEnv(BitEnv<E> env){
		assert(env != null);
		envMap = new HashMap<E, BitSet>(env.envMap);
	}

	public void insert(E v, int index){
		assert(v != null && index >= 0);
		BitSet varEnv  = new BitSet();
		varEnv.set(index);
		envMap.put(v, varEnv);
	}

	public void insert(E v, BitSet t){
		assert(v != null && t != null);
		envMap.put(v, t);
	}

	public void insert(BitEnv<E> env){
		assert(env != null);
		for(E v : env.envMap.keySet()){
			BitSet thatVarEnv = env.envMap.get(v);
			BitSet thisVarEnv = this.envMap.get(v);
			if(thisVarEnv != null){
				BitSet newVarEnv = new BitSet();
				newVarEnv.or(thatVarEnv);
				newVarEnv.or(thisVarEnv);
				this.envMap.put(v, newVarEnv);
			}else{
				this.envMap.put(v, thatVarEnv);
			}
		}
	}

	public boolean subsumes(BitEnv<E> env) {
		assert(env != null);
		for(E v : env.envMap.keySet()){
			BitSet thatVarEnv = env.envMap.get(v);
			BitSet thisVarEnv = this.envMap.get(v);
			if(thisVarEnv != null){
				BitSet newVarEnv = new BitSet();
				newVarEnv = (BitSet) thisVarEnv.clone();
				newVarEnv.or(thatVarEnv);
				if (!newVarEnv.equals(thisVarEnv)) return false;
			} else {
				return false;
			}
		}
		return true;
	}
	
	public BitSet get(E v){
		assert(v != null);
		return envMap.get(v);
	}

	public BitSet remove(E v){
		assert(v != null);
		return envMap.remove(v);
	}

	public boolean isEmpty(){
		return envMap.isEmpty();
	}

	public boolean containsAll(BitEnv<E> that){
		return false;
	}
	
	public Set<E> getKeySet(){
		return this.envMap.keySet();
	}

	@Override
	public int hashCode(){
		return envMap.hashCode();
	}

	@Override
	public boolean equals(Object o){
		if(this == o) return true;
		if (getClass() != o.getClass())
			return false;
		BitEnv<E> that = (BitEnv<E>) o;
		return envMap.equals(that.envMap);
	}
	
	public boolean wildcardEquals(Object o){
		if(this == o) return true;
		if (getClass() != o.getClass())
			return false;
		BitEnv<E> that = (BitEnv<E>) o;
//		return that.envMap.entrySet().containsAll(envMap.entrySet());
		
		// We assume that only this object can have wildcard entries.
		Map<E, BitSet> thisMap = envMap;
		Map<E, BitSet> thatMap = that.envMap;
		if (thatMap == thisMap)
            return true;
        
		if (thatMap.size() > thisMap.size())
			return false;

		Set<E> thisKeySet = thisMap.keySet();
		Set<E> thatKeySet = thatMap.keySet();
		
		if (!thisKeySet.containsAll(thatKeySet))
			return false;

		Iterator<Entry<E, BitSet>> i = thisMap.entrySet().iterator();
		while (i.hasNext()) {
			Entry<E,BitSet> e = i.next();
			E key = e.getKey();
			BitSet value = e.getValue();
			if (value == null) {
				if (!(thatMap.get(key)==null && thatMap.containsKey(key)))
					return false;
			} else if (value == BitAbstractState.markerBitSet) {
			} else {
				if (!value.equals(thatMap.get(key)))
					return false;
			}
		}
		return true;
	}
}

