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
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALength;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor.EmptyVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.CIPAAnalysis;
import chord.analyses.alias.ICICG;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.qmaxsat.Problem;

@Chord(name = "nullderef-maxsatgen-java",consumes = {"checkExcludedM" })
public class NullDereferenceGenerator extends JavaAnalysis {
	public final static List<Object> EMPTY = new ArrayList<Object>();
	private Map<jq_Method, Set<Integer>> queries;
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
		queries = new HashMap<jq_Method, Set<Integer>>();
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
		String forwardQueries = Config.outDirName + File.separator +"q.txt";

		try {
			PrintWriter pw = new PrintWriter(new File(forwardQueries));
			for(Map.Entry<jq_Method, Set<Integer>> fqEntry : queries.entrySet()){
				pw.println("// "+fqEntry.getKey());
				for(int fq : fqEntry.getValue())
					pw.println(fq);
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
		Set<Integer> qSet = new HashSet<Integer>();
		queries.put(m, qSet);
		ControlFlowGraph cfg = m.getCFG();
		
		Set<Register> refRegs =  new HashSet<Register>();

		RegisterFactory rf = cfg.getRegisterFactory();
		for(int i = 0; i < rf.size(); i++){
			Register r = rf.get(i);
			if(r.getType().isReferenceType()){
				refRegs.add(r);
			}
		}

		// register all variables and least fixpoint constraints
		registerVarsAndLfp(p, cfg, refRegs);

		// establish Dataflow equation
		generateDataflowConstraints(p, cfg, refRegs);

		// generate query constraints
		for (BasicBlock bb : cfg.reversePostOrder()) {
			List<Quad> quads = bb.getQuads();
			for(Quad q : quads){
				DerefVisitor dev = new DerefVisitor();
				q.accept(dev);
				for(Register r : dev.regs){
					Query query = new Query();
					query.deref = q;
					qSet.add(p.registerVar(query, query.toString()));
					p.registerConstraint(1, EMPTY, toList(query));
					InFact in = new InFact();
					in.p = q;
					in.reg = r;
					p.registerConstraint(-1, toList(query), toList(in));
				}
			}
		}

	}

	private void generateDataflowConstraints(Problem p, ControlFlowGraph cfg, Set<Register> refRegs) {
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
				for(Register r : refRegs){
					// def reachable at the end of predecessor should be
					// reachable at the start of current bb.
					OutFact out = new OutFact();
					out.p = lq;
					out.reg = r;
					InFact in = new InFact();
					in.p = fq;
					in.reg = r;
					p.registerConstraint(-1, toList(in), toList(out));
				}
			}

			// The rest of the quads on processing inSet
			for (int i = 1; i < quads.size(); i++) {
				Quad cq = quads.get(i);
				Quad pq = quads.get(i - 1);
				for (Register r : refRegs) {
					OutFact pOut = new OutFact();
					pOut.reg = r;
					pOut.p = pq;

					InFact cIn = new InFact();
					cIn.reg = r;
					cIn.p = cq;

					// p out => c in
					p.registerConstraint(-1, toList(cIn), toList(pOut));
				}
			}

			// Transfer function
			for (int i = 0; i < quads.size(); i++) {
				Quad cq = quads.get(i);
				for (Register r : refRegs) {
					DefVisitor dv = new DefVisitor(r, p);
					cq.accept(dv);
					if(!dv.processed){
						InFact in = new InFact();
						in.p = cq;
						in.reg = r;
						OutFact out = new OutFact();
						out.p = cq;
						out.reg = r;
						p.registerConstraint(-1, toList(out), toList(in));
					}
				}
			}
		}
	}

	private void registerVarsAndLfp(Problem p, ControlFlowGraph cfg, Set<Register> refRegs) {
		for (BasicBlock bb : cfg.reversePostOrder()) {
			if (bb.isEntry() || bb.isExit())
				continue;
			if (bb.getQuads().isEmpty())
				throw new RuntimeException(
						"Emtpy blocks besides entry and exit: " + bb + ".");
			for (Quad q : bb.getQuads()) {
				for(Register r : refRegs){
					InFact irdf = new InFact();
					irdf.p = q;
					irdf.reg = r;
					OutFact ordf = new OutFact();
					ordf.p = q;
					ordf.reg = r;
					p.registerVar(irdf, irdf.toString());
					p.registerVar(ordf, ordf.toString());
					p.registerConstraint(1, EMPTY, toList(irdf));
					p.registerConstraint(1, EMPTY, toList(ordf));
				}	
			}
		}
	}

	class DefVisitor extends EmptyVisitor{
		Register r;
		Problem p;
		boolean processed;
		
		public DefVisitor(Register r, Problem p){
			this.r = r;
			this.p = p;
			this.processed = false;
		}
		
		private void handleMoveNull(Register cr, Register qr, Quad q){
			if(cr.equals(qr)){
				OutFact out = new OutFact();
				out.p = q;
				out.reg = cr;
				p.registerConstraint(-1, toList(out), EMPTY);
			}else{
				InFact in = new InFact();
				in.p = q;
				in.reg = cr;
				OutFact out = new OutFact();
				out.p = q;
				out.reg = cr;
				p.registerConstraint(-1, toList(out), toList(in));
			}		
		}
		
		private void handleKill(Register cr, Register qr, Quad q){
			if(cr.equals(qr)){
				OutFact out = new OutFact();
				out.p = q;
				out.reg = cr;
				p.registerConstraint(-1, EMPTY, toList(out));
			}else{
				InFact in = new InFact();
				in.p = q;
				in.reg = cr;
				OutFact out = new OutFact();
				out.p = q;
				out.reg = cr;
				p.registerConstraint(-1, toList(out), toList(in));
			}				
		}
		
		@Override
		public void visitALoad(Quad obj) {
			RegisterOperand ro = ALoad.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			this.processed = true;
			this.handleMoveNull(r, ro.getRegister(), obj);
		}

		@Override
		public void visitGetfield(Quad obj) {
			RegisterOperand ro = Getfield.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			this.processed = true;
			this.handleMoveNull(r, ro.getRegister(), obj);
		}

		@Override
		public void visitGetstatic(Quad obj) {
			RegisterOperand ro = Getfield.getDest(obj);
			if(!ro.getType().isReferenceType())
				return;
			this.processed = true;
			this.handleMoveNull(r, ro.getRegister(), obj);
		}


		@Override
		public void visitMove(Quad obj) {
			RegisterOperand dsto = Move.getDest(obj);
			if(!dsto.getType().isReferenceType())
				return;
			this.processed = true;
			Register dst = dsto.getRegister();
			Operand so = Move.getSrc(obj);
			if(so instanceof AConstOperand){
				AConstOperand aso = (AConstOperand)so;
				if(aso.getValue() == null)
					this.handleMoveNull(r, dst, obj);
				else
					this.handleKill(r, dst, obj);
				return;
			}
			RegisterOperand sro = (RegisterOperand)so;
			Register sr = sro.getRegister();
			if(r.equals(dst)){
				InFact in = new InFact();
				in.p = obj;
				in.reg = sr;
				OutFact out = new OutFact();
				out.p = obj;
				out.reg = r;
				//insrc => outDst
				p.registerConstraint(-1, toList(out), toList(in));
			}else{
				InFact in = new InFact();
				in.p = obj;
				in.reg = r;
				OutFact out = new OutFact();
				out.p = obj;
				out.reg = r;
				p.registerConstraint(-1, toList(out), toList(in));
			}
		}

		@Override
		public void visitNew(Quad obj) {
			this.processed = true;
			RegisterOperand ro = New.getDest(obj);
			this.handleKill(r, ro.getRegister(), obj);
		}

		@Override
		public void visitNewArray(Quad obj) {
			this.processed = true;
			RegisterOperand ro = NewArray.getDest(obj);
			this.handleKill(r, ro.getRegister(), obj);
		}

		@Override
		public void visitMultiNewArray(Quad obj) {
			this.processed = true;
			RegisterOperand ro = MultiNewArray.getDest(obj);
			this.handleKill(r, ro.getRegister(), obj);
		}

		@Override
		public void visitInvoke(Quad obj) {
			RegisterOperand ro = Invoke.getDest(obj);
			if(ro != null && ro.getType().isReferenceType()){
				this.processed = true;
				this.handleKill(r, ro.getRegister(), obj);
			}
		}	
		
	}
	
	class DerefVisitor extends EmptyVisitor{
		public Set<Register> regs = new HashSet<Register>();

		@Override
		public void visitALoad(Quad obj) {
			Operand base = ALoad.getBase(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			regs.add(ro.getRegister());
		}

		@Override
		public void visitAStore(Quad obj) {
			Operand base = AStore.getBase(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			regs.add(ro.getRegister());
		}

		@Override
		public void visitALength(Quad obj) {
			Operand base = ALength.getSrc(obj);
			if(!(base instanceof RegisterOperand))
					return;
			RegisterOperand ro = (RegisterOperand)base;
			regs.add(ro.getRegister());
		}

		@Override
		public void visitGetfield(Quad obj) {
			RegisterOperand ro = (RegisterOperand)Getfield.getBase(obj);
			regs.add(ro.getRegister());
		}

		
		@Override
		public void visitPutfield(Quad obj) {
			RegisterOperand ro = (RegisterOperand)Putfield.getBase(obj);
			regs.add(ro.getRegister());
		}
		
		@Override
		public void visitInvoke(Quad obj) {
			ParamListOperand pList = Invoke.getParamList(obj);
			for(int i = 0; i < pList.length(); i++){
				RegisterOperand ro = pList.get(i);
				if(ro.getType().isReferenceType()){
					regs.add(ro.getRegister());
				}
			}
		}

		@Override
		public void visitReturn(Quad obj) {
			Operand o = Return.getSrc(obj);
			if(o instanceof RegisterOperand){
				RegisterOperand ro = (RegisterOperand)o;
				if(ro.getType().isReferenceType()){
					regs.add(ro.getRegister());
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

class DFFact {
	public Register reg;
	public Quad p;

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		DFFact other = (DFFact) obj;
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

class Query{
	Quad deref;
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((deref == null) ? 0 : deref.hashCode());
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
		Query other = (Query) obj;
		if (deref == null) {
			if (other.deref != null)
				return false;
		} else if (!deref.equals(other.deref))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return "Deref "+deref+" might be performed on a NULL reference.";
	}
}

class InFact extends DFFact {

	@Override
	public String toString() {
		return "Register " + reg +" might be null at the program point before "
				+ p + ".";
	}

}

class OutFact extends DFFact {

	@Override
	public String toString() {
		return "Register " + reg + " might be null at the program point after "
				+ p + ".";
	}

}