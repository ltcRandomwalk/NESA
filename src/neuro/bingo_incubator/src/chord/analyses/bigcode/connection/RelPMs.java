package chord.analyses.bigcode.connection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.ICICG;
import chord.program.visitors.IInstVisitor;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "PMs",
		sign = "P0,M0:P0xM0"
	)

public class RelPMs extends ProgramRel implements IInstVisitor {

	private jq_Method ctnrMethod;
	private ICICG cicg;
	private Set<jq_Method> visited = new HashSet<jq_Method>(); 
	public void visit(jq_Class c) { }
	public void visit(jq_Method m) {
		ctnrMethod = m;
	}
	
	
	public void visit(Quad q) {
		
		LinkedList<jq_Method> callers = new LinkedList<jq_Method>();
		Set<jq_Method> addedCallers = new HashSet<jq_Method>();
		callers.add(ctnrMethod);
		cicg = getCallGraph();
		while(!callers.isEmpty())
		{
			jq_Method caller = callers.remove();
			//if(visited.contains(caller) == false)
			//{
				//visited.add(caller);
				add(q, caller);
				for  (Quad invokeQuad : cicg.getCallers(caller))
				{
					jq_Method m = invokeQuad.getMethod();
					if (addedCallers.contains(m))
					{
						addedCallers.add(m);
						callers.add(m);
					}
				}
			//}	
		}
	}
	
	public ICICG getCallGraph() {
		if (cicg == null) {
			CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g()
					.getTrgt("cicg-java");
			ClassicProject.g().runTask(cicgAnalysis);
			cicg = cicgAnalysis.getCallGraph();
		}
		return cicg;
	}
}
