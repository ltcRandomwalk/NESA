package chord.analyses.mln.kobj;

import static chord.util.RelUtil.pRel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alloc.DomH;
import chord.analyses.argret.DomK;
import chord.analyses.invk.DomI;
import chord.analyses.mln.MLNAnalysisDriver;
import chord.analyses.provenance.kobj.KOBJRefiner;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/**
 * -Dchord.mln.client=downcast
 * -Dchord.mln.nonpldi
 * -Dchord.mln.nonpldiK
 * 
 * @author xin
 *
 */
@Chord(name = "kobj-mln-gen-woinit")
public class MLNKobjDriverWOInit extends MLNAnalysisDriver {
	private int client; // 0 polysite, 1 downcast, 2 race, 3 pts, 4 infoflow
	private boolean nonPLDIOracle;
	private int nonPLDIK;
	
	ProgramRel IKRel;
	ProgramRel HKRel;
	ProgramRel OKRel;
	ProgramRel allowHRel;
	ProgramRel denyHRel;
	ProgramRel allowORel;
	ProgramRel denyORel;
	DomI domI;
	DomH domH;
	DomV domV;
	DomK domK;
	chord.analyses.provenance.kobj.KOBJRefiner objRefinerTask;
	
	@Override
	protected Set<String> getDerivedRelations(){
		Set<String> ret = new HashSet<String>();
		
		//pro-cspa-kobj-dlog
		ret.add("CCM");
		ret.add("DIH");
		ret.add("DVDV");
		ret.add("reachableCI");
		ret.add("CCMatch");
		ret.add("RputStatFldInst");
		ret.add("DIC");
		ret.add("kobjSenSpecIM");
		ret.add("ctxtCpyStatIM");
		ret.add("ctxtInsIHM");
		ret.add("IHM");
		ret.add("reachableT");
		ret.add("CVC");
		ret.add("RputInstFldInst");
		ret.add("RobjVarAsgnInst");
		ret.add("CFC_37_0");
		ret.add("RgetStatFldInst");
		ret.add("ctxtInsSpecIM");
		ret.add("RobjValAsgnInst");
		ret.add("CICM");
		ret.add("FC");
		ret.add("CVC_33_0");
		ret.add("CMCM");
		ret.add("reachableCM");
		ret.add("ctxtInsStatIM");
		ret.add("kobjSenIHM");
		ret.add("kobjSenICM");
		ret.add("DI");
		ret.add("RgetInstFldInst");
		ret.add("CFC");
		ret.add("rootCM");
		
		if(client == 1){
			ret.add("reachableCast");
			ret.add("ptsVT");
			ret.add("unsafeDowncast");
			ret.add("ptsVH");
		} else if(client == 0){
			ret.add("virtI");
			ret.add("insvIM");
			ret.add("polySite");
		} else if(client == 3){
			ret.add("reachableV");
			ret.add("reachableH");
			ret.add("ptsVH");
		} else if(client == 4){
			ret.add("reachableV");
			ret.add("reachableH");
			ret.add("flowHV");
		}else
			this.throwUnknownClientException();
		return ret;
	}
	
	@Override
	protected Set<String> getDomains() {
		Set<String> ret = new HashSet<String>();
		//domains from kobj-bit-init.dlog
		ret.add("I");
		ret.add("H");
		ret.add("M");
		ret.add("K");
		ret.add("V");
		ret.add("C");

		//domains from cspa-kobj.dlog
		ret.add("I");
		ret.add("T");
		ret.add("H");
		ret.add("M");
		ret.add("V");
		ret.add("Z");
		ret.add("C");
		ret.add("F");
		
		//domains from the client
		if(client == 0){
			ret.add("I");
			ret.add("M");
			ret.add("C");
		} else if(client == 1){
			ret.add("H");
			ret.add("T");
			ret.add("M");
			ret.add("V");
			ret.add("C");
		} else if(client == 3 || client == 4){
			ret.add("H");
			ret.add("M");
			ret.add("V");
			ret.add("C");
		}
		else
			this.throwUnknownClientException();;
		return ret;
	}
	
