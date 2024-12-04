package chord.analyses.qmaxsat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.ALength;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor.EmptyVisitor;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIObj;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.qmaxsat.Problem;
import chord.util.ArraySet;

@Chord(name = "reachdef-maxsatgen-java",consumes = {"checkExcludedM" })
public class ReachDefGenerator extends JavaAnalysis {
	public final static List<Object> EMPTY = new ArrayList<Object>();
	private Map<jq_Method, Set<Integer>> fQueries;
	private Map<jq_Method, Set<Integer>> bQueries;
	public  CIPAAnalysis cipa;
	public  ICICG cicg;

	@Override
	public void run() {
		CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g().getTrgt(
				"cicg-java");
		ClassicProject.g().runTask(cicgAnalysis);
		cicg = cicgAnalysis.getCallGraph();
		cipa = (CIPAAnalysis) ClassicProject.g().getTask("cipa-java");
		ClassicProject.g().runTask(cipa);
		fQueries = new HashMap<jq_Method, Set<Integer>>();
		bQueries = new HashMap<jq_Method, Set<Integer>>();
		ProgramRel relCheckExcludedM = (ProgramRel) ClassicProject.g().getTrgt(
				"checkExcludedM");
		relCheckExcludedM.load();
		Problem p = new Problem("Null dereference problem on "
				+ Config.workDirName);
		for (jq_Method m : cicg.getNodes()) {
			if (!relCheckExcludedM.contains(m)) {
				this.genProblemAndQueries(m, p);
			}
		}
		
		String maxsatPath = Config.outDirName+File.separator+"problem.dimacs";
		p.storeAsDimacs(maxsatPath);
		String forwardQueries = Config.outDirName + File.separator +"fq.txt";

		try {
			PrintWriter pw = new PrintWriter(new File(forwardQueries));
			for(Map.Entry<jq_Method, Set<Integer>> fqEntry : fQueries.entrySet()){
				pw.println("// "+fqEntry.getKey());
				for(int fq : fqEntry.getValue())
					pw.println(fq);
			}
			pw.flush();
			pw.close();


			String backwardQueries = Config.outDirName + File.separator +"bq.txt";
			pw = new PrintWriter(new File(backwardQueries));
			for(Map.Entry<jq_Method, Set<Integer>> bqEntry : bQueries.entrySet()){
					pw.println("// "+bqEntry.getKey());
					for(int bq : bqEntry.getValue())
						pw.println(bq);
			}
			pw.flush();
			pw.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

		String problemDescPath = Config.outDirName+File.separator+"problemDesc.txt";
		p.storeVarDes(problemDescPath);
	}

	private void genProblemAndQueries(jq_Method m, Problem p) {
		Set<Integer> fqSet = new HashSet<Integer>();
		fQueries.put(m, fqSet);
		Set<Integer> bqSet = new HashSet<Integer>();
		bQueries.put(m, bqSet);
		ControlFlowGraph cfg = m.getCFG();
		Map<Register, Set<Quad>> defMap = new HashMap<Register, Set<Quad>>();
		Map<Register, Set<Quad>> nullDefMap = new HashMap<Register, Set<Quad>>();
		Map<Register, Set<Quad>> derefMap = new HashMap<Register, Set<Quad>>();

		// find all reference type definitions
		findAllDefsAndDerefs(cfg, defMap, nullDefMap, derefMap);

		// register all variables and least fixpoint constraints
		registerVarsAndLfp(p, cfg, defMap);

		// establish Dataflow equation
		generateDataflowConstraints(p, cfg, defMap);

		// generate query constraints
		for(Map.Entry<Register, Set<Quad>> nullDefEntry : nullDefMap.entrySet()){
			Register r = nullDefEntry.getKey();
			Set<Quad> derefs = derefMap.get(r);
			//Whether null def can reach any dereference
			for(Quad ndef : nullDefEntry.getValue()){
				FQuery fq = new FQuery();
				fq.r = r;
				fq.def = ndef;
				fqSet.add(p.registerVar(fq, fq.toString()));
				//lfp
				p.registerConstraint(1, EMPTY, toList(fq));
				if(derefs != null)
					for(Quad deref : derefs){
						InRDFact in = new InRDFact();
						in.reg = r;
						in.p = deref;
						in.def = ndef;
						// in => fq
						p.registerConstraint(-1, toList(fq), toList(in));
					}
			}
			//whether current dereference can be reached by any null def
			if(derefs != null)
				for(Quad deref : derefs){
					BQuery bq = new BQuery();
					bq.r = r;
					bq.deref = deref;
					bqSet.add(p.registerVar(bq, bq.toString()));
					p.registerConstraint(1, EMPTY, toList(bq));
					for(Quad ndef : nullDefEntry.getValue()){
						InRDFact in = new InRDFact();
						in.reg = r;
						in.p = deref;
						in.def = ndef;
						// in => fq
						p.registerConstraint(-1, toList(bq), toList(in));	
					}
				}
		}
	}

	private void generateDataflowConstraints(Problem p, ControlFlowGraph cfg,
			Map<Register, Set<Quad>> defMap) {
		for (BasicBlock bb : cfg.reversePostOrder()) {
			List<Quad> quads = bb.getQuads();
			if (quads.isEmpty())
				continue;

			// First quad in a bb
			Quad fq = quads.get(0);
			for (BasicBlock pbb : bb.getPredecessors()) {
				List<Quad> pquads = pbb.getQuads();
				if (pquads.size() == 0)
					continue;
				Quad lq = pquads.get(pquads.size() - 1);
				for (Map.Entry<Register, Set<Quad>> dEntry : defMap.entrySet()) {
					for (Quad dq : dEntry.getValue()) {
						// def reachable at the end of predecessor should be
						// reachable at the start of current bb.
						OutRDFact out = new OutRDFact();
						out.def = dq;
						out.p = lq;
						out.reg = dEntry.getKey();
						InRDFact in = new InRDFact();
						in.def = dq;
						in.p = fq;
						in.reg = dEntry.getKey();
						p.registerConstraint(-1, toList(in), toList(out));
					}
				}
			}

			// The rest of the quads on processing inSet
			for (int i = 1; i < quads.size(); i++) {
				Quad cq = quads.get(i);
				Quad pq = quads.get(i - 1);
				for (Map.Entry<Register, Set<Quad>> dEntry : defMap.entrySet()) {
					for (Quad def : dEntry.getValue()) {
						Register cr = dEntry.getKey();
						OutRDFact pOut = new OutRDFact();
						pOut.def = def;
						pOut.reg = cr;
						pOut.p = pq;

						InRDFact cIn = new InRDFact();
						cIn.def = def;
						cIn.reg = cr;
						cIn.p = cq;

						// p out => c in
						p.registerConstraint(-1, toList(cIn), toList(pOut));
					}
				}
			}

			// Transfer function
			for (int i = 0; i < quads.size(); i++) {
				Quad cq = quads.get(i);
				for (Map.Entry<Register, Set<Quad>> dEntry : defMap.entrySet()) {
					for (Quad def : dEntry.getValue()) {
						Register cr = dEntry.getKey();
						InRDFact cIn = new InRDFact();
						cIn.def = def;
						cIn.reg = cr;
						cIn.p = cq;

						OutRDFact cOut = new OutRDFact();
						cOut.def = def;
						cOut.reg = cr;
						cOut.p = cq;

						List<RegisterOperand> dfs = cq.getDefinedRegisters();
						boolean matched = false;
						// kill and gen
						for(RegisterOperand dro : dfs){
							if(dro.getRegister().equals(cr)){
								matched = true;
								// kill
								if (def != cq)
									p.registerConstraint(-1, EMPTY, toList(cOut));
								// gen
								else
									p.registerConstraint(-1, toList(cOut), EMPTY);
							}
						}

						if(!matched)
						{
							p.registerConstraint(-1, toList(cOut), toList(cIn));
						}
					}
				}
			}

		}
	}

	private void registerVarsAndLfp(Problem p, ControlFlowGraph cfg,
			Map<Register, Set<Quad>> defMap) {
		for (BasicBlock bb : cfg.reversePostOrder()) {
			if (bb.isEntry() || bb.isExit())
				continue;
			if (bb.getQuads().isEmpty())
				throw new RuntimeException(
						"Emtpy blocks besides entry and exit: " + bb + ".");
			for (Quad q : bb.getQuads()) {
				for (Map.Entry<Register, Set<Quad>> defEntry : defMap
						.entrySet()) {
					for (Quad def : defEntry.getValue()) {
						InRDFact irdf = new InRDFact();
						irdf.def = def;
						irdf.p = q;
						irdf.reg = defEntry.getKey();
						OutRDFact ordf = new OutRDFact();
						ordf.def = def;
						ordf.p = q;
						ordf.reg = defEntry.getKey();
						p.registerVar(irdf, irdf.toString());
						p.registerVar(ordf, ordf.toString());
						p.registerConstraint(1, EMPTY, toList(irdf));
						p.registerConstraint(1, EMPTY, toList(ordf));
					}
				}
			}
		}
	}

	class DefVisitor extends EmptyVisitor{
		Set<Register> nullRegs;
		
		public DefVisitor(){
			nullRegs = new ArraySet<Register>();
		}

		@Override
		public void visitALoad(Quad obj) {
			RegisterOperand ro = ALoad.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			Operand bo = ALoad.getBase(obj);
			if(!(bo instanceof RegisterOperand)) //slightly unsound, ignore the constant case
				return;
			RegisterOperand bro = (RegisterOperand)bo;
			Register br = bro.getRegister();
			CIObj brobj = cipa.pointsTo(br);
			if(cipa.pointsTo(brobj, null).pts.contains(null)){
				nullRegs.add(ro.getRegister());
			}
		}

		@Override
		public void visitGetfield(Quad obj) {
			RegisterOperand ro = Getfield.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			Operand bo = Getfield.getBase(obj);
			RegisterOperand bro = (RegisterOperand)bo;
			Register br = bro.getRegister();
			CIObj brobj = cipa.pointsTo(br);
			if(cipa.pointsTo(brobj, Getfield.getField(obj).getField()).pts.contains(null)){
				nullRegs.add(ro.getRegister());
			}
		}

		@Override
		public void visitGetstatic(Quad obj) {
			RegisterOperand ro = Getfield.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			if(cipa.pointsTo(Getstatic.getField(obj).getField()).pts.contains(null)){
				nullRegs.add(ro.getRegister());
			}
		}


		@Override
		public void visitMove(Quad obj) {
			RegisterOperand dsto = Move.getDest(obj);
			if(!dsto.getType().isReferenceType())
				return;
			Register dst = dsto.getRegister();
			Operand so = Move.getSrc(obj);
			if(so instanceof AConstOperand){
				AConstOperand aso = (AConstOperand)so;
				if(aso.getValue() == null)
					this.nullRegs.add(dst);
				return;
			}
			RegisterOperand sro = (RegisterOperand)so;
			Register sr = sro.getRegister();
			CIObj srobj = cipa.pointsTo(sr);
			if(srobj.pts.contains(null)){
				nullRegs.add(dsto.getRegister());
			}
		}
		
	}
	
	class DerefVisitor extends EmptyVisitor{
		public Register r = null;

		@Override
		public void visitALoad(Quad obj) {
			Operand base = ALoad.getBase(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			r = ro.getRegister();
		}

		@Override
		public void visitAStore(Quad obj) {
			Operand base = AStore.getBase(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			r = ro.getRegister();
		}

		@Override
		public void visitALength(Quad obj) {
			Operand base = ALength.getSrc(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			r = ro.getRegister();
		}

		@Override
		public void visitGetfield(Quad obj) {
			RegisterOperand ro = (RegisterOperand)Getfield.getBase(obj);
			r = ro.getRegister();		
		}

		
		@Override
		public void visitPutfield(Quad obj) {
			RegisterOperand ro = (RegisterOperand)Putfield.getBase(obj);
			r = ro.getRegister();		
		}
		
		@Override
		public void visitInvoke(Quad obj) {
			jq_Method target = Invoke.getMethod(obj).getMethod();
			if(!target.isStatic())
				r = Invoke.getParam(obj, 0).getRegister();
		}

		@Override
		public void visitReturn(Quad obj) {
			Operand o = Return.getSrc(obj);
			if(o instanceof RegisterOperand){
				RegisterOperand ro = (RegisterOperand)o;
				if(ro.getType().isReferenceType()){
					this.r = ro.getRegister();
				}
			}
		}
	
	}
	
	private void findAllDefsAndDerefs(ControlFlowGraph cfg,
			Map<Register, Set<Quad>> defMap, Map<Register, Set<Quad>> nullDefMap, Map<Register,Set<Quad>> derefMap) {
		for (BasicBlock bb : cfg.reversePostOrder()) {
			if (bb.isEntry() || bb.isExit())
				continue;
			for (Quad q : bb.getQuads()) {
				DefVisitor dv = new DefVisitor();
				q.accept(dv);
				
				Set<Register> nullDefs = dv.nullRegs;

				// Find the derefs for each register
				DerefVisitor v = new DerefVisitor();
				q.accept(v);
				if(v.r != null){
					Set<Quad> derefs = derefMap.get(v.r);
					if(derefs == null){
						derefs = new HashSet<Quad>();
						derefMap.put(v.r, derefs);
					}
					derefs.add(q);
				}
				
				List<RegisterOperand> gens = q.getDefinedRegisters();
				for (RegisterOperand ro : gens) {
					Register r = ro.getRegister();
					if (r.getType().isReferenceType()) {// run reach def on all
														// reference type
						Set<Quad> defSet = defMap.get(r);
						if (defSet == null) {
							defSet = new HashSet<Quad>();
							defMap.put(r, defSet);
						}
						defSet.add(q);
						if (nullDefs.contains(r)) {
							Set<Quad> nDefSet = nullDefMap.get(r);
							if (nDefSet == null) {
								nDefSet = new HashSet<Quad>();
								nullDefMap.put(r, nDefSet);
							}
							nDefSet.add(q);
						}
					}
				}
			}
		}
	}

	public static List<Object> toList(Object... oa) {
		List<Object> ret = new ArrayList<Object>();
		for (Object o : oa)
			ret.add(o);
		return ret;
	}
}

class RDFact {
	public Quad def;
	public Register reg;
	public Quad p;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((def == null) ? 0 : def.hashCode());
		result = prime * result + ((p == null) ? 0 : p.hashCode());
		result = prime * result + ((reg == null) ? 0 : reg.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RDFact other = (RDFact) obj;
		if (def == null) {
			if (other.def != null)
				return false;
		} else if (!def.equals(other.def))
			return false;
		if (p == null) {
			if (other.p != null)
				return false;
		} else if (!p.equals(other.p))
			return false;
		if (reg == null) {
			if (other.reg != null)
				return false;
		} else if (!reg.equals(other.reg))
			return false;
		return true;
	}

}

class FQuery{
	Register r;
	Quad def;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((def == null) ? 0 : def.hashCode());
		result = prime * result + ((r == null) ? 0 : r.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FQuery other = (FQuery) obj;
		if (def == null) {
			if (other.def != null)
				return false;
		} else if (!def.equals(other.def))
			return false;
		if (r == null) {
			if (other.r != null)
				return false;
		} else if (!r.equals(other.r))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return "NULL def "+def+" might be dereferenced.";
	}
}

class BQuery{
	Register r;
	Quad deref;
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((deref == null) ? 0 : deref.hashCode());
		result = prime * result + ((r == null) ? 0 : r.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BQuery other = (BQuery) obj;
		if (deref == null) {
			if (other.deref != null)
				return false;
		} else if (!deref.equals(other.deref))
			return false;
		if (r == null) {
			if (other.r != null)
				return false;
		} else if (!r.equals(other.r))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return "Deref "+deref+" might be reachable by a NULL reference.";
	}
}

class InRDFact extends RDFact {

	@Override
	public String toString() {
		return "Definition of " + reg +" " +def + " can reach the program point before "
				+ p + ".";
	}

}

class OutRDFact extends RDFact {

	@Override
	public String toString() {
		return "Definition of " + reg +" " +def + " can reach the program point after "
				+ p + ".";
	}

}