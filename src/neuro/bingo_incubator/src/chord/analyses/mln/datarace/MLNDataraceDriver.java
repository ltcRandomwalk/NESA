package chord.analyses.mln.datarace;

import static chord.util.RelUtil.pRel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.crypto.Data;

import java.io.PrintWriter;
import java.util.ArrayList;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Class.jq_LineNumberBC;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;

import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.thread.cs.DomAS;
import chord.analyses.heapacc.DomE;
import chord.util.tuple.object.Pair;
import chord.analyses.mln.ConstraintItem;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.analyses.mln.kobj.MLNKobjDriver;
import chord.analyses.ursa.classifier.AllFalseClassifier;
import chord.analyses.ursa.classifier.BaggedClassifier;
import chord.analyses.ursa.classifier.Classifier;
import chord.analyses.ursa.classifier.datarace.DynamicAnalysisClassifier;
import chord.analyses.ursa.classifier.datarace.HandCraftedClassifier;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;
import chord.util.tuple.integer.IntTrio;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.integer.IntHext;

/**
 * -Dchord.mln.nonpldiK
 * -Dchord.mln.pointer
 * -Dchord.ursa.classifier=<craftedAggr/craftedCons/dynamic/bag/uniform>
 *
 * @author Ravi
 * @author Xin
 *
 */
@Chord(name = "datarace-mln-gen")
public class MLNDataraceDriver extends MLNAnalysisDriver {
	private int nonPLDIK;
	private String pointerAnalysis;
	private boolean useThrEsc;
	private String thrEscFile;
	private String classifierKind;    
	private boolean produceAliases;
	private String aliasOutput;
	private boolean produceCEC;
	private String CECOutput;

	private final static String ALIAS_OUTPUT = "aliasNames.txt";
	private final static String CEC_OUTPUT = "CECNames.txt";

	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("threadAC");
		ret.add("threadACH");
		ret.add("threadPM_cs");
		ret.add("threadPH_cs");
		ret.add("simplePM_cs");
		ret.add("simplePH_cs");
		ret.add("simplePT_cs");
		ret.add("PathEdge_cs");
		ret.add("mhp_cs");
		ret.add("SummEdge_cs");
	
		//flowins-thresc-cs-dlog
		ret.add("escO");
		ret.add("CEC");

		//datarace-parallel-include-cs-dlog
		ret.add("mhe_cs");

		//datarace-cs-noneg-dlog
		ret.add("statE");
		ret.add("escapingRaceHext");
		ret.add("parallelRaceHext");
		ret.add("datarace");
		ret.add("racePairs_cs");