	@Override
	protected Set<String> getInputRelations() {
		Set<String> ret = new HashSet<String>();
		//input relations from kobj-bit-init.dlog
		ret.add("initCOC");
		ret.add("initCHC");
		ret.add("truncCKC");
		ret.add("roots");
		ret.add("IM");
		ret.add("VH");
		ret.add("MI");
		ret.add("MH");
		ret.add("CL");
		ret.add("IinvkArg0");
		ret.add("statM");
		ret.add("AllowH");
		ret.add("DenyH");
		ret.add("AllowO");
		ret.add("DenyO");
		ret.add("thisMV");
		
		//output relations from kobj-bit-init-dlog
		ret.add("COC_1");
		ret.add("CM");
		ret.add("HM");
		ret.add("CHC");
		ret.add("CHC_1");
		ret.add("CHC_2");
		ret.add("COC_2");
		ret.add("COC");
		
		//input relations from cspa-kobj.dlog
		ret.add("HT");
		ret.add("cha");
		ret.add("sub");
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
		ret.add("MmethArg");
		ret.add("MspcMethArg");
		ret.add("IinvkArg");
		ret.add("IinvkArg0");
		ret.add("IinvkRet");
		ret.add("argCopy");
		ret.add("retCopy");
		ret.add("VCfilter");
		ret.add("CH");
		ret.add("epsilonM");
		ret.add("kobjSenM");
		ret.add("ctxtCpyM");
		
		//input relations from the client
		if(client == 0){
			ret.add("virtIM");
			ret.add("checkExcludedI");
		}
		else if(client == 1){
			ret.add("checkExcludedM");
			ret.add("McheckCastInst");
		}
		else if(client == 3){
			ret.add("checkExcludedV");
			ret.add("checkExcludedH");
			ret.add("MV");
			ret.add("MH");
		} else if(client == 4){
			ret.add("sourceH");
			ret.add("sinkV");
			ret.add("MV");
			ret.add("MH");
		}
		else
			this.throwUnknownClientException();;
		return ret;
	}
	
	@Override
	protected String getQueryRelation(){
		if(client == 1)
			return "unsafeDowncast";
		if(client == 0)
			return "polySite";
		if(client == 3)
			return "ptsVH";
		if(client == 4)
			return "flowHV";
		this.throwUnknownClientException();
		return null;
	}
	
	private String getClient() {
		String clientStr = null;
		if(this.client == 1)
			clientStr = "downcast";
		else if(this.client == 0)
			clientStr = "polysite";
		else if(this.client == 3)
			clientStr = "pts";
		else if(this.client == 4)
			clientStr = "infoflow";
		else
			this.throwUnknownClientException();
		return clientStr;
	}
	
	@Override
	protected String[] getConfigFiles() {
		String clientConfigPath = null;
		if (this.client == 1) {
			clientConfigPath = "src/chord/analyses/mln/kobj/pro-downcast-dlog_XZ90_.config";
		} else if (this.client == 0) {
			clientConfigPath = "src/chord/analyses/mln/kobj/polysite-dlog_XZ90_.config";
		} else if (this.client == 3) {
			clientConfigPath = "src/chord/analyses/mln/kobj/pro-pts-dlog_XZ90_.config";
		} else if (this.client == 4) {
			clientConfigPath = "src/chord/analyses/mln/kobj/infoflow-dlog_XZ89_.config";
		} else
			this.throwUnknownClientException();
		
		String chordMain = System.getenv("CHORD_MAIN");
		String chordIncubator = System.getenv("CHORD_INCUBATOR");
		//String kinitConfig = chordIncubator + File.separator + "src/chord/analyses/mln/kobj/kobj-bit-init-dlog_XZ90_.config";
		String kobjConfig = chordIncubator + File.separator + "src/chord/analyses/mln/kobj/pro-cspa-kobj-dlog_XZ90_.config";
		String clientConfig = chordIncubator + File.separator + clientConfigPath;
		String[] configFiles = new String[]{ kobjConfig, clientConfig };
		return configFiles;
	}
	
