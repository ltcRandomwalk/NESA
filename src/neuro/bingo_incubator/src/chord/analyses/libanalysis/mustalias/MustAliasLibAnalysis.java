package chord.analyses.libanalysis.mustalias;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.absmin.Abstraction;
import chord.analyses.absmin.AbstractionFactory;
import chord.analyses.absmin.AbstractionMinimizer;
import chord.analyses.absmin.Query;
import chord.analyses.absmin.GenericQueryFactory;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.type.DomT;
import chord.analyses.var.DomV;
import chord.analyses.libanalysis.AbstractLibAnalysis;
import chord.analyses.libanalysis.MethodAbstractionFactory;
import chord.analyses.libanalysis.MethodAbstractionFactory.MethodAbstraction;
import chord.bddbddb.Dom;
import chord.bddbddb.Rel.AryNIterable;
import chord.program.Program;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.OutDirUtils;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Mode;
import chord.project.analyses.parallelizer.Scenario;
import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;

@Chord(name = "mustaliaslibanalysis")
public class MustAliasLibAnalysis extends AbstractLibAnalysis {

	private Set<String> readQueries(Set<Pair<Quad, Quad>> set) {
        Set<String> queries = new HashSet<String>(set.size());
        for (Pair<Quad, Quad> p : set) {
            queries.add(qFactory.encode(new Object[] { p.val0, p.val1 }));
        }
        return queries;
	}

	@Override
	public GenericQueryFactory getQueryFactory() {
        DomI domI = (DomI) ClassicProject.g().getTask("I");
        ClassicProject.g().runTask(domI);
        DomH domH = (DomH) ClassicProject.g().getTask("H");
        ClassicProject.g().runTask(domH);
        return new GenericQueryFactory(new ProgramDom[] { domI, domH });
	}
		
	@Override
	public Set<String> getErrQueries() {
        MustAliasAnalysis tsAnalysis = (MustAliasAnalysis) ClassicProject.g().getTrgt("mustalias-java");
		return readQueries(tsAnalysis.getErrQueries());
	}

	@Override
	public Set<String> getAllQueries() {
        MustAliasAnalysis tsAnalysis = (MustAliasAnalysis) ClassicProject.g().getTrgt("mustalias-java");
		return readQueries(tsAnalysis.getAllQueries());
	}

	@Override
	public void queriesToXML(Set<String> queries, String fileNameAppend) {
		// Save to disk
		PrintWriter out = Utils.openOut(this.X.path("queries"+fileNameAppend+".xml"));
		out.println("<queries>");                
		for (String y : queries) {
			Query q = qFactory.create(y);
			out.println("<query " + q.encodeForXML() + "/>");
		}
		out.println("</queries>");

		out.close();
		out.flush();

		if(this.xmlToHtmlTask != null){
			DomM domM = (DomM) ClassicProject.g().getTrgt("M");
			DomI domI = (DomI) ClassicProject.g().getTrgt("I");
			DomH domH = (DomH) ClassicProject.g().getTrgt("H");

			domM.saveToXMLFile();
			domI.saveToXMLFile();
			domH.saveToXMLFile();

			OutDirUtils.copyResourceByName("web/style.css");
			OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/invk/Ilist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/invk/I.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/qresults"+fileNameAppend+".dtd");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/qresults"+fileNameAppend+".xml");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/qresults"+fileNameAppend+".xsl");

			OutDirUtils.runSaxon("qresults"+fileNameAppend+".xml", "qresults"+fileNameAppend+".xsl");

			Program.g().HTMLizeJavaSrcFiles();
		}
	}
}
