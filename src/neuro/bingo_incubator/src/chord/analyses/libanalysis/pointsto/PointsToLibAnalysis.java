package chord.analyses.libanalysis.pointsto;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.net.Socket;
import java.net.ServerSocket;

import joeq.Class.jq_ClassInitializer;
import joeq.Class.jq_Class;
import joeq.Class.jq_Type;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import chord.util.Execution;
import chord.analyses.absmin.Abstraction;
import chord.analyses.absmin.AbstractionFactory;
import chord.analyses.absmin.AbstractionMinimizer;
import chord.analyses.absmin.Query;
import chord.analyses.absmin.GenericQueryFactory;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.alias.CtxtsAnalysis;
import chord.bddbddb.Dom;
import chord.bddbddb.Rel.AryNIterable;
import chord.bddbddb.Rel.IntPairIterable;
import chord.bddbddb.Rel.PairIterable;
import chord.bddbddb.Rel.TrioIterable;
import chord.bddbddb.Rel.QuadIterable;
import chord.bddbddb.Rel.PentIterable;
import chord.bddbddb.Rel.HextIterable;
import chord.bddbddb.Rel.RelView;
import chord.analyses.alloc.DomH;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.heapacc.DomE;
import chord.analyses.libanalysis.AbstractLibAnalysis;
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.program.Program;
import chord.project.Chord;
import chord.project.Messages;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Project;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Mode;
import chord.project.analyses.parallelizer.Scenario;
import chord.util.IndexMap;
import chord.util.ArraySet;
import chord.util.graph.IGraph;
import chord.util.graph.MutableGraph;
import chord.util.tuple.integer.IntPair;
import chord.util.tuple.object.Pair;
import chord.util.tuple.object.Trio;
import chord.util.Utils;
import chord.util.StopWatch;
import chord.bddbddb.Rel.IntAryNIterable;

@Chord(name = "pointstolibanalysis")
public class PointsToLibAnalysis extends AbstractLibAnalysis {
	
    @Override
    public GenericQueryFactory getQueryFactory() {
        DomV domV = (DomV) ClassicProject.g().getTask("V");
        ClassicProject.g().runTask(domV);
        DomH domH = (DomH) ClassicProject.g().getTask("H");
        ClassicProject.g().runTask(domH);
        return new GenericQueryFactory(new ProgramDom[] { domV, domH });
    }

	@Override
	public Set<String> getErrQueries() {
		ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("RVH1");
		rel.load();
		Set<String> queries = new HashSet<String>();
		IntAryNIterable tuples = rel.getAryNIntTuples();
		for (int[] tuple : tuples) {
			queries.add(qFactory.encode(tuple));
		}
		rel.close();
		return queries;
	}
	
	@Override
	public Set<String> getAllQueries() {
		ClassicProject.g().resetTaskDone("allVH-Fil-dlog");
		ClassicProject.g().runTask("allVH-Fil-dlog");
		ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("allVHFil"); 
		rel.load();
		Set<String> queries = new HashSet<String>();
		IntAryNIterable tuples = rel.getAryNIntTuples();
		for (int[] tuple : tuples) {
			queries.add(qFactory.encode(tuple));
		}
		rel.close();
		return queries;
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
			
		if (this.xmlToHtmlTask != null) {
			DomM domM = (DomM) ClassicProject.g().getTrgt("M");
			DomV domV = (DomV) ClassicProject.g().getTrgt("V");
			DomH domH = (DomH) ClassicProject.g().getTrgt("H");

			domM.saveToXMLFile();
			domV.saveToXMLFile();
			domH.saveToXMLFile();

			OutDirUtils.copyResourceByName("web/style.css");
			OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/var/Vlist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/var/V.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
			OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/qresults"+fileNameAppend+".dtd");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/qresults"+fileNameAppend+".xml");
			OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/qresults"+fileNameAppend+".xsl");

			OutDirUtils.runSaxon("qresults"+fileNameAppend+".xml", "qresults"+fileNameAppend+".xsl");

			Program.g().HTMLizeJavaSrcFiles();
		}
	}
}
