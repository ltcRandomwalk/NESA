package chord.analyses.experiment.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;

public abstract class FeatureExtractor {
  protected Projector p;

  public FeatureExtractor(Projector p){
	  this.p = p;
  }

  public abstract ArrayList<Double> features(Provenance provenance, Tuple t);

  public abstract ArrayList<Double> features(Provenance provenance, ConstraintItem c);

  public abstract void clear();

  /**
   * Build the feature vector for all tuples and cache them for efficiency
   * @param rules
   * @param queries
   */
  public void prebuild(Provenance provenance, Set<Tuple> queries){

  }

  public String featureToString(ArrayList<Double> features){
	  return "TODO: implement method featureToString in "+this.getClass();
  }
}
