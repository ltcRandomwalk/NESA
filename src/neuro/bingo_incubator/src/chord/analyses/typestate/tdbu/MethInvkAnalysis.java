package chord.analyses.typestate.tdbu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.instr.InstrScheme;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.DynamicAnalysis;
import chord.project.analyses.ProgramRel;

@Chord(name="typestate-invktrace-java",consumes={"checkExcludedH"})
public class MethInvkAnalysis extends DynamicAnalysis {
	private int runID = 0;
	private InstrScheme scheme = null;
	private Map<Integer,jq_Type> objTypeMap = null;
	private Map<Integer,List<jq_InstanceMethod>> invkListMap;
	private DomH domH;
	private DomI domI;
	private ProgramRel checkExcludedH;
	private final static String SEP = "=============SEP=============";
	public final static String POSTFIX = ".methtrace";
	
	public static Map<String,List<List<String>>> readFromFile(String path){
		try {
			Scanner sc = new Scanner(new File(path));
			Map<String,List<List<String>>> ret = new HashMap<String,List<List<String>>>();
			List<String> current = null;
			while(sc.hasNext()){
				String line = sc.nextLine();
				if(line.equals(SEP)){
					current = new ArrayList<String>();
					line = sc.nextLine();//the type
					List<List<String>> ilCol = ret.get(line);//list of list of invocations
					if(ilCol == null){
						ilCol = new ArrayList<List<String>>();
						ret.put(line, ilCol);
					}
					ilCol.add(current);
					line = sc.nextLine();
				}
				current.add(line);
			}
			return ret;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void writeToFile(String path){
		try {
			PrintWriter pw = new PrintWriter(new File(path));
			for(Map.Entry<Integer, List<jq_InstanceMethod>> entry : invkListMap.entrySet()){
				pw.println(SEP);
				jq_Type jc = objTypeMap.get(entry.getKey());
				pw.println(jc.getName());
				for(jq_InstanceMethod m : entry.getValue()){
					pw.println(m.getNameAndDesc());
				}
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public InstrScheme getInstrScheme() {
		if(scheme != null)
			return scheme;
		scheme = new InstrScheme();
		scheme.setBefNewEvent(true, false, true);
		scheme.setBefMethodCallEvent(true,false, true);
		return scheme;
	}

	@Override
	public void initPass() {
		super.initPass();
		objTypeMap = new HashMap<Integer,jq_Type>();
		invkListMap = new HashMap<Integer,List<jq_InstanceMethod>>();
	}

	@Override
	public void donePass() {
		super.donePass();
		String path = Config.workDirName+File.separator+runID+POSTFIX;
		runID++;
		writeToFile(path);
	}

	@Override
	public void initAllPasses() {
		super.initAllPasses();
		domH = (DomH)ClassicProject.g().getTrgt("H");
		ClassicProject.g().runTask(domH);
		domI = (DomI)ClassicProject.g().getTrgt("I");
		ClassicProject.g().runTask(domI);
		checkExcludedH = (ProgramRel)ClassicProject.g().getTrgt("checkExcludedH");
		checkExcludedH.load();
	}

	@Override
	public void doneAllPasses() {
		// TODO Auto-generated method stub
		super.doneAllPasses();
	}

	@Override
	public void processBefMethodCall(int i, int t, int o) {
		super.processBefMethodCall(i, t, o);
		if(i<0||!objTypeMap.containsKey(o))
			return;
//		System.out.println("Method index: "+i);
		Quad q = domI.get(i);
		MethodOperand mo = Invoke.getMethod(q);
		jq_Method meth = mo.getMethod();
		if(meth instanceof jq_InstanceMethod){
			jq_InstanceMethod insMeth = (jq_InstanceMethod)meth;
			List<jq_InstanceMethod> ml = invkListMap.get(o);
			if(ml == null){
				ml = new ArrayList<jq_InstanceMethod>();
				invkListMap.put(o, ml);
			}
			ml.add(insMeth);
		}
	}

	@Override
	public void processBefNew(int h, int t, int o) {
		super.processBefNew(h, t, o);
		if(checkExcludedH.contains(h))
			return;
		Quad q = (Quad)domH.get(h);
		jq_Type type = New.getType(q).getType();
		objTypeMap.put(o, type);
	}


}
