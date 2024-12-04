package chord.analyses.mln;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import chord.bddbddb.Dom;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * -Dchord.mln.client=downcast -Dchord.mln.edbweight=-1/-2/
 * <weight> -Dchord.mln.infWeight -Dchord.mln.withProvenance
 * -Dchord.mln.posRatio -Dchord.mln.negRatio -Dchord.mln.ratio -Dchord.mln.mode
 * [oracle/batch/inter] -Dchord.mln.consWeight -Dchord.mln.oraclePath
 * -Dchord.mln.revMode [or/and]
 * [For ursa] -Dchord.ursa.classifierFile=<null>
 * 
 * @author Ravi
 * @author xin
 */
public abstract class MLNAnalysisDriver extends JavaAnalysis {
	protected enum Mode {
		ORACLE, LEARN, PROBLEM_GEN, BATCH, INTER
	}

	protected enum RevMode {
		AND, OR
	}

	protected Mode mode;
	protected RevMode revMode;
	protected int evidenceWeight = -1; // -1 = hard, -2 = generate weight based
										// on the query weights
	protected String infFeedbackWeight;
	protected boolean withProvenance;
	protected Double[] posFeedbackPercentage;
	protected Double[] negFeedbackPercentage;
	protected Double[] feedbackPercentage;
	protected String constraintWeight;
	protected String oraclePath;

	protected List<ITask> tasks;
	protected Set<Integer> revorDomain;
	protected RevConstraintGenerator revGen = null;
	protected boolean areCurrentRelsOracle = false;
	protected boolean runningBase = true;
	private String classifierPath = null;
	
	protected abstract Set<String> getDerivedRelations();

	protected abstract Set<String> getDomains();

	protected abstract Set<String> getInputRelations();

	protected abstract String getQueryRelation();

	protected abstract String[] getConfigFiles();

	protected abstract void genTasks();

	protected void throwUnknownClientException() {
		throw new RuntimeException("Unknown query relation");
	}

	protected abstract void runOracle();

	protected Set<Tuple> loadTuples(boolean useDerivedRels) {
		Set<Tuple> ret = new HashSet<Tuple>();
		Set<String> rels = useDerivedRels ? this.getDerivedRelations() : this.getInputRelations();
		for (String s : rels) {
			ProgramRel r = (ProgramRel) ClassicProject.g().getTrgt(s);
			r.load();
			for (int[] vals : r.getAryNIntTuples()) {
				Tuple temp = new Tuple(r, vals);
				ret.add(temp);
			}
		}
		return ret;
	}

	/**
	 * Load tuples from executed analyses. Note any tuple containing elements in
	 * DomC is projected.
	 * 
	 * @return
	 */
	protected Set<Tuple> loadProjectedTuples(boolean useDerivedRels) {
		Set<Tuple> ret = new HashSet<Tuple>();
		Set<String> rels = useDerivedRels ? this.getDerivedRelations() : this.getInputRelations();
		for (String s : rels) {
			ProgramRel r = (ProgramRel) ClassicProject.g().getTrgt(s);
			r.load();
			for (int[] vals : r.getAryNIntTuples()) {
				Tuple temp = new Tuple(r, vals);
				for (Tuple t : this.project(temp))
					ret.add(t);
			}
		}
		return ret;
	}

	protected abstract void runBaseCase();

	protected abstract Set<Tuple> project(Tuple t);

	protected Set<Tuple> getQueryTuples() {
		Set<Tuple> ret = new HashSet<Tuple>();
		String qs = this.getQueryRelation();
		ProgramRel qrel = (ProgramRel) ClassicProject.g().getTrgt(qs);
		qrel.load();
		Dom doms[] = qrel.getDoms();
		if (doms.length == 2) {
			// take the cross product of domains
			for (int i = 0; i < doms[0].size(); i++)
				for (int j = 0; j < doms[1].size(); j++) {
					int tempIdx[] = new int[2];
					tempIdx[0] = i;
					tempIdx[1] = j;
					ret.add(new Tuple(qrel, tempIdx));
				}
			return ret;
		} else if (doms.length == 1) {
			// take the cross product of domains
			for (int i = 0; i < doms[0].size(); i++) {
				int tempIdx[] = new int[1];
				tempIdx[0] = i;
				ret.add(new Tuple(qrel, tempIdx));
			}
			return ret;
		} else
			this.throwUnknownClientException();
		return null;
	}

	protected int getQueryTupleNum() {
		String qs = this.getQueryRelation();
		ProgramRel qrel = (ProgramRel) ClassicProject.g().getTrgt(qs);
		qrel.load();
		Dom doms[] = qrel.getDoms();
		if (doms.length == 2) {
			// take the cross product of domains

			return doms[0].size() * doms[1].size();
		} else if (doms.length == 1) {
			// take the cross product of domains
			return doms[0].size();
		} else
			this.throwUnknownClientException();
		return -1;
	}

