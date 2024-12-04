package chord.analyses.experiment.classifier;

import java.util.ArrayList;

public class ThresholdEstimator extends DefaultEstimator {
	@Override protected double estimate(ArrayList<Double> features) {
		double res = super.estimate(features);
		return ((res > 0.98) ? 1.0 : 0.0);  
	}
}
