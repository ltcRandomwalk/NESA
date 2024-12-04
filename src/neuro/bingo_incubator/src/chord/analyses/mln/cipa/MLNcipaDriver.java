package chord.analyses.mln.cipa;

import static chord.util.RelUtil.pRel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.invk.DomI;
import chord.analyses.mln.ConstraintItem;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.analyses.provenance.kobj.KOBJRefiner;
import chord.analyses.ursa.classifier.Classifier;
import chord.analyses.ursa.classifier.cipa.DynamicAnalysisClassifier;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.ArraySet;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Quad;

/**
 * -Dchord.mln.client=cipa_0cfa -Dchord.ursa.classifier=<dynamic/none>
 * 
 * @author xin
 *
 */
@Chord(name = "cipa-mln-gen", consumes = { "checkExcludedV", "checkExcludedH", "checkExcludedT", "checkExcludedI" })
public class MLNcipaDriver extends MLNAnalysisDriver {
	private int client;
	private boolean nonPLDIOracle; // use kobj as the oracle
	private Integer nonPLDIK;
	private chord.analyses.provenance.kobj.KOBJRefiner objRefinerTask;
	private static Set<String> relsWithLabels;
	private static Set<String> oracleRels;
	private String classifierKind;
	private Classifier classifier;

	static {
		relsWithLabels = new ArraySet<String>();
		relsWithLabels.add("VH");
		relsWithLabels.add("FH");
		relsWithLabels.add("HFH");
		relsWithLabels.add("reachableI");
		relsWithLabels.add("reachableM");
		relsWithLabels.add("IM");
		relsWithLabels.add("MM");

		relsWithLabels.add("ptsVH");

		oracleRels = new ArraySet<String>();
		oracleRels.add("CVC");
		oracleRels.add("FC");
		oracleRels.add("CFC");
		oracleRels.add("reachableCI");
		oracleRels.add("reachableCM");
		oracleRels.add("CICM");
		oracleRels.add("CMCM");
		oracleRels.add("ptsVH");
	}

	@Override
	protected Set<String> getDerivedRelations() {
		Set<String> ret = new HashSet<String>();

		// cipa-0cfa-dlog
		ret.add("IHM");
		ret.add("VV");
		ret.add("specIMV");
		ret.add("objValAsgnInst");
		ret.add("objVarAsgnInst");
		ret.add("getInstFldInst");
		ret.add("putInstFldInst");
		ret.add("getStatFldInst");
		ret.add("putStatFldInst");
		ret.add("reachableT");
		// ret.add("VHfilter"); move to input due to the wild card rule
		ret.add("VH");
		ret.add("FH");
		ret.add("HFH");
		ret.add("rootM");
		ret.add("reachableI");
		ret.add("reachableM");
		ret.add("IM");
		ret.add("MM");
		ret.add("reachableH");
		ret.add("reachableV");
		ret.add("ptsVH");

		if (client == 3) {
		} else
			this.throwUnknownClientException();

		return ret;
	}

