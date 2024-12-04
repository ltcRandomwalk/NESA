package chord.analyses.bigcode.connection.TopDown;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.CICGAnalysis;
import chord.analyses.alias.ICICG;
import chord.analyses.heapacc.DomE;
import chord.analyses.point.DomP;
import chord.analyses.bigcode.connection.VariablesPartition;
import chord.bddbddb.Rel.PairIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;


@Chord(name = "chooseQuads", 
		   produces = { "chosenQuads"} ,
			namesOfSigns = { "chosenQuads"},
			signs = { "P0"}
	)
public class ChooseQuads extends JavaAnalysis{

	private DomP domP;
	
	@Override
	public void run()
	{
//		Random r = new Random();
	
		domP = (DomP) ClassicProject.g().getTrgt("P");
		ClassicProject.g().runTask(domP);
		
		CICGAnalysis cicgAnalysis = (CICGAnalysis) ClassicProject.g()
				.getTrgt("cicg-java");
		ClassicProject.g().runTask(cicgAnalysis);
		ICICG cicg = cicgAnalysis.getCallGraph();
		
		Set<jq_Method> reachableMethods = cicg.getNodes();	
		ProgramRel chosenQuads= (ProgramRel) ClassicProject.g().getTrgt("chosenQuads");
		chosenQuads.zero();
		PrintWriter chosenQuadsOut  = OutDirUtils.newPrintWriter("chosenQuads");
			
		for (Inst i: domP)
		{	
			if (!(i instanceof Quad)) continue;
		//	int random = r.nextInt(10);
			Quad q = (Quad) i;
			if (q.getMethod().getDeclaringClass().toString().startsWith("java") || 
				q.getMethod().getDeclaringClass().toString().startsWith("sun") || 
				q.getMethod().getDeclaringClass().toString().startsWith("com.sun") ||
				q.getMethod().getDeclaringClass().toString().startsWith("javax") ||
				q.getMethod().getDeclaringClass().toString().startsWith("org.apache.harmony") ||
				q.getMethod().getDeclaringClass().toString().startsWith("com.ibm")
				) 
			{
				continue;
			}
			if (reachableMethods.contains(q.getMethod()))
			{
				if (!(q.getOperator() instanceof Operator.ALoad || 
				       q.getOperator() instanceof Operator.AStore ||
				       q.getOperator() instanceof Operator.Putfield ||
				       q.getOperator() instanceof Operator.Getfield)) 
						continue;
				
				chosenQuads.add(q);
				try
				{
					chosenQuadsOut.println(q.toVerboseStr());
				}
				catch (Exception e)
				{
					System.out.println("caught exception in chooseQuads");
					System.out.println(q);
				}
			}
		}
		chosenQuads.save();
		chosenQuadsOut.close();
	}
}