		return ret;
	}

	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("AS");
		ret.add("I");
		ret.add("M");
		ret.add("P");
		ret.add("C");
	
		//flowins-thresc-cs-dlog
		ret.add("E");
		ret.add("M");
		ret.add("V");
		ret.add("Z");
		ret.add("C");
		ret.add("F");

		//datarace-parallel-include-cs-dlog
		ret.add("AS");
		ret.add("E");
		ret.add("P");
		ret.add("C");
		
		//datarace-cs-noneg-dlog
		ret.add("AS");
		ret.add("E");
		ret.add("K");
		ret.add("C");
		ret.add("F");

		return ret;
	}

	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();

		//mhp-cs-dlog
		ret.add("PP");
		ret.add("MPhead");
		ret.add("MPtail");
		ret.add("PI");
		ret.add("CICM");
		ret.add("threadACM");
		ret.add("threadStartI");
		ret.add("threadCICM");
	
		//flowins-thresc-cs-dlog
		ret.add("CVC");
		ret.add("FC");
		ret.add("CFC");
		ret.add("MmethArg");
		ret.add("EV");
		ret.add("escE");
		
		//datarace-parallel-include-cs-dlog
		ret.add("PE");
	//	ret.add("mhp_cs");

		//datarace-cs-noneg-dlog
		ret.add("EF");
		ret.add("statF");
		ret.add("excludeSameThread");
		ret.add("unlockedRaceHext");
	//	ret.add("mhe_cs");
	//	ret.add("CEC");

		return ret;
	}

	@Override
	protected String getQueryRelation(){
		return "racePairs_cs";
	}

	@Override
	protected String[] getConfigFiles() {
		String[] configFiles = new String[4];
		String chordMain = System.getenv("CHORD_INCUBATOR"); 
		String chordIncubator = System.getenv("CHORD_INCUBATOR");
		configFiles[0] = chordMain + File.separator + "src/chord/analyses/mln/datarace/flowins-thresc-cs-dlog_XZ89_.config";
		configFiles[1] = chordMain + File.separator + "src/chord/analyses/mln/datarace/mhp-cs-dlog_XZ89_.config";
	//	configFiles[1] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-escaping-include-cs-dlog_XZ89_.config";
		configFiles[2] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-parallel-include-cs-dlog_XZ89_.config";
        String aliasConfig = chordIncubator + File.separator + "src/chord/analyses/mln/kobj/alias-dlog_XZ89_.config";
		if (this.runningBase) {
			configFiles[3] = chordMain + File.separator + "src/chord/analyses/mln/datarace/datarace-cs-noneg-dlog_XZ89_.config";
			if(this.produceAliases)
		    	configFiles = new String[]{ configFiles[0], configFiles[1], configFiles[2], configFiles[3], aliasConfig};;
		}
		return configFiles;
	}

	@Override
	protected void genTasks(){
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("ctxts-java"));
		tasks.add(ClassicProject.g().getTask("argCopy-dlog"));
		if (this.pointerAnalysis.equals("kobj")) {
			tasks.add(ClassicProject.g().getTask("cspa-kobj-dlog"));
		} else {
			tasks.add(ClassicProject.g().getTask("cspa-kcfa-dlog"));
		}
		tasks.add(ClassicProject.g().getTask("thrSenCSCG-dlog"));
		tasks.add(ClassicProject.g().getTask("reachableACM-dlog"));
		tasks.add(ClassicProject.g().getTask("syncCLC-dlog"));
		if (Boolean.getBoolean("chord.datarace.exclude.nongrded"))
			tasks.add(ClassicProject.g().getTask("datarace-nongrded-exclude-cs-dlog"));
		else
			tasks.add(ClassicProject.g().getTask("datarace-nongrded-include-cs-dlog"));
		tasks.add(ClassicProject.g().getTask("escE-java")); //PLDI'16
		tasks.add(ClassicProject.g().getTask("datarace-cs-init-dlog"));
	//	tasks.add(ClassicProject.g().getTask("mhp-cs-dlog"));
		
		// we use the instrumented files from as we need all derivation paths for reverted constraints
		// also, we need to output all relations
		tasks.add(ClassicProject.g().getTask("flowins-thresc-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("mhp-cs-dlog_XZ89_"));
		tasks.add(ClassicProject.g().getTask("datarace-parallel-include-cs-dlog_XZ89_"));
	//	tasks.add(ClassicProject.g().getTask("datarace-escaping-include-cs-dlog_XZ89_"));
		if (this.runningBase) {
			tasks.add(ClassicProject.g().getTask("datarace-cs-noneg-dlog_XZ89_"));
		} else {
			tasks.add(ClassicProject.g().getTask("datarace-cs-noneg-dlog"));
		}
		if(this.produceAliases){
            tasks.add(ClassicProject.g().getTask("alias-dlog_XZ89_"));
        }
	}

	/**
	 * Invoke kobj-refiner to get the result.
	 */
	@Override
	protected void runOracle(){
		if (this.pointerAnalysis.equals("kobj")) {
			System.setProperty("chord.ctxt.kind", "co");
			System.setProperty("chord.kobj.k", ""+this.nonPLDIK);
		} else {
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", ""+this.nonPLDIK);
		}
		if (this.useThrEsc)
			System.setProperty("chord.mln.threscFile", this.thrEscFile);
		areCurrentRelsOracle = true;

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}

		if(this.produceCEC) {
			printOracleRace(this.CECOutput);
		}
	}
	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase(){
//		if (this.pointerAnalysis.equals("kobj")) {
//			System.setProperty("chord.ctxt.kind", "co");
//			System.setProperty("chord.kobj.k", "1");
//		} else {
		// Always run 0-cfa, but note the labels on intermediate tuples might not make sense anymore
			System.setProperty("chord.ctxt.kind", "cs");
			System.setProperty("chord.kcfa.k", "0");
//		}
		/* SRK 28th Sept 2017: This function (runBaseCase) is executed both in the PROBLEM mode and ORACLE mode.
		   In the PROBLEM mode: the commandline settings of chord.mln.threscFile must be honored.
		   In the ORACLE mode: the chord.mln.threscFile setting must be honored for the oracle run but it must
		                       be cleared for the base run that just precedes the oracle run.
		*/
		if (this.mode == Mode.ORACLE)
			System.clearProperty("chord.mln.threscFile");
		for (ITask t : tasks) {
			if(t.getName().equals("cspa-kobj-dlog"))
				t = ClassicProject.g().getTask("cspa-kcfa-dlog");
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}

		// Output variable names
		if(this.produceAliases){
				MLNKobjDriver.printAlias(this.aliasOutput);
		}

		if(this.produceCEC) {
			printCEC(this.CECOutput);
		}
		areCurrentRelsOracle = false;
	}

	public void printOracleCEC(String outFileName){
		ProgramRel CECs = (ProgramRel) ClassicProject.g().getTrgt("CEC");
		CECs.load();
		try {
			PrintWriter pw = new PrintWriter((Config.outDirName+File.separator+outFileName));
			for(IntTrio cec : CECs.getAry3IntTuples()) {
				Tuple cecTuple = new Tuple(CECs, new int[]{cec.idx0, cec.idx1, cec.idx2});
				Set<Tuple> s = project(cecTuple);
				for(Tuple t : s) {
					pw.println(t);
				}
				//pw.println();
			}
			pw.flush();
			pw.close();
		} catch(FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public void printOracleRace(String outFileName){
		ProgramRel CECs = (ProgramRel) ClassicProject.g().getTrgt("racePairs_cs");
		CECs.load();
		try {
			PrintWriter pw = new PrintWriter((Config.outDirName+File.separator+outFileName));
			for(IntPair cec : CECs.getAry2IntTuples()) {
				Tuple cecTuple = new Tuple(CECs, new int[]{cec.idx0, cec.idx1});
				Set<Tuple> s = project(cecTuple);
				for(Tuple t : s) {
					pw.println(t);
				}
				//pw.println();
			}
			pw.flush();
			pw.close();
		} catch(FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void printCEC(String outFileName){
	
		DomE domE = (DomE) ClassicProject.g().getTrgt("E");
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		ProgramRel CECs = (ProgramRel) ClassicProject.g().getTrgt("CEC");
		ProgramRel Dataraces = (ProgramRel) ClassicProject.g().getTrgt("racePairs_cs");
		CECs.load();
		Dataraces.load();
		/* 
		try {
			PrintWriter pw = new PrintWriter((Config.outDirName+File.separator+outFileName));
			for(IntTrio cec : CECs.getAry3IntTuples()) {
				Tuple cecTuple = new Tuple(CECs, new int[]{cec.idx0, cec.idx1, cec.idx2});
				//pw.println(cecTuple.getValue(0));
				//pw.println(cecTuple.getValue(1));
				//pw.println(cecTuple.getValue(2));
				Quad q = (Quad)cecTuple.getValue(1);
				Ctxt c = (Ctxt)cecTuple.getValue(2);
				Quad qc = c.get(0);
				//pw.println(q);
				//pw.println(c);
				String varLine1 = null;
				String varLine2 = null;
				jq_Method m = null;
				List<Integer> lns = null;
				//List<String> varLines = new ArrayList<>(String)();
				try{
				    m = q.getMethod();
					varLine1 = getVarLine(q);
					//varLine2 = getVarLine(qc);
				}catch (Exception e){
					//pw.println(e);
					continue;
				}
				pw.println(cecTuple);
				pw.println(m.getName());
				//pw.println(lns);
				pw.println(varLine1);
				//pw.println(varLine2);
				pw.println(qc.getOp2());
				//pw.println(varLine2);

				//Register r1 = (Register)cecTuple.getValue(1);
				//Register r2 = (Register) cecTuple.getValue(2);
				//pw.println(r1);
				//pw.println(r2);
				//pw.println();
				pw.println();
			}
			//pw.println(domE);
			//pw.println(domC);

			//pw.println("testcec");
			//pw.println();
			pw.flush();
			pw.close();
		} catch(FileNotFoundException e) {
			throw new RuntimeException(e);
		}
*/
		try {
			PrintWriter pw = new PrintWriter((Config.outDirName+File.separator+outFileName));
			//pw.println("1111");
			for(IntPair cec : Dataraces.getAry2IntTuples()) {
				//pw.println("2222");
				Tuple cecTuple = new Tuple(Dataraces, new int[]{cec.idx0, cec.idx1});
				//Pair<Quad, jq_Method> t1 = (Pair<Quad, jq_Method>)cecTuple.getValue(0);
				//Quad qt1 = t1.val0;
				//jq_Method mt1 = t1.val1;
				//Ctxt c1 = (Ctxt)cecTuple.getValue(1);
				Quad q1 = (Quad)cecTuple.getValue(0);
				//Pair<Quad, jq_Method> t2 = (Pair<Quad, jq_Method>)cecTuple.getValue(3);
				//Quad qt2 = t2.val0;
				//jq_Method mt2 = t2.val1;
				//Ctxt c2 = (Ctxt)cecTuple.getValue(4);
				Quad q2 = (Quad)cecTuple.getValue(1);

				String varLine1 = null;
				String varLine2 = null;
				String t_varLine1 = null;
				String t_varLine2 = null;
				jq_Method m1 = q1.getMethod();
				jq_Method m2 = q2.getMethod();

				try{
				    //m = q.getMethod();

					varLine1 = getVarLine(q1);
					varLine2 = getVarLine(q2);
					//t_varLine1 = getVarLine(qt1);
					//t_varLine2 = getVarLine(qt2);
					//varLine2 = getVarLine(qc);
				}catch (Exception e){
					pw.println(e);
					continue;
				}
				pw.println(cecTuple);
				pw.println(varLine1);
				//pw.println(m1.getName());
				//for(jq_LineNumberBC b : m1.getLineNumberTable()) {
				//	pw.println(b);
				//}

				pw.println();
				//pw.println(m1.getLineNumberTable());
				pw.println(varLine2);
				//pw.println(m2.getName());
				//for(jq_LineNumberBC b : m2.getLineNumberTable()) {
				//	pw.println(b);
				//}
				pw.println();

			}

			pw.flush();
			pw.close();
		} catch(FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}


	private static String getVarLine(Quad q) {
		//DomE domE = (DomE) ClassicProject.g().getTrgt("E");
		jq_Method m = q.getMethod();
		String[] srcPaths = Config.srcPathName.split(File.pathSeparator);
		String srcFile = null;
		for(String sp : srcPaths){
			srcFile = Config.workDirName+File.separator+sp+File.separator+m.getDeclaringClass().getSourceFileName();
			File f = new File(srcFile);
			if(f.exists()){
				break;
			}
		}
		List<Integer> lns = new ArrayList<Integer>();
		for(int i=-10; i<10; i++) {
			int ln = q.getLineNumber();
			if(ln + i <= 0) continue;
			lns.add(q.getLineNumber()+i);
		}
		//lns.add(q.getLineNumber()-1);
		//lns.add(q.getLineNumber());

		//lns.add(q.getLineNumber()+1);
		
		//List<Integer> lns = m.getLineNumber(q.getBCI());
		System.out.println(lns);
		try{
			String defLine = "";
			String thisLine = null;
			if(lns != null){
				for(int dln : lns) {
					//int dln = lns.get(0);

					if (dln == 0)
						return "";
					List<String> lines = new ArrayList<String>();
					BufferedReader reader = new BufferedReader(new FileReader(new File(srcFile)));
					String line;
					while ((line = reader.readLine()) != null)
						lines.add(line);
					reader.close();
					if(dln > lines.size()) break;
					defLine += lines.get(dln - 1);
					defLine += '\n';
					if(dln == q.getLineNumber())
						thisLine = lines.get(dln - 1);

				}
				return "*this:" + thisLine + '\n' + defLine ;
			}
			else return null;
		}catch(IOException e){
			throw new RuntimeException(e);
		}
	}

	//In kobj, there're two kinds of Cs: H and O. For simplicity, we project t to both possibilities
	@Override
	protected Set<Tuple> project(Tuple t){
		int[] newIndicies = new int[t.getIndices().length];
		Set<Tuple> ret = this.projectRecursively(t, newIndicies, 0);
		return ret;
	}

	private Set<Tuple> projectRecursively(Tuple t, int[] newIndicies, int index){
		Set<Tuple> ret = new HashSet<Tuple>();
		Dom doms[] = t.getDomains();
		Dom d = doms[index];
		int oriIndicies[] = t.getIndices();
		if(d instanceof DomC){
			DomC dc = (DomC)d;
			Ctxt ct = dc.get(oriIndicies[index]);
			Ctxt ct1 = ct.prefix(0);
			Ctxt ct2 = ct.prefix(1);
			int[] newIndicies1 = new int[newIndicies.length];
			int[] newIndicies2 = new int[newIndicies.length];
			System.arraycopy(newIndicies, 0, newIndicies1, 0, newIndicies.length);
			System.arraycopy(newIndicies, 0, newIndicies2, 0, newIndicies.length);
			newIndicies1[index] = dc.indexOf(ct1);
			newIndicies2[index] = dc.indexOf(ct2);
			if(index == newIndicies.length-1){
				Tuple t1 = new Tuple(t.getRel(),newIndicies1);
				Tuple t2 = new Tuple(t.getRel(),newIndicies2);
				ret.add(t1);
				ret.add(t2);
			}else{
				index++;
				ret.addAll(this.projectRecursively(t, newIndicies1, index));
				ret.addAll(this.projectRecursively(t, newIndicies2, index));
			}
		}else{
			int[] newIndicies1 = new int[newIndicies.length];
			System.arraycopy(newIndicies, 0, newIndicies1, 0, newIndicies.length);
			newIndicies1[index] = oriIndicies[index];
			if(index == newIndicies.length-1){
				Tuple t1 = new Tuple(t.getRel(),newIndicies1);
				ret.add(t1);
			}else{
				index++;
				ret.addAll(this.projectRecursively(t, newIndicies1, index));
			}		
		}
		return ret;
	}

	@Override
	protected void readSettings(){
		super.readSettings();
		this.nonPLDIK = Integer.getInteger("chord.mln.nonpldiK", 1);
		this.useThrEsc = Boolean.getBoolean("chord.mln.useThrEsc");
		this.thrEscFile = System.getProperty("chord.mln.threscFile");
		this.classifierKind = System.getProperty("chord.ursa.classifier", "dynamic");
        this.produceAliases = Boolean.getBoolean("chord.mln.kobj-alias");
		this.aliasOutput = System.getProperty("chord.mln.alias-output", ALIAS_OUTPUT);
		this.produceCEC = Boolean.getBoolean("chord.mln.datarace-cec");
		this.CECOutput = System.getProperty("chord.mln.cec-output", CEC_OUTPUT);
		if (this.useThrEsc && this.thrEscFile == null) {
			throw new RuntimeException("Specify thread escape proven queries file.");
		}
		
		this.pointerAnalysis = System.getProperty("chord.mln.pointer", "kcfa");
		if(!this.pointerAnalysis.equals("kcfa") && !this.pointerAnalysis.equals("kobj")){
			throw new RuntimeException("Unknown pointer analysis");
		} 

		// for ursa:
//		System.setProperty("chord.datarace.exclude.init", "false");
//		System.setProperty("chord.datarace.exclude.eqth", "true");
//		System.setProperty("chord.datarace.exclude.nongrded", "true");
		// for fse15:
//		System.setProperty("chord.datarace.exclude.init", "true");
//		System.setProperty("chord.datarace.exclude.eqth", "true");
//		System.setProperty("chord.datarace.exclude.nongrded", "false");
	}

	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("PathEdge_cs"), new int[]{0, 0, 1, 0, 0}));
		return axiomTuples;
	}

	@Override
	public void run() {
		super.run();
		ClassicProject.g().runTask("orderedEE-dlog");
		ProgramRel orderedEE = (ProgramRel)ClassicProject.g().getTrgt("OrderedEE");
		orderedEE.load();
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName+File.separator+"correlEE.txt"));
			for(int n[] : orderedEE.getAryNIntTuples()){
				for(int i : n)
					pw.print("escE("+i+") ");
				pw.println();
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}	
	}

	@Override
	protected void predict(Set<Tuple> tuples, Set<ConstraintItem> provenance, String classifierPath) {
		try {
			PrintWriter pw = new PrintWriter(new File(Config.outDirName + File.separator + "prediction.txt"));
			Classifier classifier = null;
			if(this.classifierKind.equals("dynamic")){
				classifier = new DynamicAnalysisClassifier();
			}
			else if (this.classifierKind.equals("craftedAggr"))
				classifier = new HandCraftedClassifier(true);
			else if (this.classifierKind.equals("craftedCons"))
				classifier = new HandCraftedClassifier(false);
			else if (this.classifierKind.equals("bag"))
				classifier = new BaggedClassifier();
			else if (this.classifierKind.equals("uniform"))
				classifier = new AllFalseClassifier();
			else
				throw new RuntimeException("Unknown classifier "+this.classifierKind);
			for (Tuple t : tuples) {
				pw.println(t+" "+classifier.predictFalse(t, provenance));
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	@Override
	protected void generateAppScope(String fileName) {
		ClassicProject.g().runTask("checkExcludedP-dlog");
		ClassicProject.g().runTask("checkExcludedI-dlog");
		ClassicProject.g().runTask("checkExcludedE-dlog");
		
		ProgramRel checkExcludedI = (ProgramRel)ClassicProject.g().getTrgt("checkExcludedI");
		checkExcludedI.load();
		ProgramRel checkExcludedP = (ProgramRel)ClassicProject.g().getTrgt("checkExcludedP");
		checkExcludedP.load();
		ProgramRel checkExcludedE = (ProgramRel)ClassicProject.g().getTrgt("checkExcludedE");
		checkExcludedE.load();
		
		try {
			PrintWriter pw = new PrintWriter(new File(fileName));

			// app causes
			ProgramRel pathEdge = (ProgramRel) ClassicProject.g().getTrgt("PathEdge_cs");
			pathEdge.load();
			
			for(int content[] : pathEdge.getAryNIntTuples()){
				Tuple t = new Tuple(pathEdge,content);
				// check if P is app P
				if(!checkExcludedP.contains(content[1]))
					pw.println(t.toString());
			}
			
			ProgramRel escE = (ProgramRel) ClassicProject.g().getTrgt("escE");
			escE.load();
			
			for(int content[] : escE.getAryNIntTuples()){
				Tuple t = new Tuple(escE,content);
				// check if E is appE
				if(!checkExcludedE.contains(content[0]))
					pw.println(t.toString());
			}
			
			ProgramRel cicm = (ProgramRel) ClassicProject.g().getTrgt("CICM");
			cicm.load();
			
			for(int content[] : cicm.getAryNIntTuples()){
				Tuple t = new Tuple(cicm,content);
				//check if I is appI
				if(!checkExcludedI.contains(content[1]))
					pw.println(t.toString());
			}
			
			ProgramRel race = (ProgramRel) ClassicProject.g().getTrgt("racePairs_cs");
			race.load();
			
			for(int content[] : race.getAryNIntTuples()){
				Tuple t = new Tuple(race,content);
				pw.println(t.toString());
			}
			
			pw.flush();
			pw.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}	
	
}