	@Override
	protected Set<Tuple> loadProjectedTuples(boolean useDerivedRels) {
		if (!useDerivedRels) {
			System.out.println("Warning: not projecting edb tuples.");
			return new ArraySet<Tuple>();
		}
		Set<Tuple> ret = new HashSet<Tuple>();
		Set<String> rels = oracleRels;
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

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();

		// domains from cipa-0cfa-dlog
		ret.add("T");
		ret.add("F");
		ret.add("M");
		ret.add("I");
		ret.add("H");
		ret.add("V");
		ret.add("Z");

		// domains from the client
		if (client == 3) {
		} else
			this.throwUnknownClientException();

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		// input relations from cipa-0cfa-dlog
		ret.add("VT");
		ret.add("HT");
		ret.add("cha");
		ret.add("sub");
		ret.add("MmethArg");
		ret.add("MmethRet");
		ret.add("IinvkArg0");
		ret.add("IinvkArg");
		ret.add("IinvkRet");
		ret.add("MI");
		ret.add("statIM");
		ret.add("specIM");
		ret.add("virtIM");
		ret.add("MobjValAsgnInst");
		ret.add("MobjVarAsgnInst");
		ret.add("MgetInstFldInst");
		ret.add("MputInstFldInst");
		ret.add("MgetStatFldInst");
		ret.add("MputStatFldInst");
		ret.add("clsForNameIT");
		ret.add("objNewInstIH");
		ret.add("objNewInstIM");
		ret.add("conNewInstIH");
		ret.add("conNewInstIM");
		ret.add("aryNewInstIH");
		ret.add("classT");
		ret.add("staticTM");
		ret.add("staticTF");
		ret.add("clinitTM");

		// hack, make constant relation VHfilter as input
		ret.add("VHfilter");

		// input relations from the client
		if (client == 3) {
			ret.add("checkExcludedH");
			ret.add("checkExcludedV");
			ret.add("MH");
			ret.add("MV");
		} else
			this.throwUnknownClientException();

		return ret;
	}

	@Override
	protected String getQueryRelation() {
		// if (client == 1)
		// return "unsafeDowncast";
		// if (client == 0)
		// return "polySite";
		if (client == 3)
			return "ptsVH";
		// if (client == 4)
		// return "flowHV";
		this.throwUnknownClientException();
		return null;

	}

