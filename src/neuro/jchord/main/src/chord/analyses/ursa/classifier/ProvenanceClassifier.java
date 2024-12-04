package chord.analyses.ursa.classifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.classifier.Classifier;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import chord.util.Utils;

public class ProvenanceClassifier implements Classifier {
	private Map<String, Double> frMap;
	private Map<String, Double> edbMap;

	private void increaseCount(Map<String, Integer> countMap, String key) {
		Integer cur = countMap.get(key);
		if (cur == null)
			cur = 0;
		countMap.put(key, cur + 1);
	}

	private String getSig(ConstraintItem it) {
		StringBuilder ret = new StringBuilder();
		ret.append(it.getHeadTuple().getRelName());
		for (Tuple st : it.getSubTuples()) {
			ret.append("," + st.getRelName());
		}
		return ret.toString();
	}

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap, Set<ConstraintItem> provenance,
			Set<Tuple> relevantTuples) {
		frMap = new HashMap<String, Double>();
		Map<String, Integer> allCount = new HashMap<String, Integer>();
		Map<String, Integer> falseCount = new HashMap<String, Integer>();
		Map<Tuple, Boolean> labelMap = new HashMap<Tuple, Boolean>();
		labelMap.putAll(edbLabelMap);
		labelMap.putAll(idbLabelMap);
		for (ConstraintItem it : provenance) {
			String sig = this.getSig(it);
			this.increaseCount(allCount, sig);
			boolean ifBodyAllTrue = true;
			for (int i = 0; i < it.getSubTuples().size(); i++) {
				if (it.getSubTuplesSign().get(i)) {
					ifBodyAllTrue &= labelMap.get(it.getSubTuples().get(i));
				}
			}
			if (ifBodyAllTrue && !labelMap.get(it.getHeadTuple())) {
				this.increaseCount(falseCount, sig);
			}
		}

		for (Map.Entry<String, Integer> ae : allCount.entrySet()) {
			String sig = ae.getKey();
			Integer ac = ae.getValue();
			Integer fc = falseCount.get(sig);
			if (fc == null)
				fc = 0;
			double fr = (fc.doubleValue()) / (ac.doubleValue());
			frMap.put(sig, fr);
		}

		edbMap = new HashMap<String, Double>();
		Map<String, Integer> allEdbCount = new HashMap<String, Integer>();
		Map<String, Integer> falseEdbCount = new HashMap<String, Integer>();
		for (Map.Entry<Tuple, Boolean> e : edbLabelMap.entrySet()) {
			String relName = e.getKey().getRelName();
			this.increaseCount(allEdbCount, relName);
			if (!e.getValue())
				this.increaseCount(falseEdbCount, relName);
		}

		for (Map.Entry<String, Integer> ae : allEdbCount.entrySet()) {
			String relName = ae.getKey();
			Integer ac = ae.getValue();
			Integer fc = falseEdbCount.get(relName);
			if (fc == null)
				fc = 0;
			double fr = fc.doubleValue() / ac.doubleValue();
			edbMap.put(relName, fr);
		}
	}

	@Override
	public void save(String path) {
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			for (Map.Entry<String, Double> e : this.frMap.entrySet()) {
				pw.println(e.getKey() + " " + e.getValue());
			}
			pw.println();
			for (Map.Entry<String, Double> e : this.edbMap.entrySet()) {
				pw.println(e.getKey() + " " + e.getValue());
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public void load(String path) {
		frMap = new HashMap<String, Double>();
		edbMap = new HashMap<String, Double>();
		try {
			Scanner sc = new Scanner(new File(path));
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				if (line.trim().equals(""))
					break;
				String tokens[] = line.split("\\s+");
				frMap.put(tokens[0].trim(), Double.parseDouble(tokens[1].trim()));
			}
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String tokens[] = line.split("\\s+");
				edbMap.put(tokens[0].trim(), Double.parseDouble(tokens[1].trim()));
			}
			sc.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private <K, V> void addToMap(Map<K, Set<V>> map, K k, V v) {
		Set<V> vSet = map.get(k);
		if (vSet == null) {
			vSet = new HashSet<V>();
			map.put(k, vSet);
		}
		vSet.add(v);
	}

	private <K, V> void addToMap1(Map<K, ArraySet<V>> map, K k, V v) {
		ArraySet<V> vSet = map.get(k);
		if (vSet == null) {
			vSet = new ArraySet<V>();
			map.put(k, vSet);
		}
		vSet.add(v);
	}

	private Map<Tuple, Double> prediction = null;

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		if (prediction != null) {
			if (!prediction.containsKey(t))
				return this.edbMap.get(t.getRelName());
			return prediction.get(t);
		}
		this.prediction = new HashMap<Tuple, Double>();
		Map<Tuple, Set<ConstraintItem>> headToRules = new HashMap<Tuple, Set<ConstraintItem>>();
		Map<Tuple, Set<ConstraintItem>> bodyToRules = new HashMap<Tuple, Set<ConstraintItem>>();
		for (ConstraintItem ci : provenance) {
			if (ci.getSubTuples().contains(ci.getHeadTuple()))
				continue;
			this.addToMap(headToRules, ci.getHeadTuple(), ci);
			for (int i = 0; i < ci.getSubTuples().size(); i++) {
				if (ci.getSubTuplesSign().get(i)) {
					this.addToMap(bodyToRules, ci.getSubTuples().get(i), ci);
				}
			}
		}
		this.eliminateBackEdges(headToRules, bodyToRules);
		Set<Tuple> edbTuples = new HashSet<Tuple>();
		edbTuples.addAll(bodyToRules.keySet());
		edbTuples.removeAll(headToRules.keySet());

		for (Tuple tt : bodyToRules.keySet())
			prediction.put(tt, 0.0);

		for (Tuple tt : headToRules.keySet())
			prediction.put(tt, 0.0);

		for (Tuple edb : edbTuples) {
			if (edb.getRelName().equals("PathEdge_cs"))
				prediction.put(edb, 0.0);
			else
				prediction.put(edb, edbMap.get(edb.getRelName()));
		}
		Queue<Tuple> workList = new LinkedList<Tuple>();
		workList.addAll(edbTuples);
		while (!workList.isEmpty()) {
			Tuple changed = workList.remove();
			if (bodyToRules.containsKey(changed))
				for (ConstraintItem ci : bodyToRules.get(changed)) {
					Tuple head = ci.getHeadTuple();
					double oldV = prediction.get(head);
					double newV = 0.0;
					for (ConstraintItem ci1 : headToRules.get(head)) {
						double temp = 1 - this.frMap.get(this.getSig(ci1));
						for (int i = 0; i < ci1.getSubTuples().size(); i++) {
							if (ci1.getSubTuplesSign().get(i)) {
								temp *= (1 - prediction.get(ci1.getSubTuples().get(i)));
							}
						}
						newV += temp;
					}
					if (newV > 1)
						newV = 1;
					newV = 1 - newV;
					if (oldV != newV)
						workList.add(head);
					prediction.put(head, newV);
				}
		}

		if (!prediction.containsKey(t))
			return this.edbMap.get(t.getRelName());

		return prediction.get(t);
	}

	void eliminateBackEdges(Map<Tuple, Set<ConstraintItem>> headToRules, Map<Tuple, Set<ConstraintItem>> bodyToRules) {
		double top = 10000*10000;
		Map<Tuple, Double> rankMap = new HashMap<Tuple, Double>();
		Set<Tuple> evidenceTuples = new HashSet<Tuple>();
		evidenceTuples.addAll(bodyToRules.keySet());
		evidenceTuples.removeAll(headToRules.keySet());
		for(Tuple t : evidenceTuples)
			rankMap.put(t, 0.0);
		for(Tuple t : headToRules.keySet()){
			rankMap.put(t, top);
		}
		Queue<Tuple> workList = new LinkedList<Tuple>();
		workList.addAll(evidenceTuples);
		while(!workList.isEmpty()){
			Tuple t = workList.remove();
			Set<Tuple> heads = new ArraySet<Tuple>();
			if (bodyToRules.containsKey(t))
				for (ConstraintItem ci : bodyToRules.get(t)) {
					heads.add(ci.getHeadTuple());
				}
			for(Tuple h : heads){
				double oldV = rankMap.get(h);
				double newV = top;
				for(ConstraintItem ci : headToRules.get(h)){
					double temp = 0.0;
					for(int i = 0 ; i < ci.getSubTuples().size(); i++){
						if(ci.getSubTuplesSign().get(i)){
							temp = Math.max(temp, rankMap.get(ci.getSubTuples().get(i)));
						}
					}
					temp+=1;
					newV = Math.min(newV, temp);
				}
				if(newV != oldV){
					rankMap.put(h, newV);
					workList.add(h);
				}
			}
		}
		Set<ConstraintItem> backEdges = new HashSet<ConstraintItem>();
		Set<ConstraintItem> allCons = new HashSet<ConstraintItem>();
		for(Set<ConstraintItem> cs : headToRules.values()){
			allCons.addAll(cs);
		}
		for(ConstraintItem ci : allCons){
			double hRank = rankMap.get(ci.getHeadTuple());
			for(int i = 0 ; i < ci.getSubTuples().size(); i++){
				if(ci.getSubTuplesSign().get(i)){
					if(hRank <= rankMap.get(ci.getSubTuples().get(i))){
						backEdges.add(ci);
						break;
					}
				}
			}
		}
		for (Map.Entry<Tuple, Set<ConstraintItem>> e : headToRules.entrySet())
			e.getValue().removeAll(backEdges);

		for (Map.Entry<Tuple, Set<ConstraintItem>> e : bodyToRules.entrySet())
			e.getValue().removeAll(backEdges);

	}

	void eliminateBackEdges1(Map<Tuple, Set<ConstraintItem>> headToRules, Map<Tuple, Set<ConstraintItem>> bodyToRules) {
		Map<Tuple, Set<Tuple>> dom = new HashMap<Tuple, Set<Tuple>>();
		Set<Tuple> evidenceTuples = new HashSet<Tuple>();
		evidenceTuples.addAll(bodyToRules.keySet());
		evidenceTuples.removeAll(headToRules.keySet());

		for (Tuple key : evidenceTuples) {
			Set<Tuple> val = new HashSet<Tuple>();
			val.add(null);
			val.add(key);
			dom.put(key, val);
		}

		List<Tuple> order = new LinkedList<Tuple>();
		Set<Tuple> in_order = new HashSet<Tuple>();

		in_order.addAll(evidenceTuples);

		int n = headToRules.size();
		while (order.size() < n) {
			for (Tuple key : headToRules.keySet()) {
				if (in_order.contains(key)) {
					continue;
				}

				Boolean derived = false;
				for (ConstraintItem gc : headToRules.get(key)) {
					if (canDerive(gc, in_order)) {
						derived = true;
						break;
					}
				}

				if (derived) {
					order.add(key);
					in_order.add(key);
				}
			}
		}

		// System.out.println("after initialization, dom: " + dom);

		Boolean updated = true;
		int iter = 0;
		while (updated) {
			++iter;
			System.out.println("Back edge elimination Iteration: " + iter);

			updated = false;

			for (Tuple key : order) {
				Set<Tuple> val = dom.get(key);

				Set<Tuple> new_val = null;
				for (ConstraintItem clause : headToRules.get(key)) {
					Set<Tuple> clause_dom = getClauseDominator(clause, dom);
					// System.out.println("clause: " + clause);
					// System.out.println("clause_dom: " + clause_dom);

					if (clause_dom == null) {
						continue;
					}

					if (new_val == null) {
						new_val = clause_dom;
					} else {
						new_val.retainAll(clause_dom);
					}
				}

				// System.out.println("For key: " + key + ", new val: " +
				// new_val);

				if (new_val == null) {
					if (val != null) {
						System.err.println("Error: new_val becomes Top, which should not happen.");
						throw new RuntimeException("Error: new_val becomes Top, which should not happen.");
					}
				} else {
					new_val.add(key); // add itself as a dominator

					if (!Utils.areEqual(val, new_val)) {
						dom.put(key, new_val);
						updated = true;
					}
				}
			}
		}

		// System.out.println("after propagation, dom: " + dom);

		Set<ConstraintItem> clauses = new HashSet<ConstraintItem>();

		for (Set<ConstraintItem> v : headToRules.values())
			clauses.addAll(v);

		for (Set<ConstraintItem> v : bodyToRules.values())
			clauses.addAll(v);

		Set<ConstraintItem> backEdges = new HashSet<ConstraintItem>();

		for (ConstraintItem gc : clauses) {
			if (isBackEdge(gc, dom)) {
				backEdges.add(gc);
			}
		}

		for (Map.Entry<Tuple, Set<ConstraintItem>> e : headToRules.entrySet())
			e.getValue().removeAll(backEdges);

		for (Map.Entry<Tuple, Set<ConstraintItem>> e : bodyToRules.entrySet())
			e.getValue().removeAll(backEdges);
	}

	private boolean canDerive(ConstraintItem cons, Set<Tuple> st) {
		Tuple head = cons.getHeadTuple();

		for (int i = 0; i < cons.getSubTuples().size(); i++) {
			if (!cons.getSubTuplesSign().get(i))
				continue;
			Tuple t = cons.getSubTuples().get(i);
			if (!st.contains(t)) {
				return false;
			}
		}
		return true;
	}

	private Set<Tuple> getClauseDominator(ConstraintItem clause, Map<Tuple, Set<Tuple>> dom) {
		Set<Tuple> clause_dom = new HashSet<Tuple>();
		Tuple head = clause.getHeadTuple();
		for (int i = 0; i < clause.getSubTuples().size(); i++) {
			if (!clause.getSubTuplesSign().get(i))
				continue;
			Tuple t = clause.getSubTuples().get(i);
			if (dom.get(t) == null) { // we use null to represent the Top
				// System.out.println("atmId: " + atmId + ", related dom: " +
				// dom.get(atmId));
				clause_dom = null;
				break;
			}

			clause_dom.addAll(dom.get(t));
		}

		return clause_dom;
	}

	private Boolean isBackEdge(ConstraintItem clause, Map<Tuple, Set<Tuple>> dom) {
		Tuple head = clause.getHeadTuple();

		for (int i = 0; i < clause.getSubTuples().size(); i++) {
			if (!clause.getSubTuplesSign().get(i))
				continue;

			Tuple t = clause.getSubTuples().get(i);
			Set<Tuple> D = dom.get(t);
			if (D == null) {
				System.err.println("Error: there should not exist any top element at this point");
				throw new RuntimeException("Error: there should not exist any top element at this point");
				// continue;
			}

			if (D.contains(head)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void done() {
		// TODO Auto-generated method stub
		
	}
}