	/**
	 * Generate edb file for MLN
	 * 
	 * @param evidenceFile
	 */
	protected void generateProblem(String evidenceFile) {
		try {
			PrintWriter pw = new PrintWriter(new File(evidenceFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Input tuples: ");
			for (String irs : this.getInputRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if (this.evidenceWeight == -1)
						pw.println(t);
					else if (this.evidenceWeight == -2)
						pw.println((qtsSize + 1) + " " + t);
					else
						pw.println(this.evidenceWeight + " " + t);
				}
			}
			pw.println("// Domains: ");
			for (String irs : this.getDomains()) {
				ProgramDom pr = (ProgramDom) ClassicProject.g().getTrgt(irs);
				for (Object o : pr) {
					String str = "dom_" + irs + "(" + pr.indexOf(o) + ")";
					if (this.evidenceWeight == -1)
						pw.println(str);
					else if (this.evidenceWeight == -2)
						pw.println((qtsSize + 1) + " " + str);
					else
						pw.println(this.evidenceWeight + " " + str);
				}
			}
			// reverted constraints domain
			for (Integer i : revorDomain) {
				pw.println("dom_revor(" + i + ")");
			}

			pw.println();
			pw.println("// Query tuples to enforce least fixpoint: ");
			pw.println("// Total query number: " + qtsSize);
			// for(Tuple t : qTs)
			// pw.println(-1+" "+t);
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Generate scope file for MLN
	 * 
	 * @param scopeFile
	 */
	protected void generateScope(String scopeFile) {
		try {
			// if (areCurrentRelsOracle) throw new RuntimeException("Run base
			// analysis first");

			PrintWriter pw = new PrintWriter(new File(scopeFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Derived tuples: ");
			for (String irs : this.getDerivedRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t);
				}
			}
			pw.println();
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Generate verbose version of scope file for MLN
	 * 
	 * @param scopeFile
	 */
	protected void generateScopeVerbose(String scopeFile) {
		try {
			// if (areCurrentRelsOracle) throw new RuntimeException("Run base
			// analysis first");

			PrintWriter pw = new PrintWriter(new File(scopeFile));
			// Set<Tuple> qTs = this.getQueryTuples();
			int qtsSize = this.getQueryTupleNum();
			pw.println("// Input tuples: ");
			for (String irs : this.getInputRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t.toVerboseString());
				}
			}

			pw.println("// Derived tuples: ");
			for (String irs : this.getDerivedRelations()) {
				ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(irs);
				pr.load();
				for (int[] indices : pr.getAryNIntTuples()) {
					Tuple t = new Tuple(pr, indices);
					if(!t.isSpurious())
						pw.println(t.toVerboseString());
				}
			}
			pw.println();
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateFeedback(String feedbackFile, Set<Tuple> baseTuples, Set<Tuple> oracleTuples) {
		try {
			PrintWriter pw = new PrintWriter(new File(feedbackFile));
			pw.println("// Feedback");
			int qtsNum = this.getQueryTupleNum();
			for (Tuple t : baseTuples) {
				boolean ifSurvive = oracleTuples.contains(t);
				if (this.evidenceWeight == -1)
					if (ifSurvive)
						pw.println(t);
					else
						pw.println("!" + t);
				else if (this.evidenceWeight == -2)
					if (ifSurvive)
						pw.println((qtsNum + 1) + " " + t);
					else
						pw.println((0 - qtsNum - 1) + " " + t);
				else if (ifSurvive)
					pw.println(this.evidenceWeight + " " + t);
				else
					pw.println((0 - this.evidenceWeight) + " " + t);
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateSimpleFeedback(String feedbackFile, Set<Tuple> baseQueries, Set<Tuple> oracleQueries) {
		try {
			if (negFeedbackPercentage.length != 0) {
				// Separate neg and pos feedback setting
				List<Tuple> provenQs = new ArrayList<Tuple>();
				List<Tuple> impossibleQs = new ArrayList<Tuple>();
				for (Tuple t : baseQueries) {
					boolean ifSurvive = oracleQueries.contains(t);
					if (ifSurvive) {
						impossibleQs.add(t);
					} else {
						provenQs.add(t);
					}
				}

				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();

				long provenSize = provenQs.size();
				long impossibleSize = impossibleQs.size();
				for (int k = 0; k < negFeedbackPercentage.length; ++k) {
					PrintWriter pw = new PrintWriter(new File(
							feedbackFile + negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long negFeedbackSize;
					long posFeedbackSize;

					// TODO: Reuse old feedback in every iteration vs. generate
					// new feedback.
					if (k == 0) {
						negFeedbackSize = Math.round(provenSize * (negFeedbackPercentage[k]));
						posFeedbackSize = Math.round(impossibleSize * (posFeedbackPercentage[k]));
					} else {
						negFeedbackSize = Math
								.round(provenSize * (negFeedbackPercentage[k] - negFeedbackPercentage[k - 1]));
						posFeedbackSize = Math
								.round(impossibleSize * (posFeedbackPercentage[k] - posFeedbackPercentage[k - 1]));
					}

					for (int i = 0; i < negFeedbackSize; ++i) {
						Tuple fTuple = provenQs.remove(rand.nextInt(provenQs.size()));
						negFeedback.add(fTuple);
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}

					rand = new Random(System.currentTimeMillis());
					for (int i = 0; i < posFeedbackSize; ++i) {
						Tuple fTuple = impossibleQs.remove(rand.nextInt(impossibleQs.size()));
						posFeedback.add(fTuple);
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}
					pw.flush();
					pw.close();

					this.generateRevertedConstraints(Config.outDirName + File.separator + "reverted_cons_"
							+ negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".txt", posFeedback);
				}
			} else {
				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();
				List<Tuple> remBaseQs = new ArrayList<Tuple>(baseQueries);

				long qSize = remBaseQs.size();
				for (int k = 0; k < feedbackPercentage.length; ++k) {
					PrintWriter pw = new PrintWriter(new File(feedbackFile + feedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long feedbackSize;
					// TODO: Reuse old feedback in every iteration vs. generate
					// new feedback.
					if (k == 0) {
						feedbackSize = Math.round(qSize * (feedbackPercentage[k]));
					} else {
						feedbackSize = Math.round(qSize * (feedbackPercentage[k] - feedbackPercentage[k - 1]));
					}

					for (int i = 0; i < feedbackSize; ++i) {
						Tuple fTuple = remBaseQs.remove(rand.nextInt(remBaseQs.size()));
						if (!oracleQueries.contains(fTuple)) {
							negFeedback.add(fTuple);
						} else {
							posFeedback.add(fTuple);

						}
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}

					pw.flush();
					pw.close();

				}
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateInfFeedback(String feedbackFile, Set<Tuple> baseQueries, Set<Tuple> oracleQueries) {
		try {
			if (areCurrentRelsOracle)
				throw new RuntimeException("Run base analysis first");

			if (negFeedbackPercentage.length != 0) {
				// Separate neg and pos feedback setting
				List<Tuple> provenQs = new ArrayList<Tuple>();
				List<Tuple> impossibleQs = new ArrayList<Tuple>();
				for (Tuple t : baseQueries) {
					boolean ifSurvive = oracleQueries.contains(t);
					if (ifSurvive) {
						impossibleQs.add(t);
					} else {
						provenQs.add(t);
					}
				}

				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();

				long provenSize = provenQs.size();
				long impossibleSize = impossibleQs.size();
				for (int k = 0; k < negFeedbackPercentage.length; ++k) {
					PrintWriter pw = new PrintWriter(new File(
							feedbackFile + negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long negFeedbackSize;
					long posFeedbackSize;
					// TODO: Reuse old feedback in every iteration vs. generate
					// new feedback.
					if (k == 0) {
						negFeedbackSize = Math.round(provenSize * (negFeedbackPercentage[k]));
						posFeedbackSize = Math.round(impossibleSize * (posFeedbackPercentage[k]));
					} else {
						negFeedbackSize = Math
								.round(provenSize * (negFeedbackPercentage[k] - negFeedbackPercentage[k - 1]));
						posFeedbackSize = Math
								.round(impossibleSize * (posFeedbackPercentage[k] - posFeedbackPercentage[k - 1]));
					}

					for (int i = 0; i < negFeedbackSize; ++i) {
						Tuple fTuple = provenQs.remove(rand.nextInt(provenQs.size()));
						negFeedback.add(fTuple);
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}

					rand = new Random(System.currentTimeMillis());
					for (int i = 0; i < posFeedbackSize; ++i) {
						Tuple fTuple = impossibleQs.remove(rand.nextInt(impossibleQs.size()));
						if (this.withProvenance) {
							for (Tuple t : this.getProvenance(fTuple)) {
								posFeedback.add(t);

								/*
								 * The following way of getting provenance
								 * tuples is incorrect. It gets all the tuples
								 * in the provenance of the oracle instead of
								 * the base analysis. As a result, its already
								 * leading to a selection of the base tuples
								 * that should be derived and is some sense
								 * amounts to providing feedback at the
								 * intermediate tuple level.
								 */
								// Set<Tuple> projTuples = this.project(t);
								// for (Tuple t1 : projTuples) {
								// if (baseTuples.contains(t1)) {
								// pw.println(infFeedbackWeight + " " + t1);
								// }
								// }
							}
						} else {
							posFeedback.add(fTuple);
						}
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}
					pw.flush();
					pw.close();

					this.generateRevertedConstraints(Config.outDirName + File.separator + "reverted_cons_"
							+ negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".txt", posFeedback);
				}
			} else {
				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();
				List<Tuple> remBaseQs = new ArrayList<Tuple>(baseQueries);

				long qSize = remBaseQs.size();
				for (int k = 0; k < feedbackPercentage.length; ++k) {
					PrintWriter pw = new PrintWriter(new File(feedbackFile + feedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long feedbackSize;
					// To reuse old feedback in every iteration. This setting
					// used for PLDI'15.
					/*
					 * if (k == 0) { feedbackSize = Math.round(qSize *
					 * (feedbackPercentage[k])); } else { feedbackSize =
					 * Math.round(qSize * (feedbackPercentage[k] -
					 * feedbackPercentage[k-1])); }
					 */

					// To generate new feedback in every iteration. This setting
					// used for FSE'15.
					posFeedback.clear();
					negFeedback.clear();
					feedbackSize = Math.round(qSize * (feedbackPercentage[k]));

					for (int i = 0; i < feedbackSize; ++i) {
						// To reuse old feedback in every iteration. This
						// setting used for PLDI'15.
						// Tuple fTuple =
						// remBaseQs.remove(rand.nextInt(remBaseQs.size()));
						// To generate new feedback in every iteration. This
						// setting used for FSE'15.
						Tuple fTuple = remBaseQs.get(rand.nextInt(remBaseQs.size()));
						if (!oracleQueries.contains(fTuple)) {
							negFeedback.add(fTuple);
						} else {
							if (this.withProvenance) {
								for (Tuple t : this.getProvenance(fTuple)) {
									posFeedback.add(t);

									/*
									 * The following way of getting provenance
									 * tuples is incorrect. It gets all the
									 * tuples in the provenance of the oracle
									 * instead of the base analysis. As a
									 * result, its already leading to a
									 * selection of the base tuples that should
									 * be derived and is some sense amounts to
									 * providing feedback at the intermediate
									 * tuple level.
									 */
									// Set<Tuple> projTuples = this.project(t);
									// for (Tuple t1 : projTuples) {
									// if (baseTuples.contains(t1)) {
									// pw.println(infFeedbackWeight + " " + t1);
									// }
									// }
								}
							} else {
								posFeedback.add(fTuple);
							}

						}
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}

					pw.flush();
					pw.close();

					this.generateRevertedConstraints(
							Config.outDirName + File.separator + "reverted_cons_" + feedbackPercentage[k] + ".txt",
							posFeedback);
				}
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateUserStudyFeedback(String feedbackFile) {
		String userFileName = Config.workDirName + File.separator;
		String userName = System.getProperty("chord.mln.user", "user1.txt");
		if (userName.charAt(0) == '/')
			userFileName = userName;
		else
			userFileName += userName;
		try {
			if (areCurrentRelsOracle)
				throw new RuntimeException("Run base analysis first");
			List<Tuple> provenQs = new ArrayList<Tuple>();
			List<Tuple> impossibleQs = new ArrayList<Tuple>();

			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(userFileName)));
			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().equals("")) {
					continue;
				}
				if (line.startsWith("//")) {
					continue;
				}
				if (line.startsWith("!")) {
					Tuple current = new Tuple(line.substring(1));
					provenQs.add(current);
				} else {
					Tuple current = new Tuple(line);
					impossibleQs.add(current);
				}
			}
			br.close();

			if (negFeedbackPercentage.length != 0) {
				// Separate neg and pos feedback setting
				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();

				long provenSize = provenQs.size();
				long impossibleSize = impossibleQs.size();
				for (int k = 0; k < negFeedbackPercentage.length; ++k) {
					List<Tuple> provenQsCpy = new ArrayList<Tuple>(provenQs);
					List<Tuple> impossibleQsCpy = new ArrayList<Tuple>(impossibleQs);

					PrintWriter pw = new PrintWriter(new File(
							feedbackFile + negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long negFeedbackSize = Math.round(provenSize * (negFeedbackPercentage[k]));
					long posFeedbackSize = Math.round(impossibleSize * (posFeedbackPercentage[k]));

					posFeedback.clear();
					negFeedback.clear();
					for (int i = 0; i < negFeedbackSize; ++i) {
						Tuple fTuple = provenQsCpy.remove(rand.nextInt(provenQsCpy.size()));
						negFeedback.add(fTuple);
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}

					rand = new Random(System.currentTimeMillis());
					for (int i = 0; i < posFeedbackSize; ++i) {
						Tuple fTuple = impossibleQsCpy.remove(rand.nextInt(impossibleQsCpy.size()));
						if (this.withProvenance) {
							for (Tuple t : this.getProvenance(fTuple)) {
								posFeedback.add(t);
							}
						} else {
							posFeedback.add(fTuple);
						}
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}
					pw.flush();
					pw.close();

					this.generateRevertedConstraints(Config.outDirName + File.separator + "reverted_cons_"
							+ negFeedbackPercentage[k] + "_" + posFeedbackPercentage[k] + ".txt", posFeedback);
				}
			} else {
				Set<Tuple> posFeedback = new HashSet<Tuple>();
				Set<Tuple> negFeedback = new HashSet<Tuple>();
				List<Tuple> remBaseQs = new ArrayList<Tuple>();
				remBaseQs.addAll(impossibleQs);
				remBaseQs.addAll(provenQs);

				long qSize = remBaseQs.size();
				for (int k = 0; k < feedbackPercentage.length; ++k) {
					List<Tuple> remBaseQsCpy = new ArrayList<Tuple>(remBaseQs);
					PrintWriter pw = new PrintWriter(new File(feedbackFile + feedbackPercentage[k] + ".mln"));
					Random rand = new Random(System.currentTimeMillis());

					long feedbackSize;

					posFeedback.clear();
					negFeedback.clear();
					feedbackSize = Math.round(qSize * (feedbackPercentage[k]));

					for (int i = 0; i < feedbackSize; ++i) {
						Tuple fTuple = remBaseQsCpy.remove(rand.nextInt(remBaseQsCpy.size()));
						if (!impossibleQs.contains(fTuple)) {
							negFeedback.add(fTuple);
						} else {
							if (this.withProvenance) {
								for (Tuple t : this.getProvenance(fTuple)) {
									posFeedback.add(t);
								}
							} else {
								posFeedback.add(fTuple);
							}
						}
					}
					for (Tuple t : negFeedback) {
						pw.println(infFeedbackWeight + " !" + t);
					}
					for (Tuple t : posFeedback) {
						pw.println(infFeedbackWeight + " " + t);
					}

					pw.flush();
					pw.close();

					this.generateRevertedConstraints(
							Config.outDirName + File.separator + "reverted_cons_" + feedbackPercentage[k] + ".txt",
							posFeedback);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateInfFeedbackInteractive(String feedbackFile, Set<Tuple> baseQueries,
			Set<Tuple> currentQueries, Set<Tuple> oracleQueries, Set<Tuple> negTuples, Set<Tuple> posTuples,
			int numIter) {
		try {
			if (areCurrentRelsOracle)
				throw new RuntimeException("Run base analysis first");

			if (negFeedbackPercentage.length != 0) {
				// Separate neg and pos feedback setting
				List<Tuple> provenQs = new ArrayList<Tuple>();
				List<Tuple> impossibleQs = new ArrayList<Tuple>();

				for (Tuple t : baseQueries) {
					boolean ifSurvive = oracleQueries.contains(t);
					if (ifSurvive) {
						impossibleQs.add(t);
					} else {
						if (currentQueries.contains(t)) {
							provenQs.add(t);
						}
					}
				}
				provenQs.removeAll(negTuples);
				impossibleQs.removeAll(posTuples);

				PrintWriter pw = new PrintWriter(new File(feedbackFile + numIter + ".mln"));
				Random rand = new Random(System.currentTimeMillis());
				long negFeedbackSize = Math.round(provenQs.size() * negFeedbackPercentage[numIter]);
				long posFeedbackSize = Math.round(impossibleQs.size() * posFeedbackPercentage[numIter]);
				for (int i = 0; i < negFeedbackSize; ++i) {
					Tuple fTuple = provenQs.get(rand.nextInt(provenQs.size()));
					negTuples.add(fTuple);
				}
				for (Tuple t : negTuples) {
					pw.println(infFeedbackWeight + " !" + t);
				}

				rand = new Random(System.currentTimeMillis());
				for (int i = 0; i < posFeedbackSize; ++i) {
					Tuple fTuple = impossibleQs.get(rand.nextInt(impossibleQs.size()));
					if (this.withProvenance) {
						for (Tuple t : this.getProvenance(fTuple)) {
							posTuples.add(t);

							/*
							 * The following way of getting provenance tuples is
							 * incorrect. It gets all the tuples in the
							 * provenance of the oracle instead of the base
							 * analysis. As a result, its already leading to a
							 * selection of the base tuples that should be
							 * derived and is some sense amounts to providing
							 * feedback at the intermediate tuple level.
							 */
							// Set<Tuple> projTuples = this.project(t);
							// for (Tuple t1 : projTuples) {
							// if (baseTuples.contains(t1)) {
							// pw.println(infFeedbackWeight + " " + t1);
							// }
							// }
						}
					} else {
						posTuples.add(fTuple);
					}
				}
				for (Tuple t : posTuples) {
					pw.println(infFeedbackWeight + " " + t);
				}
				pw.flush();
				pw.close();

				this.generateRevertedConstraints(
						Config.outDirName + File.separator + "reverted_cons_" + numIter + ".txt", posTuples);
			} else {
				Set<Tuple> remBaseQsSet = new HashSet<Tuple>(currentQueries);
				for (Tuple t : baseQueries) {
					boolean ifSurvive = oracleQueries.contains(t);
					if (ifSurvive) {
						remBaseQsSet.add(t);
					}
				}
				remBaseQsSet.removeAll(posTuples);
				remBaseQsSet.removeAll(negTuples);
				List<Tuple> remBaseQs = new ArrayList<Tuple>(remBaseQsSet);

				PrintWriter pw = new PrintWriter(new File(feedbackFile + numIter + ".mln"));
				Random rand = new Random(System.currentTimeMillis());

				long feedbackSize = Math.round(remBaseQs.size() * (feedbackPercentage[numIter]));

				for (int i = 0; i < feedbackSize; ++i) {
					Tuple fTuple = remBaseQs.remove(rand.nextInt(remBaseQs.size()));
					if (!oracleQueries.contains(fTuple)) {
						negTuples.add(fTuple);
					} else {
						if (this.withProvenance) {
							for (Tuple t : this.getProvenance(fTuple)) {
								posTuples.add(t);

								/*
								 * The following way of getting provenance
								 * tuples is incorrect. It gets all the tuples
								 * in the provenance of the oracle instead of
								 * the base analysis. As a result, its already
								 * leading to a selection of the base tuples
								 * that should be derived and is some sense
								 * amounts to providing feedback at the
								 * intermediate tuple level.
								 */
								// Set<Tuple> projTuples = this.project(t);
								// for (Tuple t1 : projTuples) {
								// if (baseTuples.contains(t1)) {
								// pw.println(infFeedbackWeight + " " + t1);
								// }
								// }
							}
						} else {
							posTuples.add(fTuple);
						}

					}
				}
				for (Tuple t : negTuples) {
					pw.println(infFeedbackWeight + " !" + t);
				}
				for (Tuple t : posTuples) {
					pw.println(infFeedbackWeight + " " + t);
				}

				pw.flush();
				pw.close();

				this.generateRevertedConstraints(
						Config.outDirName + File.separator + "reverted_cons_" + numIter + ".txt", posTuples);
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void generateRevertedConstraints(String constraintFile, Set<Tuple> queries) {
		/*
		 * try { MaxSatGenerator maxSat = createMaxSatGenerator(); PrintWriter
		 * pw = new PrintWriter(new File(constraintFile)); List<LookUpRule>
		 * rules = maxSat.getRules(); for (LookUpRule rule : rules) {
		 * Iterator<ConstraintItem> iter = rule.getAllConstrIterator(); while
		 * (iter.hasNext()) { this.printRevertedConstraint(pw, iter.next()); } }
		 * pw.flush(); pw.close(); } catch (FileNotFoundException e) { throw new
		 * RuntimeException(e); }
		 */
		try {
			if (revGen == null) {
				if (areCurrentRelsOracle)
					throw new RuntimeException("Run base analysis first");
				// MaxSatGenerator maxSat = createMaxSatGenerator();
				// List<LookUpRule> rules = maxSat.getRules();
				List<LookUpRule> rules = this.getRules();
				SimplePTHandler ptHandler = new SimplePTHandler(this.getAxiomTuples());
				ptHandler.init(rules);
				if (this.revMode.equals(RevMode.OR))
					revGen = new RevConstraintGenerator(ptHandler);
				else
					revGen = new AndRevConstraintGenerator(ptHandler);
				revGen.update(rules);
			}
			List<Tuple> axiomTuples = this.getAxiomTuples();
			for (Tuple t : axiomTuples) {
				revGen.getTupleIndex().getIndex(t);
			}

			PrintWriter pw = new PrintWriter(new File(constraintFile));
			for (int[] c : revGen.getReversedCnf(axiomTuples, queries)) {
				this.printRevertedConstraint(pw, c, revGen.getTupleIndex());
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected abstract List<Tuple> getAxiomTuples();

	protected void generateGroundedConstraints(String constraintFile) {
		try {
			PrintWriter pw = new PrintWriter(new File(constraintFile));
			List<LookUpRule> rules = this.getRules();
			for (LookUpRule rule : rules) {
				Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
				while (iter.hasNext()) {
					this.printConstraint(pw, iter.next());
				}
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void printConstraint(PrintWriter pw, ConstraintItem ci) {
		StringBuilder sb = new StringBuilder();
		sb.append(this.constraintWeight + ": ");

		// Order in which predicates appear in the ground clause should be same
		// as the order
		// in which they appear in the corresponding MLN rule in the mln file.
		// This is because
		// the MLN solver expects the predicates in the ground clauses to appear
		// in the same order.
		for (int i = 0; i < ci.getSubTuples().size(); ++i) {
			Tuple sub = ci.getSubTuples().get(i);
			Boolean sign = ci.getSubTuplesSign().get(i);
			if (sign) {
				sb.append("NOT ");
			}
			sb.append(sub.toString());
			sb.append(", ");
		}

		Tuple head = ci.getHeadTuple();
		Boolean headSign = ci.getHeadTupleSign();
		if (!headSign) {
			sb.append("NOT ");
		}
		sb.append(head.toString());

		pw.println(sb);
	}

	protected void generateNamedGroundedConstraints(String constraintFileName, String dictFileName) {
		try {
			PrintWriter constraintFile = new PrintWriter(new File(constraintFileName));
			PrintWriter dictFile = new PrintWriter(new File(dictFileName));
			List<LookUpRule> rules = this.getRules();
			for (int i = 0; i < rules.size(); i++) {
				LookUpRule rule = rules.get(i);
				String name = "R" + Integer.toString(i);
				dictFile.println(name + ": " + rule.toString());
				Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
				while (iter.hasNext()) {
					this.printNamedConstraint(constraintFile, name, iter.next());
				}
			}
			constraintFile.flush();
			constraintFile.close();
			dictFile.flush();
			dictFile.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected void printNamedConstraint(PrintWriter pw, String name, ConstraintItem ci) {
		StringBuilder sb = new StringBuilder();
		sb.append(name + ": ");

		// Order in which predicates appear in the ground clause should be same
		// as the order
		// in which they appear in the corresponding MLN rule in the mln file.
		// This is because
		// the MLN solver expects the predicates in the ground clauses to appear
		// in the same order.
		for (int i = 0; i < ci.getSubTuples().size(); ++i) {
			Tuple sub = ci.getSubTuples().get(i);
			Boolean sign = ci.getSubTuplesSign().get(i);
			if (sign) {
				sb.append("NOT ");
			}
			sb.append(sub.toString());
			sb.append(", ");
		}

		Tuple head = ci.getHeadTuple();
		Boolean headSign = ci.getHeadTupleSign();
		if (!headSign) {
			sb.append("NOT ");
		}
		sb.append(head.toString());

		pw.println(sb);
	}

	protected void printRevertedConstraint(PrintWriter pw, ConstraintItem ci) {
		Tuple head = ci.getHeadTuple();
		for (int i = 0; i < ci.getSubTuples().size(); ++i) {
			StringBuilder sb = new StringBuilder();
			sb.append(this.constraintWeight + ": ");
			sb.append("NOT ");
			sb.append(head.toString());

			Tuple sub = ci.getSubTuples().get(i);
			Boolean sign = ci.getSubTuplesSign().get(i);

			sb.append(", ");
			if (!sign) {
				sb.append("NOT ");
			}
			sb.append(sub.toString());
			pw.println(sb);
		}
	}

	protected void printRevertedConstraint(PrintWriter pw, int[] ci, TupleIndex ti) {
		StringBuilder sb = new StringBuilder();
		sb.append(this.constraintWeight + ": ");
		for (int i = 0; i < ci.length; ++i) {
			if (i != 0) {
				sb.append(", ");
			}
			if (ci[i] < 0) {
				sb.append("NOT ");
			}
			Tuple t = ti.getTuple(Math.abs(ci[i]));
			if (t == null) {
				sb.append("RevOR(" + Math.abs(ci[i]) + ")");
				revorDomain.add(Math.abs(ci[i]));
			} else {
				sb.append(ti.getTuple(Math.abs(ci[i])));
			}
		}
		pw.println(sb);
	}

	/*
	 * Get all the tuples involved in the derivation of given query tuple
	 * queryT.
	 */
	protected Set<Tuple> getProvenance(Tuple queryT) {
		List<LookUpRule> rules = this.getRules();
		Set<Tuple> provTuples = new HashSet<Tuple>();
		Set<ConstraintItem> constraints = new HashSet<ConstraintItem>();
		constraints = lookup(queryT, provTuples, rules);
		Set<ConstraintItem> currentConstraints = new HashSet<ConstraintItem>(constraints);
		Set<ConstraintItem> consToAdd = new HashSet<ConstraintItem>();
		boolean changed = false;
		do {
			changed = false;
			consToAdd.clear();
			for (ConstraintItem ci : currentConstraints) {
				for (Tuple t : ci.getSubTuples()) {
					Set<ConstraintItem> subCons = lookup(t, provTuples, rules);
					if (!subCons.isEmpty()) {
						changed = true;
						consToAdd.addAll(subCons);
					}
				}
			}
			currentConstraints.clear();
			currentConstraints.addAll(consToAdd);
			// currentConstraints.removeAll(constraints);
			constraints.addAll(consToAdd);
		} while (changed);
		return provTuples;
	}

	protected Set<ConstraintItem> lookup(Tuple t, Set<Tuple> provTuples, List<LookUpRule> rules) {
		Set<ConstraintItem> ret = new HashSet<ConstraintItem>();
		if (provTuples.contains(t))// Some optimization, we don't want to query
									// the tuple again
			return ret;
		else
			provTuples.add(t);
		for (LookUpRule rule : rules) {
			if (rule.match(t)) {
				List<ConstraintItem> items = rule.lookUp(t);
				if (items != null)
					ret.addAll(items);
			}
		}
		return ret;
	}

	private List<LookUpRule> ruleCache = null;

	protected List<LookUpRule> getRules() {
		if(ruleCache != null)
			return ruleCache;
		List<LookUpRule> rules = new ArrayList<LookUpRule>();
		rules = new ArrayList<LookUpRule>();
		for (String conFile : this.getConfigFiles()) {
			try {
				Scanner sc = new Scanner(new File(conFile));
				while (sc.hasNext()) {
					String line = sc.nextLine().trim();
					if (!line.equals("")) {
						LookUpRule rule = new LookUpRule(line);
						rules.add(rule);
					}
				}
				sc.close();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		ruleCache = rules;
		return rules;
	}

	protected Set<Tuple> generateFinalQueries(String queryFile) {
		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			PrintWriter pw = new PrintWriter(new File(queryFile));
			String queryRel = getQueryRelation();
			ProgramRel pr = (ProgramRel) ClassicProject.g().getTrgt(queryRel);
			pr.load();
			for (int[] indices : pr.getAryNIntTuples()) {
				Tuple t = new Tuple(pr, indices);
				pw.println(t);
				queries.add(t);
			}
			pw.flush();
			pw.close();
			return queries;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	protected Set<Tuple> readOracleQueries(String oraclePath) {
		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(oraclePath)));
			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().equals("")) {
					continue;
				}
				if (line.startsWith("//")) {
					continue;
				}
				if (line.startsWith("!")) {
					br.close();
					throw new RuntimeException("Result produced by the MLN engine should not contain negated tuples");
				} else {
					Tuple current = new Tuple(line);
					queries.add(current);
				}
			}
			br.close();
			return queries;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected void readSettings() {
		String modeStr = System.getProperty("chord.mln.mode", "oracle");
		if (modeStr.equals("oracle")) {
			this.mode = Mode.ORACLE;
		} else if (modeStr.equals("learn")) {
			this.mode = Mode.LEARN;
		} else if (modeStr.equals("batch")) {
			this.mode = Mode.BATCH;
		} else if (modeStr.equals("inter")) {
			this.mode = Mode.INTER;
		} else if (modeStr.equalsIgnoreCase("problem")) {
			this.mode = Mode.PROBLEM_GEN;
		} else {
			throw new RuntimeException("Unknown mode");
		}

		String revModeStr = System.getProperty("chord.mln.revMode", "or");

		if (revModeStr.equals("or")) {
			this.revMode = RevMode.OR;
		} else if (revModeStr.equals("and")) {
			this.revMode = RevMode.AND;
		} else {
			throw new RuntimeException("Unknown rev mode");
		}

		this.evidenceWeight = Integer.getInteger("chord.mln.edbweight", -1);
		this.infFeedbackWeight = System.getProperty("chord.mln.infWeight", "1");
		this.withProvenance = Boolean.getBoolean("chord.mln.withProvenance");

		ArrayList<Double> posFeedbackPercentageList = new ArrayList<Double>();
		ArrayList<Double> negFeedbackPercentageList = new ArrayList<Double>();
		for (String str : System.getProperty("chord.mln.posRatio", "").split(",")) {
			if (!str.equalsIgnoreCase("")) {
				posFeedbackPercentageList.add(Double.parseDouble(str));
			}
		}
		for (String str : System.getProperty("chord.mln.negRatio", "").split(",")) {
			if (!str.equalsIgnoreCase("")) {
				negFeedbackPercentageList.add(Double.parseDouble(str));
			}
		}
		if (posFeedbackPercentageList.size() != negFeedbackPercentageList.size()) {
			throw new RuntimeException("Mismatching number of entries for positive and negative ratios");
		}
		this.posFeedbackPercentage = new Double[posFeedbackPercentageList.size()];
		this.negFeedbackPercentage = new Double[negFeedbackPercentageList.size()];
		posFeedbackPercentageList.toArray(this.posFeedbackPercentage);
		negFeedbackPercentageList.toArray(this.negFeedbackPercentage);

		Arrays.sort(this.posFeedbackPercentage);
		Arrays.sort(this.negFeedbackPercentage);

		ArrayList<Double> feedbackPercentageList = new ArrayList<Double>();
		for (String str : System.getProperty("chord.mln.ratio", "").split(",")) {
			if (!str.equalsIgnoreCase("")) {
				feedbackPercentageList.add(Double.parseDouble(str));
			}
		}

		if (feedbackPercentageList.size() != 0 && posFeedbackPercentageList.size() != 0) {
			throw new RuntimeException(
					"Choose between uniform feedback vs separate feedback for positive & negative queries.");
		}

		this.feedbackPercentage = new Double[feedbackPercentageList.size()];
		feedbackPercentageList.toArray(feedbackPercentage);
		Arrays.sort(feedbackPercentage);

		this.constraintWeight = System.getProperty("chord.mln.consWeight", "1");
		this.oraclePath = System.getProperty("chord.mln.oraclePath");
		this.classifierPath = System.getProperty("chord.ursa.classifierFile");
	}
	
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath){}
	
	protected void generateAppScope(String fileName){}

	@Override
	public void run() {
		ProgramRel relMV = (ProgramRel) ClassicProject.g().getTrgt("MV");
		ClassicProject.g().runTask(relMV);
		relMV.load();
		Tuple.relMV = relMV;
		
		revorDomain = new HashSet<Integer>();
		this.readSettings();
		if (this.mode == Mode.ORACLE) runningBase = true;
		this.genTasks();
		this.runBaseCase();
		if (this.mode == Mode.ORACLE) runningBase = false;
		this.genTasks();
		
		if(this.classifierPath != null){
			Set<ConstraintItem> provenance = new HashSet<ConstraintItem>();
			List<LookUpRule> rules = this.getRules();
			for(LookUpRule r : rules){
				Iterator<ConstraintItem> iter = r.getAllConstrIterator();
				while(iter.hasNext()){
					provenance.add(iter.next());
				}
			}
			Set<Tuple> tuples = new HashSet<Tuple>();
			tuples.addAll(this.loadTuples(false));
			tuples.addAll(this.loadTuples(true));
			this.predict(tuples, provenance, this.classifierPath);
		}
		
		Set<Tuple> base_queries = this.generateFinalQueries(Config.outDirName + File.separator + "base_queries.txt");
		Set<Tuple> oracle_queries = null;

		if (this.mode == Mode.ORACLE || this.mode == Mode.LEARN) {
			if (this.mode == Mode.LEARN) {
				this.generateProblem(Config.outDirName + File.separator + "base_problem.edb");
				this.generateScope(Config.outDirName + File.separator + "base_scope.edb");
				this.generateAppScope(Config.outDirName+File.separator+"app_base_scope.edb");
				this.generateScopeVerbose(Config.outDirName + File.separator + "base_scope_verbose.edb");
			}
			//this.generateGroundedConstraints(Config.outDirName + File.separator + "cons_all.txt");
			//this.generateNamedGroundedConstraints(Config.outDirName + File.separator + "named_cons_all.txt",
					//Config.outDirName + File.separator + "rule_dict.txt");
			Set<Tuple> baseTuples = this.loadTuples(true);
			Set<Tuple> baseTuplesEDB = this.loadTuples(false);
			this.runOracle();
			oracle_queries = this.generateFinalQueries(Config.outDirName + File.separator + "oracle_queries.txt");
			if (this.mode == Mode.LEARN) {
				this.generateProblem(Config.outDirName + File.separator + "oracle_problem.edb");
				this.generateScope(Config.outDirName + File.separator + "oracle_scope.edb");
				Set<Tuple> oracleTuples = this.loadProjectedTuples(true);
				Set<Tuple> oracleTuplesEDB = this.loadProjectedTuples(false);
				this.generateFeedback(Config.outDirName + File.separator + "feedback.edb", baseTuples, oracleTuples);
				this.generateSimpleFeedback(Config.outDirName + File.separator + "simpleFeedback_", base_queries,
						oracle_queries);
				this.generateFeedback(Config.outDirName + File.separator + "problem_projected.edb", baseTuplesEDB,
						oracleTuplesEDB);
			}
		} else {
			this.generateGroundedConstraints(Config.outDirName + File.separator + "cons_all.txt");
			this.generateNamedGroundedConstraints(Config.outDirName + File.separator + "named_cons_all.txt",
					Config.outDirName + File.separator + "rule_dict.txt");
			this.generateRevertedConstraints(Config.outDirName + File.separator + "reverted_cons_all.txt",
					base_queries);

			// TODO: Better design. Currently. generateRevertedConstraints with
			// all queries MUST be called before
			// generateProblem otherwise rev_or domain will not be constructed.
			this.generateProblem(Config.outDirName + File.separator + "problem.edb");
			this.generateScope(Config.outDirName + File.separator + "scope.edb");

			oracle_queries = readOracleQueries(oraclePath);
			HashSet<String> trackedRels = new HashSet<String>();
			trackedRels.add(getQueryRelation());
			MLNRunner runner = new MLNRunner(trackedRels);

			int numIters = this.negFeedbackPercentage.length == 0 ? this.feedbackPercentage.length
					: this.negFeedbackPercentage.length;
			System.setProperty("chord.mln.edb", Config.outDirName + File.separator + "problem.edb");
			System.setProperty("chord.mln.feedback", Config.outDirName + File.separator + "feedback.edb");
			System.setProperty("chord.mln.loadgc", Config.outDirName + File.separator + "cons_all.txt");

			String feedbackFileName = Config.outDirName + File.separator + "infFeedback_";
			String revConsFileName = Config.outDirName + File.separator + "reverted_cons_";

			if (this.mode == Mode.PROBLEM_GEN) {
				String userName = System.getProperty("chord.mln.user");
				if (userName != null)
					this.generateUserStudyFeedback(feedbackFileName);
				else
					this.generateInfFeedback(feedbackFileName, base_queries, oracle_queries);
			} else if (this.mode == Mode.BATCH) {
				String userName = System.getProperty("chord.mln.user");
				if (userName != null)
					this.generateUserStudyFeedback(feedbackFileName);
				else
					this.generateInfFeedback(feedbackFileName, base_queries, oracle_queries);
				for (int iterationCount = 0; iterationCount < numIters; ++iterationCount) {
					String trailingStr;
					if (feedbackPercentage.length != 0) {
						trailingStr = "" + feedbackPercentage[iterationCount];
					} else {
						trailingStr = negFeedbackPercentage[iterationCount] + "_"
								+ posFeedbackPercentage[iterationCount];
					}
					System.setProperty("chord.mln.out",
							Config.outDirName + File.separator + "mln_" + trailingStr + ".result");
					System.setProperty("chord.mln.log",
							Config.outDirName + File.separator + "mln_" + trailingStr + ".log");
					System.setProperty("chord.mln.memStatsPath",
							Config.outDirName + File.separator + "memStats_" + trailingStr + ".txt");
					System.setProperty("chord.mln.refined", feedbackFileName + trailingStr + ".mln");
					System.setProperty("chord.mln.loadRev", revConsFileName + trailingStr + ".txt");

					Map<String, Set<Tuple>> inferredTuples = null;
					try {
						inferredTuples = runner.runMLN();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			} else { // this.mode == Mode.INTER
				Set<Tuple> negTuples = new HashSet<Tuple>();
				Set<Tuple> posTuples = new HashSet<Tuple>();
				Set<Tuple> relInferredTuples = base_queries;
				for (int iterationCount = 0; iterationCount < numIters; ++iterationCount) {
					System.setProperty("chord.mln.out",
							Config.outDirName + File.separator + "mln_" + iterationCount + ".result");
					System.setProperty("chord.mln.log",
							Config.outDirName + File.separator + "mln_" + iterationCount + ".log");
					System.setProperty("chord.mln.memStatsPath",
							Config.outDirName + File.separator + "memStats_" + iterationCount + ".txt");
					System.setProperty("chord.mln.refined", feedbackFileName + iterationCount + ".mln");
					System.setProperty("chord.mln.loadRev", revConsFileName + iterationCount + ".txt");

					this.generateInfFeedbackInteractive(feedbackFileName, base_queries, relInferredTuples,
							oracle_queries, negTuples, posTuples, iterationCount);

					Map<String, Set<Tuple>> inferredTuples = null;
					try {
						inferredTuples = runner.runMLN();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}

					relInferredTuples = inferredTuples.get(getQueryRelation());
					if (relInferredTuples == null) {
						relInferredTuples = new HashSet<Tuple>();
					}
				}
			}
		}
	}

	protected boolean isTupleOfInterest(Tuple t){
	    return true;
	}
  
	protected void generateFeedbackVerbose(String feedbackFile, Set<Tuple> baseTuples, Set<Tuple> oracleTuples) {
		try {
		     PrintWriter pw = new PrintWriter(new File(feedbackFile));
		     pw.println("// Labels");
		     int qtsNum = this.getQueryTupleNum();
		     for (Tuple t : baseTuples) {
				if(!this.isTupleOfInterest(t))
		        	continue;
		            boolean ifSurvive = oracleTuples.contains(t);
		            if (this.evidenceWeight == -1)
		                if (ifSurvive)
			                pw.println(t);
			                else
		                    pw.println("!" + t);
		            else if (this.evidenceWeight == -2)
		                if (ifSurvive)
			                    pw.println((qtsNum + 1) + " " + t);
			                else
		                    pw.println((0 - qtsNum - 1) + " " + t);
		            else if (ifSurvive)
		                pw.println(this.evidenceWeight + " " + t);
		            else
		                pw.println((0 - this.evidenceWeight) + " " + t);
		            int indices[] = t.getIndices();
		            for(int n = 0 ; n < indices.length; n++){
		                pw.println(t.getDomains()[n].toUniqueString(indices[n]));
		            }
		            pw.println();
		        }
		        pw.flush();
		        pw.close();
		    } catch (FileNotFoundException e) {
		        throw new RuntimeException(e);
		    }
		}

}