	private void generateSearchSpace() {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "ursa_search_space.txt"));
			// checkExcluds
			ProgramRel relExV = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedV");
			relExV.load();

			ProgramRel relExI = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedI");
			relExI.load();

			ProgramRel relExH = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedH");
			relExH.load();

			ProgramRel relExT = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedT");
			relExT.load();

			// VH
			ProgramRel relVH = (ProgramRel) ClassicProject.g().getTrgt("VH");
			relVH.load();

			for (int ts[] : relVH.getAryNIntTuples()) {
				if (!relExV.contains(ts[0])) {
					Tuple t = new Tuple(relVH, ts);
					pw.println(t.toString());
				}
			}

			// HFH
			/*
			 * ProgramRel relHFH =
			 * (ProgramRel)ClassicProject.g().getTrgt("HFH"); relHFH.load();
			 * 
			 * for(int ts[] : relHFH.getAryNIntTuples()){
			 * if(!relExH.contains(ts[0])){ Tuple t = new Tuple(relHFH,ts);
			 * pw.println(t.toString()); } }
			 * 
			 * DomF domF = (DomF)ClassicProject.g().getTrgt("F"); // FH
			 * ProgramRel relFH = (ProgramRel)ClassicProject.g().getTrgt("FH");
			 * relFH.load();
			 * 
			 * for(int ts[] : relFH.getAryNIntTuples()){ jq_Field f =
			 * domF.get(ts[0]); jq_Class cl = f.getDeclaringClass();
			 * if(!relExT.contains(cl)){ Tuple t = new Tuple(relFH,ts);
			 * pw.println(t.toString()); } }
			 */

			// IM
			ProgramRel relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
			relIM.load();

			for (int ts[] : relIM.getAryNIntTuples()) {
				if (!relExI.contains(ts[0])) {
					Tuple t = new Tuple(relIM, ts);
					pw.println(t.toString());
				}
			}

			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Hack: here, we replace ptsVH with VH, HFH, FH, IM. It might affect
	 */
	@Override
	protected Set<Tuple> generateFinalQueries(String queryFile) {
		this.generateSearchSpace();
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		boolean usePtsVH = true;

		try {
			Set<Tuple> queries = new HashSet<Tuple>();
			PrintWriter pw = new PrintWriter(new File(queryFile));

			if (usePtsVH) {
				// ptsVH
//				ProgramRel ptsVH = (ProgramRel) ClassicProject.g().getTrgt("ptsVH");
//				ptsVH.load();
//				for (int[] indices : ptsVH.getAryNIntTuples()) {
//					Quad h = (Quad) domH.get(indices[1]);
//					if (h.getOperator() instanceof New) {
//						String cl = New.getType(h).getType().toString();
//						if (cl.equals("java.lang.StringBuilder") || cl.equals("java.lang.StringBuffer")) {
//							continue;
//						}
//					}
//					Tuple t = new Tuple(ptsVH, indices);
//					pw.println(t);
//					queries.add(t);
//				}

//				// let's exclude return values from HashpMap methods for now.
//				DomI domI = (DomI) ClassicProject.g().getTrgt("I");
//				DomV domV = (DomV) ClassicProject.g().getTrgt("V");
//				Set<Integer> mapVs = new HashSet<Integer>();
//				for(Quad q : domI){
//				        jq_Method m = Invoke.getMethod(q).getMethod();
//				        if(m.getDeclaringClass().toString().equals("java.util.HashMap")){
//				                RegisterOperand ro = Invoke.getDest(q);
//				                if(ro != null && ro.getType().isReferenceType())
//				                        mapVs.add(domV.indexOf(ro.getRegister()));
//				        }
//				}
				
				
				// VH
				{
					ProgramRel checkExcludedV = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedV");
					ProgramRel checkExcludedH = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedH");
					checkExcludedV.load();
					checkExcludedH.load();

					ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
					vh.load();
					for (int[] indices : vh.getAryNIntTuples()) {
						Quad h = (Quad) domH.get(indices[1]);
						if (checkExcludedV.contains(indices[0]) || checkExcludedH.contains(indices[1]))
							continue;
						if (h.getOperator() instanceof New) {
							String cl = New.getType(h).getType().toString();
							if (cl.equals("java.lang.StringBuilder") || cl.equals("java.lang.StringBuffer")) {
								continue;
							}
						}
						Tuple t = new Tuple(vh, indices);
						pw.println(t);
						queries.add(t);
					}
				 }
				
				// IM
				{
					ProgramRel checkExcludedI = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedI");
					checkExcludedI.load();
					ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
					im.load();
					for (int[] indices : im.getAryNIntTuples()) {
						if(checkExcludedI.contains(indices[0]))
							continue;
						Tuple t = new Tuple(im, indices);
						pw.println(t);
						queries.add(t);
					}

				}
				
			} else {

				// VH
				{
					ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
					vh.load();
					for (int[] indices : vh.getAryNIntTuples()) {
						Quad h = (Quad) domH.get(indices[1]);
						if (h.getOperator() instanceof New) {
							String cl = New.getType(h).getType().toString();
							if (cl.equals("java.lang.StringBuilder") || cl.equals("java.lang.StringBuffer")) {
								continue;
							}
						}
						Tuple t = new Tuple(vh, indices);
						pw.println(t);
						queries.add(t);
					}
				}

				// FH
				{
					ProgramRel fh = (ProgramRel) ClassicProject.g().getTrgt("FH");
					fh.load();
					for (int[] indices : fh.getAryNIntTuples()) {
						Tuple t = new Tuple(fh, indices);
						pw.println(t);
						queries.add(t);
					}

				}

				// HFH
				{
					ProgramRel hfh = (ProgramRel) ClassicProject.g().getTrgt("HFH");
					hfh.load();
					for (int[] indices : hfh.getAryNIntTuples()) {
						jq_Field f = domF.get(indices[1]);
						if (f != null)
							if (f.getDeclaringClass().toString().equals("java.lang.Throwable")) {
								continue;
							}
						Tuple t = new Tuple(hfh, indices);
						pw.println(t);
						queries.add(t);
					}

				}

				// IM
				{
					ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
					im.load();
					for (int[] indices : im.getAryNIntTuples()) {
						Tuple t = new Tuple(im, indices);
						pw.println(t);
						queries.add(t);
					}

				}
			}

			pw.flush();
			pw.close();
			return queries;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	protected String[] getConfigFiles() {

		// String chordMain = System.getenv("CHORD_MAIN");

		String chordIncubator = System.getenv("CHORD_INCUBATOR");
		String cipaConfig = chordIncubator + File.separator + "src/chord/analyses/mln/cipa/cipa-0cfa-dlog_XZ89_.config";
		String ptsConfig = chordIncubator + File.separator + "src/chord/analyses/mln/cipa/cipa-pts-dlog_XZ89_.config";

		String[] configFiles = new String[] { cipaConfig, ptsConfig };
		return configFiles;

	}

	@Override
	protected void genTasks() {
		tasks = new ArrayList<ITask>();

		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog_XZ89_"));

		if (client == 3)
			tasks.add(ClassicProject.g().getTask("cipa-pts-dlog_XZ89_"));
		else
			this.throwUnknownClientException();
	}

	private String getClient() {
		String clientStr = null;
		// if (this.client == 1)
		// clientStr = "downcast";
		// else if (this.client == 0)
		// clientStr = "polysite";
		// else
		if (this.client == 3)
			clientStr = "pts";
		// else if (this.client == 4)
		// clientStr = "infoflow";
		else
			this.throwUnknownClientException();
		return clientStr;
	}

	private void runKObj(int k) {
		System.setProperty("chord.ctxt.kind", "co");
		System.setProperty("chord.kobj.k", this.nonPLDIK.toString());
		ClassicProject.g().runTask("cipa-0cfa-dlog");
		ClassicProject.g().runTask("ctxts-java");
		ClassicProject.g().runTask("argCopy-dlog");
		ClassicProject.g().runTask("cspa-kobj-dlog");
		ClassicProject.g().runTask("pro-pts-dlog");
	}

	/**
	 * Invoke kobj-refiner/kobj/kcfa to get the result.
	 */
	@Override
	protected void runOracle() {
		String clientStr = getClient();
		if (this.nonPLDIOracle) {
			this.runKObj(this.nonPLDIK);
		} else {
			System.setProperty("chord.provenance.client", clientStr);
			System.setProperty("chord.provenance.obj2", "false");
			System.setProperty("chord.provenance.queryOption", "all");
			System.setProperty("chord.provenance.heap", "true");
			System.setProperty("chord.provenance.mono", "true");
			System.setProperty("chord.provenance.boolDomain", "true");
			System.setProperty("chord.provenance.queryWeight", "0");
			System.setProperty("chord.provenance.invkK", "10");
			System.setProperty("chord.provenance.allocK", "10");
			objRefinerTask = (KOBJRefiner) ClassicProject.g().getTask("kobj-refiner");
			ClassicProject.g().runTask(objRefinerTask);
		}
		areCurrentRelsOracle = true;
	}

	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase() {

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}

		areCurrentRelsOracle = false;
	}

	private Quad getH(Ctxt c) {
		if (c.length() == 0)
			return null;
		return c.get(0);
	}

	@Override
	protected Set<Tuple> project(Tuple t) {
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");

		// manual projection

		Set<Tuple> ret = new ArraySet<Tuple>();

		// CVC -> VH
		if (t.getRelName().equals("CVC")) {
			Ctxt c2 = (Ctxt) t.getValue(2);
			Quad h = getH(c2);
			ProgramRel vh = (ProgramRel) ClassicProject.g().getTrgt("VH");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[1];
			indicies[1] = domH.indexOf(h);
			Tuple r = new Tuple(vh, indicies); // be careful here, the projected
												// tuple might or might not be
												// contained in the actual
												// relation.
			ret.add(r);
		} else
		// FC -> FH
		if (t.getRelName().equals("FC")) {
			Ctxt c = (Ctxt) t.getValue(1);
			Quad h = getH(c);
			ProgramRel fh = (ProgramRel) ClassicProject.g().getTrgt("FH");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[0];
			indicies[1] = domH.indexOf(h);
			Tuple r = new Tuple(fh, indicies);
			ret.add(r);
		} else
		// CFC - > HFH
		if (t.getRelName().equals("CFC")) {
			Ctxt c1 = (Ctxt) t.getValue(0);
			Ctxt c2 = (Ctxt) t.getValue(2);
			ProgramRel fhf = (ProgramRel) ClassicProject.g().getTrgt("HFH");
			int indicies[] = new int[3];
			indicies[0] = domH.indexOf(getH(c1));
			indicies[1] = t.getIndices()[1];
			indicies[2] = domH.indexOf(getH(c2));
			Tuple r = new Tuple(fhf, indicies);
			ret.add(r);
		} else
		// reachableCI -> reachableI
		if (t.getRelName().equals("reachableCI")) {
			ProgramRel reachableI = (ProgramRel) ClassicProject.g().getTrgt("reachableI");
			int indicies[] = new int[1];
			indicies[0] = t.getIndices()[1];
			Tuple r = new Tuple(reachableI, indicies);
			ret.add(r);
		} else
		// reachableCM -> reachableM
		if (t.getRelName().equals("reachableCM")) {
			ProgramRel reachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
			int indicies[] = new int[1];
			indicies[0] = t.getIndices()[1];
			Tuple r = new Tuple(reachableM, indicies);
			ret.add(r);
		} else
		// CICM -> IM
		if (t.getRelName().equals("CICM")) {
			ProgramRel im = (ProgramRel) ClassicProject.g().getTrgt("IM");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[1];
			indicies[1] = t.getIndices()[3];
			Tuple r = new Tuple(im, indicies);
			ret.add(r);
		}
		// CMCM -> MM
		else if (t.getRelName().equals("CMCM")) {
			ProgramRel mm = (ProgramRel) ClassicProject.g().getTrgt("MM");
			int indicies[] = new int[2];
			indicies[0] = t.getIndices()[1];
			indicies[1] = t.getIndices()[3];
			Tuple r = new Tuple(mm, indicies);
			ret.add(r);
		} else
			ret.add(t);
		return ret;
	}

	@Override
	protected void readSettings() {
		super.readSettings();
		String clientStr = System.getProperty("chord.mln.client", "pts");
		if (clientStr.equals("downcast")) {
			this.client = 1;
		} else if (clientStr.equals("polysite")) {
			this.client = 0;
		} else if (clientStr.equals("pts")) {
			this.client = 3;
		} else if (clientStr.equals("infoflow")) {
			this.client = 4;
		} else {
			this.throwUnknownClientException();
		}

		this.classifierKind = System.getProperty("chord.ursa.classifier", "dynamic");
		this.nonPLDIOracle = Boolean.getBoolean("chord.mln.nonpldi");
		this.nonPLDIK = Integer.getInteger("chord.mln.nonpldiK", 2);

		System.setProperty("chord.ctxt.kind", "co");
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("rootM"), new int[] { 0 }));
		axiomTuples.add(new Tuple(pRel("reachableM"), new int[] { 0 }));
		return axiomTuples;
	}

	// manual labels
	private boolean ifFollowDynamicJspider(Tuple t){
		if(!t.getRelName().equals("IM") && !t.getRelName().equals("VH"))
			return false;
		Dom dom1 = t.getDomains()[0];
		Dom dom2 = t.getDomains()[1];
		int indices[] = t.getIndices();
		String str1 = dom1.toUniqueString(indices[0]);
		String str2 = dom2.toUniqueString(indices[1]);
		// IMs
		if (t.getRelName().equals("IM")) {
			// IM(6394,2280)
			if(str1.equals("3!registerEvent:(Ljava/net/URL;Lnet/javacoding/jspider/core/event/CoreEvent;)V@net.javacoding.jspider.core.impl.AgentImpl")
					&& str2.equals("accept:(Ljava/net/URL;Lnet/javacoding/jspider/core/event/CoreEventVisitor;)V@net.javacoding.jspider.core.event.impl.URLSpideredErrorEvent"))
				return false;
		}
		// VH
		if (t.getRelName().equals("VH")){
			// VH(17206,1660)
			if(str1.equals("T4!getConfiguration:()Lnet/javacoding/jspider/core/util/config/JSpiderConfiguration;@net.javacoding.jspider.core.util.config.ConfigurationFactory")
					&& str2.equals("6!getConfiguration:()Lnet/javacoding/jspider/core/util/config/JSpiderConfiguration;@net.javacoding.jspider.core.util.config.ConfigurationFactory")){
				return false;
			}
		}
		return true;	
	}
	
	private boolean ifFollowDynamicHedc(Tuple t){
		if(!t.getRelName().equals("IM") && !t.getRelName().equals("VH"))
			return false;
		Dom dom1 = t.getDomains()[0];
		Dom dom2 = t.getDomains()[1];
		int indices[] = t.getIndices();
		String str1 = dom1.toUniqueString(indices[0]);
		String str2 = dom2.toUniqueString(indices[1]);
			// IMs
		if (t.getRelName().equals("IM")) {
			// IM(1089.*)
			if(str1.equals("10!match:(Lhedc/regexp/State;)Z@hedc.regexp.Regexp"))
				return false;
		}
		// VH
		if (t.getRelName().equals("VH")){
			//VH(1567,106)
			if(str1.equals("T25!makeTasks:(Ljava/util/Hashtable;Ljava/util/Date;Lhedc/MetaSearchRequest;)Ljava/util/List;@hedc.TaskFactory") &&
					str2.equals("33!<clinit>:()V@hedc.TaskFactory"))
				return false;
		}
		return true;	
	
	}
	
	private boolean ifFollowDynamicFtp(Tuple t) {
		if(!t.getRelName().equals("IM") && !t.getRelName().equals("VH"))
			return false;
		Dom dom1 = t.getDomains()[0];
		Dom dom2 = t.getDomains()[1];
		int indices[] = t.getIndices();
		String str1 = dom1.toUniqueString(indices[0]);
		String str2 = dom2.toUniqueString(indices[1]);
		// IMs
		if (t.getRelName().equals("IM")) {
			Quad qi = (Quad) t.getValue(0);
			jq_Method m = (jq_Method) t.getValue(1);
			// IM(15234.*)
			if (str1.equals(
					"22!service:(Lorg/apache/ftpserver/FtpRequestImpl;Lorg/apache/ftpserver/FtpWriter;)V@org.apache.ftpserver.RequestHandler"))
				return false;
			// IM(14581,*), actually any invocation related to
			// org.apache.ftpserver.ftplet.Configuration
			if (Invoke.getMethod(qi).getMethod().getDeclaringClass().toString()
					.equals("org.apache.ftpserver.ftplet.Configuration"))
				return false;
			// IM(16759,4642), actullay any method related to LogFactory
			if (m.getDeclaringClass().toString().equals("org.apache.commons.logging.LogFactory"))
				return false;
			// IM(15224,.*)
			if (str1.equals("22!service:(Lorg/apache/ftpserver/FtpRequestImpl;Lorg/apache/ftpserver/FtpWriter;)V@org.apache.ftpserver.RequestHandler"))
				return false;
		}
		// VH
		if (t.getRelName().equals("VH")) {
			// VH(33013,3054)
			if (str1.equals("T1!getUser:()Lorg/apache/ftpserver/ftplet/User;@org.apache.ftpserver.FtpRequestImpl")
					&& str2.equals("1!reinitialize:()V@org.apache.ftpserver.FtpRequestImpl"))
				return false;
		}
		return true;
	}
	
	private boolean ifFollowDynamicWeblech(Tuple t){
		if(!t.getRelName().equals("IM") && !t.getRelName().equals("VH"))
			return false;
		Dom dom1 = t.getDomains()[0];
		Dom dom2 = t.getDomains()[1];
		int indices[] = t.getIndices();
		String str1 = dom1.toUniqueString(indices[0]);
		String str2 = dom2.toUniqueString(indices[1]);
			// IMs
		if (t.getRelName().equals("IM")) {
			Quad i = (Quad)t.getValue(0);
			if(i.getMethod().getDeclaringClass().toString().contains("org.apache.log4j"))
				return false;
		}
		// VH
		if (t.getRelName().equals("VH")){
			Quad h = (Quad)t.getValue(1);
			if(h.getMethod().getDeclaringClass().toString().contains("org.apache.log4j"))
				return false;
			// VH(34710,3004) 
			if(str1.equals("T1!getURL:()Ljava/net/URL;@weblech.spider.URLToDownload")
					&& str2.equals("238!extractAttributesFromTags:(Ljava/lang/String;Ljava/lang/String;Ljava/net/URL;Ljava/util/List;Ljava/util/Set;Ljava/lang/String;)V@weblech.spider.HTMLParser"))
				return false;
		}
		return true;	
	}

	@Override
	protected void generateFeedback(String feedbackFile, Set<Tuple> baseTuples, Set<Tuple> oracleTuples) {
		try {
			PrintWriter pw = new PrintWriter(new File(feedbackFile));
			pw.println("// Feedback");
			int qtsNum = this.getQueryTupleNum();
			for (Tuple t : baseTuples) {
				if (!relsWithLabels.contains(t.getRelName()))
					continue;
				boolean ifSurvive = oracleTuples.contains(t);

				if(t.getRelName().equals("VH") || t.getRelName().equals("IM"))
				{ // Hack
					// for some of the benchmarks, we use dynamic analysis and
					// manual labels to make the oracle more precises
					// Raytracer: we can fully trust the dynamic anlaysis
					// (manually verified)
					if (Config.workDirName.contains("raytracer")) {
						if (classifier.predictFalse(t, null) > 0.51)
							ifSurvive = false;
					}

					// jspider, some manual labels and dynamic analysis
					if (Config.workDirName.contains("jspider")) {
						boolean followDynamic = this.ifFollowDynamicJspider(t);
						if(followDynamic && classifier.predictFalse(t, null) > 0.51)
							ifSurvive = false;
					}
					
					// hedc, some manual labels and dynamic analysis
					if(Config.workDirName.contains("hedc")){
						boolean followDynamic = this.ifFollowDynamicHedc(t);
						if(followDynamic && classifier.predictFalse(t, null) > 0.51)
							ifSurvive = false;
					}
					
					// ftp, some manual labels and dynamic analysis
					if (Config.workDirName.contains("ftp")) {
						boolean followDynamic = this.ifFollowDynamicFtp(t);
						if (followDynamic && classifier.predictFalse(t, null) > 0.51)
							ifSurvive = false;
					}					
					// weblech, some manual labels and dynamic analysis
					if (Config.workDirName.contains("weblech")) {
						boolean followDynamic = this.ifFollowDynamicWeblech(t);
						if (followDynamic && classifier.predictFalse(t, null) > 0.51)
							ifSurvive = false;
					}
				}

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

	@Override
	protected void generateInfFeedback(String feedbackFile, Set<Tuple> baseQueries, Set<Tuple> oracleQueries) {
		System.out.println("Warning: do nothing in generateInfFeedback.");
	}

	@Override
	protected void generateUserStudyFeedback(String feedbackFile) {
		System.out.println("Warning: do nothing in generateUserStudyFeedback.");
	}

	@Override
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath) {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "prediction.txt"));
			// always run dynamic now
			// if(this.classifierKind.equals("dynamic")){
			classifier = new DynamicAnalysisClassifier();
			// }
			// else if (this.classifierKind.equals("none"))
			// classifier = null;
			// else
			// throw new RuntimeException("Unknown classifier
			// "+this.classifierKind);
			for (Tuple t : tuples) {
				if (classifier == null)
					pw.println(t + " 0");
				else
					pw.println(t + " " + classifier.predictFalse(t, provenance));
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

}
