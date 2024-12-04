package chord.analyses.experiment.classifier;

import com.google.common.base.Predicate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;
import chord.util.tuple.object.Pair;

import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.fail;
import static chord.util.SystemUtil.path;

public class BoundaryEstimator extends Estimator {

	private Set<ArrayList<Double>> negativeExamples = newHashSet();
	private Set<ArrayList<Double>> positiveExamples = newHashSet();

	@Override
	public void learnFromTuples(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<Tuple> selector
			) {
		negativeExamples.clear();
		positiveExamples.clear();
    for (ConstraintItem c : provenance) {
			Tuple t = c.getHeadTuple();
			if (t != null && selector.apply(t)) {
				int w = oracle.weight(t);
				if (w > 0) positiveExamples.add(extractor.features(provenance, t));
				else {
					boolean allPos = true;
					for (Tuple st : c.getSubTuples()) allPos &= oracle.weight(st) > 0;
					negativeExamples.add(extractor.features(provenance, t));
				}
			}
		}
	}

	@Override
	public void learnFromConstraints(
			Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance,
			FeatureExtractor extractor,
			Predicate<ConstraintItem> selector
			) {
		negativeExamples.clear();
		positiveExamples.clear();

    for (ConstraintItem c : provenance) {
			if (selector.apply(c)) {
				int w = oracle.weight(c);
				if (w > 0) positiveExamples.add(extractor.features(provenance, c));
				else negativeExamples.add(extractor.features(provenance, c));
			}
		}
	}


  @Override protected double estimate(ArrayList<Double> features) {
    if (negativeExamples.contains(features)) return -10000.0;
    else if (positiveExamples.contains(features)) return 1.0;
    else return 0.0;
  }

  @Override
  public void save(String estimatorName) {
	  try {
		  PrintWriter pw = new PrintWriter(path(Config.outDirName, estimatorName));
		  pw.println(negativeExamples.size());
		  for(ArrayList<Double> s : negativeExamples){
			  for(Double d : s){
				  pw.print(d + " ");
			  }
			  pw.println();
		  }

		  pw.println(positiveExamples.size());
		  for(ArrayList<Double> s : positiveExamples){
			  for(Double d : s){
				  pw.print(d + " ");
			  }
			  pw.println();
		  }

		  pw.flush(); pw.close();
	  } catch (IOException e) {
		  fail(e);
	  }
  }

  @Override
  public void load(String estimatorName) {
	  negativeExamples.clear();
	  positiveExamples.clear();
	  Set<ArrayList<Double>> currSet = null;

	  File f = new File(path(Config.outDirName, estimatorName));
	  Scanner sc;
	  try {
		  sc = new Scanner(f);
		  int linePos = 0;
		  int nextSwitch = -1;
		  while (sc.hasNext()) {
			  String line = sc.nextLine();
			  if(linePos == nextSwitch + 1){
				  currSet = (nextSwitch == -1) ? negativeExamples : positiveExamples;
				  nextSwitch = Integer.parseInt(line);
			  }else{
				  ArrayList<Double> arr = new ArrayList<Double>();
				  Scanner lineSc = new Scanner(line);
				  while (lineSc.hasNext()){
					  String c = lineSc.next();
					  if (!c.equals("")){
						  arr.add(Double.parseDouble(c));
					  }
				  }
				  currSet.add(arr);
			  }
			  linePos++;
		  }
	  } catch (FileNotFoundException e) {
		  fail(e);
	  }
  }

  @Override
  public void clear() {
	negativeExamples.clear();
	positiveExamples.clear();

  }
}
