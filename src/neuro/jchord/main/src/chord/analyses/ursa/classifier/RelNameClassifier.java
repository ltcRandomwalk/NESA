package chord.analyses.ursa.classifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.analyses.ursa.ConstraintItem;
import chord.analyses.ursa.classifier.Classifier;
import chord.project.analyses.provenance.Tuple;

public class RelNameClassifier implements Classifier {
	Map<String, Double> relNameProbMap;

	private void increaseCount(Map<String, Integer> countMap, String key) {
		Integer cur = countMap.get(key);
		if (cur == null)
			cur = 0;
		countMap.put(key, cur + 1);
	}

	@Override
	public void train(Map<Tuple, Boolean> edbLabelMap, Map<Tuple, Boolean> idbLabelMap,
			Set<ConstraintItem> provenance, Set<Tuple> relevantTuples) {
		relNameProbMap = new HashMap<String, Double>();
		Map<String, Integer> relTupleCount = new HashMap<String, Integer>();
		Map<String, Integer> falseRelTupleCount = new HashMap<String, Integer>();
		for (Map.Entry<Tuple, Boolean> te : edbLabelMap.entrySet()) {
			if(!relevantTuples.contains(te.getKey()))
				continue;
			String relName = te.getKey().getRelName();
			this.increaseCount(relTupleCount, relName);
			if (!te.getValue()) {
				this.increaseCount(falseRelTupleCount, relName);
			}
		}
		for (Map.Entry<Tuple, Boolean> te : idbLabelMap.entrySet()) {
			if(!relevantTuples.contains(te.getKey()))
				continue;
			String relName = te.getKey().getRelName();
			this.increaseCount(relTupleCount, relName);
			if (!te.getValue()) {
				this.increaseCount(falseRelTupleCount, relName);
			}
		}
		for (Map.Entry<String, Integer> e : relTupleCount.entrySet()) {
			String relname = e.getKey();
			int base = e.getValue();
			int top = 0;
			if(falseRelTupleCount.containsKey(relname))
				top = falseRelTupleCount.get(relname);
			relNameProbMap.put(relname, ((double) top) / ((double) (base)));
		}
	}

	@Override
	public void save(String path) {
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			for (Map.Entry<String, Double> e : relNameProbMap.entrySet()) {
				pw.println(e.getKey()+" "+e.getValue());
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public void load(String path) {
		relNameProbMap = new HashMap<String,Double>();
		try {
			Scanner sc = new Scanner(new File(path));
			while(sc.hasNextLine()){
				String line = sc.nextLine();
				String tokens[] = line.split("\\s+");
				relNameProbMap.put(tokens[0], Double.parseDouble(tokens[1]));
			}
			sc.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public double predictFalse(Tuple t, Set<ConstraintItem> provenance) {
		return this.relNameProbMap.get(t.getRelName());
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
