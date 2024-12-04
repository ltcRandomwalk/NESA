package chord.analyses.experiment.classifier;

import static chord.util.ExceptionUtil.fail;

import com.google.common.base.Predicate;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;

import static chord.util.SystemUtil.path;

public class NaiveBayesIncrementalEstimator extends Estimator {

	private Instances trainData = null;
	private NaiveBayesUpdateable classifier = null;

	@Override
	protected double estimate(ArrayList<Double> features) {
		if ((features.size() + 1) != trainData.numAttributes()) fail("WEKA: How come I don't have enough coefficients?");
		if(classifier == null) fail("WEKA: Trying to estimate using an uninitialized/untrained classifier");

		try {
			double[] dataVals = new double[features.size() + 1];
			int i = 0;
			for (double x : features) dataVals[i++] = x;
			dataVals[i] = Utils.missingValue();

			//testData.add(new DenseInstance(1.0, dataVals));
			return classifier.classifyInstance(new DenseInstance(1.0, dataVals));
		} catch (Exception e) {
			fail("WEKA: Estimation error");
		}
		return 0;
	}

	private Instance addData(List<Double> XItem, double yItem) {
		try{
			if(trainData == null){
				ArrayList<Attribute> atts = new ArrayList<Attribute>();
				for(int i = 0; i < XItem.size(); i++){
					// - numeric
					atts.add(new Attribute("att" + i));
				}
				atts.add(new Attribute("att" + XItem.size()));
				// 2. create Instances object
				trainData = new Instances("TrainData", atts, 0);

				classifier = new NaiveBayesUpdateable();
				classifier.buildClassifier(trainData);
			}

			double[] dataVals = new double[XItem.size() + 1];
			int i = 0;
			for (double x : XItem) dataVals[i++] = x;
			dataVals[i] = yItem;
			//trainData.add(new DenseInstance(1.0, dataVals));
			return new DenseInstance(1.0, dataVals);
		}catch(Exception e){
			fail("WEKA: Error in building classifier");
		}
		return null;
	}

	@Override
	public void learnFromTuples(Oracle oracle, Set<Tuple> queries, Provenance provenance,
			FeatureExtractor extractor, Predicate<Tuple> selector) {

		try{
      for (ConstraintItem c : provenance) {
        Tuple t = c.getHeadTuple();
        if (t != null && selector.apply(t)) {
          List<Double> xItem = extractor.features(provenance, t);
          double yItem = (double) oracle.weight(t);
          classifier.updateClassifier(addData(xItem, yItem));
        }
			}
		} catch (Exception e) {
			fail("WEKA: Error in updating classifier");
		}
	}

	@Override
	public void learnFromConstraints(Oracle oracle,
			Set<Tuple> queries,
			Provenance provenance, FeatureExtractor extractor,
			Predicate<ConstraintItem> selector) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(String estimatorName) {
		if(classifier == null) fail("WEKA: Trying to save uninitialized/empty classifier");
		try {
			weka.core.SerializationHelper.write(path(Config.outDirName, estimatorName), classifier);
		} catch (Exception e) {
			fail("WEKA: Failed to save classifier to file");
		}
	}

	@Override
	public void load(String estimatorName) {
		try {
			classifier = (NaiveBayesUpdateable) weka.core.SerializationHelper.read(path(Config.outDirName, estimatorName));
		} catch (Exception e) {
			fail("WEKA: Failed to load classifier from file");
		}
	}

	@Override
	public void clear() {
		trainData = null;
		classifier = null;

	}

}
