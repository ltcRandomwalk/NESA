package chord.analyses.libanalysis.mustalias;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import chord.analyses.absmin.GenericQueryFactory;
import chord.analyses.absmin.Query;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.project.analyses.ProgramDom;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;

@Chord(	name = "mustaliasoracle-java")
public class MustAliasOracle extends JavaAnalysis {
	private GenericQueryFactory qFactory;
	
	private Set<String> readQueries(Set<Pair<Quad, Quad>> set) {
		Set<String> queries = new HashSet<String>(set.size());
		for (Pair<Quad, Quad> p : set) {
			queries.add(qFactory.encode(new Object[] { p.val0, p.val1 }));
		}
		return queries;
	}

	@Override
	public void run() {
        DomI domI = (DomI) ClassicProject.g().getTask("I");
        ClassicProject.g().runTask(domI);
        DomH domH = (DomH) ClassicProject.g().getTask("H");
        ClassicProject.g().runTask(domH);
		qFactory = new GenericQueryFactory(new ProgramDom[] { domI, domH });

		ClassicProject.g().resetTaskDone("mustalias-java");
		ClassicProject.g().runTask("mustalias-java");

		MustAliasAnalysis tsAnalysis = (MustAliasAnalysis) ClassicProject.g().getTrgt("mustalias-java");

		Set<String> errQ = readQueries(tsAnalysis.getErrQueries());
		{
			PrintWriter out = Utils.openOut(Execution.v().path("ErrQueries.txt"));
			for(String query : errQ) {
				if(query.equals(""))
					continue;
				Query q = qFactory.create(query);
				out.println(q.toString());
			}
			out.close();
		}

		Set<String> allQ = readQueries(tsAnalysis.getAllQueries());
		{
			PrintWriter out = Utils.openOut(Execution.v().path("AllQueries.txt"));
			for(String query : allQ) {
				if(query.equals(""))
					continue;
				Query q = qFactory.create(query);
				out.println(q.toString());
			}
			out.close();
        }

		{
			PrintWriter out = Utils.openOut(Execution.v().path("NumQueries.txt"));
			out.println("AllQueries: " + allQ.size());
			out.println("ErrQueries: " + errQ.size());
			out.close();
		}
	}
}
