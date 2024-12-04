package chord.analyses.experiment.classifier;

import com.google.common.base.Predicate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import chord.analyses.experiment.solver.ConstraintItem;
import chord.analyses.experiment.solver.LookUpRule;
import chord.analyses.experiment.solver.Provenance;
import chord.analyses.experiment.solver.Tuple;
import chord.project.Config;
import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.*;
import static chord.util.SystemUtil.path;

/** Dumb-and-quick implementation for regularized linear regression.
  Let X be the observed features layed-out row-by-row.
  Let y be the observed weights recommended by the oracle.

  The parameter λ=0 corresponds to no-regularized linear regression.
*/
public class LinearEstimator extends Estimator {

  private final double lambda;
  private boolean upToDate; // whether {@code a} and {@code b} correspond to X and y
  private double[] a; // see {@code estimate}
  private ArrayList<double[]> X;
  private ArrayList<Double> y;

  static private final double EPS = 1e-9;

  public LinearEstimator(double lambda) {
    if (lambda < 0.0) fail("negative regularization coefficient");
    this.lambda = lambda;
    upToDate = false;
    X = newArrayList();
    y = newArrayList();
  }

  private void learn(List<Double> XItem, double yItem) {
    y.add(yItem);
    double[] xs = new double[XItem.size()];
    int i = 0; for (double x : XItem) xs[i++] = x;
    X.add(xs);
    upToDate = false;
  }

  @Override public void learnFromTuples(
    Oracle oracle,
    Set<Tuple> queries,
    Provenance provenance,
    FeatureExtractor extractor,
    Predicate<Tuple> selector
  ) {
	if(upToDate) System.out.println("EST: WARNING!! Trying to update a non-incremental classifier. Will overwrite the previous classifier");

    for (ConstraintItem c : provenance) {
      Tuple t = c.getHeadTuple();
      if (t != null && selector.apply(t)) {
        List<Double> xItem = extractor.features(provenance, t);
        double yItem = (double) oracle.weight(t);
        learn(xItem, yItem);
      }
    }
  }

  @Override
  public void learnFromConstraints(
  		Oracle oracle,
  		Set<Tuple> queries,
  		Provenance provenance, FeatureExtractor extractor,
  		Predicate<ConstraintItem> selector) {
  	// TODO Auto-generated method stub

  }

  @Override protected double estimate(ArrayList<Double> features) {
    if (!upToDate) computeCoefficients();
    if (features.size() >= a.length) fail("How come I don't have enough coefficients?");
    int i = 0;
    double r = a[a.length - 1];
    for (Double d : features) r += a[i++] * d;
    return r;
  }

  // roughly, a = (λΙ + X^T X)^(-1) X^T y
  public double[] computeCoefficients() {
    int m = X.size();
    int n = 0;
    for (double[] xs : X) n = Math.max(n, xs.length);

    if (m < n + 1) fail("I need at least " + (n + 1) +" samples to learn.");

    // row j represents the equation
    //    M[j][0]*a[0] + ... + M[j][n-1]*a[n-1] + M[j][n] * a[n] + M[j][n+1] == 0
    double[][] M = new double[n + 1][n + 2];

    // first derivatives with respect to a[0..n-1]
    for (int j1 = 0; j1 < n; ++j1) {
      for (int j2 = 0; j2 < n; ++j2) for (int i = 0; i < m; ++i)
        M[j1][j2] += Xat(i, j1) * Xat(i, j2);
      M[j1][j1] += lambda;
      for (int i = 0; i < m; ++i) M[j1][n] += Xat(i, j1);
      for (int i = 0; i < m; ++i) M[j1][n+1] -= y.get(i) * Xat(i, j1);
    }

    // then derivative with respect to a[n] (corresponds to a const feature =1)
    for (int j2 = 0; j2 < n; ++j2) for (int i = 0; i < m; ++i)
      M[n][j2] += Xat(i, j2);
    M[n][n] = m;
    for (int i = 0; i < m; ++i) M[n][n+1] -= y.get(i);

    // Now solve the system with gaussian elimination.
    // Start with reseting entries below the main diagonal. This does pivoting.
    // To choose a good pivot you need to permute rows.
    int[] p = new int[n + 1];
    for (int k = 0; k <= n; ++k) p[k] = k;
    for (int j2 = 0; j2 <= n; ++j2) {
      int tmp; // for swapping

      // Pick pivot.
      int bk = j2;
      for (int k = j2 + 1; k <= n; ++k)
        if (Math.abs(M[p[k]][j2]) > Math.abs(M[p[bk]][j2])) bk = k;
      tmp = p[j2]; p[j2] = p[bk]; p[bk] = tmp;
      int j1 = p[j2];
      if (Math.abs(M[j1][j2]) < EPS) fail("Oops: Tiny pivot.");

      // Reset the remaining rows, which are indexed by p[j2+1 .. n-1].
      for (int k = j2 + 1; k <= n; ++k) {
        int j3 = p[k];
        double alpha = M[j3][j2] / M[j1][j2];
        for (int j = j2 + 1; j <= n + 1; ++j) M[j3][j] -= alpha * M[j1][j];
        if (Math.abs(M[j3][j2] - alpha * M[j1][j2]) > EPS) fail("wat?");
        M[j3][j2] = 0.0;
      }
    }

    // Now do phase 2 of gaussian elimination: back-substitution.
    a = new double[n + 1];
    for (int j2 = n; j2 >= 0; --j2) {
      int j1 = p[j2];
      a[j2] = -M[j1][n + 1];
      for (int j3 = j2 + 1; j3 <= n; ++j3) a[j2] -= M[j1][j3] * a[j3];
      a[j2] /= M[j1][j2];
    }

    upToDate = true;
    return a;
  }

  // returns 0 if j is outside the range
  private double Xat(int i, int j) {
    double[] row = X.get(i);
    return j >= row.length? 0.0 : row[j];
  }

  @Override
  public void save(String estimatorName) {
	  try {
		  if (!upToDate) computeCoefficients();
          PrintWriter pw = new PrintWriter(path(
              Config.outDirName, estimatorName));
          for(int i = 0; i < a.length; i++){
        	  pw.println(a[i]);
          }

          pw.flush(); pw.close();
      } catch (IOException e) {
          fail(e);
      }
  }

  @Override
  public void load(String estimatorName) {
	  List<Double> fileData = newArrayList();
	  File f = new File(path(Config.outDirName, estimatorName));
	  Scanner sc;
	  try {
		  sc = new Scanner(f);
		  while (sc.hasNext()) {
			  String line = sc.nextLine();
			  fileData.add(Double.parseDouble(line));
		  }
		  a = new double[fileData.size()];
		  int i = 0;
		  for(Double d : fileData){
			  a[i] = d;
			  i++;
		  }
		  upToDate = true;
	  } catch (FileNotFoundException e) {
		  fail(e);
	  }
  }

  @Override
  public void clear() {
	  upToDate = false;
	  if(X != null) X.clear();
	  if(y != null) y.clear();
  }

}