	@Override
	protected void genTasks(){
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("simple-pro-ctxts-java"));
		tasks.add(ClassicProject.g().getTask("pro-argCopy-dlog"));
		// we use the instrumented files from PLDI'14 as they save intermediate relations
		tasks.add(ClassicProject.g().getTask("kobj-bit-init-dlog_XZ90_"));
		tasks.add(ClassicProject.g().getTask("pro-cspa-kobj-dlog_XZ90_"));
		if(client == 1)
			tasks.add(ClassicProject.g().getTask("pro-downcast-dlog_XZ90_"));
		else if(client == 0)
			tasks.add(ClassicProject.g().getTask("polysite-dlog_XZ90_"));
		else if(client == 3)
			tasks.add(ClassicProject.g().getTask("pro-pts-dlog_XZ90_"));
		else if(client == 4)
			tasks.add(ClassicProject.g().getTask("infoflow-dlog_XZ89_"));
		else
			this.throwUnknownClientException();
	}
		
	/**
	 * Invoke kobj-refiner to get the result.
	 */
	@Override
	protected void runOracle(){
		String clientStr = getClient();

		if (this.nonPLDIOracle) {
			this.runClientWithK(this.nonPLDIK);
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
			
	private void runClientWithK(int k) {
		int hk;
		if(k == 0)
			hk = 1;
		else
			hk = k;
		HKRel.zero();
		allowHRel.zero();
		denyHRel.zero();
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			setHK(H,hk, 20);
		}
		HKRel.save();
		allowHRel.save();
		denyHRel.save();

		OKRel.zero();
		allowORel.zero();
		denyORel.zero();
		for(int i = 0; i < domH.size(); i++){
			Quad H = (Quad) domH.get(i);
			setOK(H,k, 20);
		}
		OKRel.save();
		allowORel.save();
		denyORel.save();

		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	/**
	 * Run 0-cfa
	 */
	@Override
	protected void runBaseCase(){
		System.setProperty("chord.ctxt.kind", "co");
		IKRel = (ProgramRel) ClassicProject.g().getTrgt("IK");
		HKRel = (ProgramRel) ClassicProject.g().getTrgt("HK");
		OKRel = (ProgramRel) ClassicProject.g().getTrgt("OK");
		allowHRel = (ProgramRel) ClassicProject.g().getTrgt("AllowH");
		denyHRel = (ProgramRel) ClassicProject.g().getTrgt("DenyH");
		allowORel = (ProgramRel) ClassicProject.g().getTrgt("AllowO");
		denyORel = (ProgramRel) ClassicProject.g().getTrgt("DenyO");
		domI = (DomI) ClassicProject.g().getTrgt("I");
		domK = (DomK) ClassicProject.g().getTrgt("K");
		domH = (DomH) ClassicProject.g().getTrgt("H");
		ClassicProject.g().runTask(domI);
		ClassicProject.g().runTask(domK);
		ClassicProject.g().runTask(domH);
		IKRel.zero();
		for (int i = 0; i < domI.size(); i++) {
			Quad I = (Quad) domI.get(i);
			IKRel.add(I,0);
		}
		IKRel.save();
		
		int k = 0;
		int hk = 1;

		HKRel.zero();
		allowHRel.zero();
		denyHRel.zero();
		for (int i = 0; i < domH.size(); i++) {
			Quad H = (Quad) domH.get(i);
			setHK(H,hk, 20);
		}
		HKRel.save();
		allowHRel.save();
		denyHRel.save();
		
		OKRel.zero();
		allowORel.zero();
		denyORel.zero();
		for(int i = 0; i < domH.size(); i++){
			Quad H = (Quad) domH.get(i);
			setOK(H,k, 20);
		}
		OKRel.save();
		allowORel.save();
		denyORel.save();
	
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
		
		if (client == 4) {
			try {
				Map<RegisterFactory.Register, Set<Quad>> sinkRegToQuadMap = new HashMap<RegisterFactory.Register, Set<Quad>>();
				ProgramRel relCheckIncludedI = (ProgramRel) ClassicProject.g().getTrgt("checkIncludedI");
				relCheckIncludedI.load();
				
				DomI domI = (DomI) ClassicProject.g().getTrgt("I");
				ClassicProject.g().runTask(domI);
				for (int idx = 0; idx < domI.size() ; idx++) {
					Quad q = (Quad) domI.get(idx);
					jq_Method m = Invoke.getMethod(q).getMethod();
					if (m.getName().toString().equals("sink") && relCheckIncludedI.contains(idx)) {
						ParamListOperand args = Invoke.getParamList(q);
						for (int i = 0; i < args.length(); i++) {
			                Register actualReg = args.get(i).getRegister();
			                Set<Quad> sinkCalls = sinkRegToQuadMap.get(actualReg);
			                if (sinkCalls == null) {
			                	sinkCalls = new HashSet<Quad>();
			                	sinkRegToQuadMap.put(actualReg, sinkCalls);
			                }
			                sinkCalls.add(q);
			            }
					}
				}
				relCheckIncludedI.close();
				
				domV = (DomV) ClassicProject.g().getTrgt("V");
				PrintWriter pw = new PrintWriter(new File(Config.outDirName+File.separator+"base_queries_lineNum.txt"));
				String queryRel = getQueryRelation();
				ProgramRel pr = (ProgramRel)ClassicProject.g().getTrgt(queryRel);
				pr.load();
				for(int[] indices : pr.getAryNIntTuples()){
					Tuple t = new Tuple(pr, indices);
					pw.print(t);
					Quad source = (Quad)domH.get(indices[0]);
					Register sinkReg = (Register)domV.get(indices[1]);
					Set<Quad> sinkCalls = sinkRegToQuadMap.get(sinkReg);
					String sinks = "";
					for (Quad q : sinkCalls) {
						sinks += q.toJavaLocStr() + ",";
					}
					pw.println(": Source = " + source.toJavaLocStr() + "; "
						+ "Sink = " + sinks);
				}
				pw.flush();
				pw.close();
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		
		areCurrentRelsOracle = false;
	}
	
	private void setHK(Quad q, int k,int max){
		HKRel.add(q,k);
		for(int i = 0; i <= k; i++){
			allowHRel.add(q,i);
		}
		for(int i = k+1; i <= max; i++){
			denyHRel.add(q,i);
		}
	}

	private void setOK(Quad q, int k, int max){
		OKRel.add(q,k);
		for(int i = 0; i <= k; i++){
			allowORel.add(q,i);
		}
		for(int i = k+1; i <= max; i++){
			denyORel.add(q,i);
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
		String clientStr = System.getProperty("chord.mln.client", "downcast");
		if(clientStr.equals("downcast")){
			this.client = 1;
		} else if(clientStr.equals("polysite")) {
			this.client = 0;
		} else if(clientStr.equals("pts")) {
			this.client = 3;
		} else if(clientStr.equals("infoflow")) {
			this.client = 4;
		}else {
			this.throwUnknownClientException();
		}
		
		this.nonPLDIOracle = Boolean.getBoolean("chord.mln.nonpldi");
		this.nonPLDIK = Integer.getInteger("chord.mln.nonpldiK", 2);
		
		System.setProperty("chord.ctxt.kind", "co");
	}
	
	@Override
	protected List<Tuple> getAxiomTuples() {
		List<Tuple> axiomTuples = new ArrayList<Tuple>();
		axiomTuples.add(new Tuple(pRel("reachableCM"), new int[]{0, 0}));
		axiomTuples.add(new Tuple(pRel("rootCM"), new int[]{0, 0}));
		return axiomTuples;
		
	}
}
