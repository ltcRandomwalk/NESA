package chord.project.analyses.mln;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jwutil.io.SystemProperties;
import net.sf.bddbddb.Attribute;
import net.sf.bddbddb.Domain;
import net.sf.bddbddb.InferenceRule;
import net.sf.bddbddb.Relation;
import net.sf.bddbddb.RuleTerm;
import net.sf.bddbddb.Solver;
import net.sf.bddbddb.Variable;
import chord.bddbddb.Dom;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.TaskParser;
import chord.project.analyses.DlogAnalysis;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 * Simple dlog => mln convertor. Needs more thoughts on negations and wild cards
 * @author xin
 *
 */
@Chord(name = "mlnconvertor")
public class DlogMlnConvertor extends JavaAnalysis {
	public final static String DLOG = "chord.mln.convertor.dlog";
	private Solver solver;
	private String dlogName;
	private PrintWriter out;
	private List rules;
	private Set<String> inputRelations;
	private Set<String> noninputRelations; 
	private Set<String> doms;
	private List<String> convertedRules;
	
	@Override
	public void run() {
		dlogName = System.getProperty(DLOG);
		TaskParser taskParser = new TaskParser();
		if (!taskParser.run())
			throw new RuntimeException("Task parsing not successful.");
		//Try to generate empty relations and doms that needed. Note if multiple tasks can produce
		//the tasks, you need to specify the one in the command line
		List<String> relConsumed = taskParser.getNameToConsumeNamesMap().get(dlogName);
		if(relConsumed!=null)
		for(String s : relConsumed){
			Object o = ClassicProject.g().getTrgt(s);
			if(o instanceof Dom){
				Dom d = (Dom)o;
				try {
					d.save(Config.bddbddbWorkDirName, Config.saveDomMaps);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			else{
				ProgramRel rel = (ProgramRel)o;
				rel.zero();
				rel.save();
			}
		}
		ClassicProject.g().runTask(dlogName);
		DlogAnalysis dlogAnalysis = taskParser.getNameToDlogTaskMap().get(dlogName);
		String fileName = dlogAnalysis.getFileName();
		System.setProperty("verbose", "" + Config.verbose);
		System.setProperty("bdd", "j");
		System.setProperty("basedir", Config.bddbddbWorkDirName);
		String solverName = SystemProperties.getProperty("solver", "net.sf.bddbddb.BDDSolver");

		initOutput();

		try {
			solver = (Solver) Class.forName(solverName).newInstance();
			solver.load(fileName);
			rules = solver.getRules();
			this.analyze();
			this.genPredicateSchema();
			out.println();
			this.genRules();
			closeOutput();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void analyze(){
		inputRelations = new HashSet<String>();
		noninputRelations = new HashSet<String>();	
		convertedRules = new ArrayList<String>();
		doms = new HashSet<String>();
		
		for(Object o : rules){
			InferenceRule r = (InferenceRule)o;
			for(Object o1 : r.getVariables()){
				Variable v = (Variable)o1;
				Domain d = v.getDomain();
				doms.add("dom"+"_"+d.toString()+"("+d.toString().toLowerCase()+"_dom)");
			}
			List<String> lhs = new ArrayList<String>();
			for(Object o1 : r.getSubgoals()){
				RuleTerm rt = (RuleTerm)o1;
				inputRelations.add(this.relationToPredicateString(rt.getRelation()));
				String crts = rt.toString();
				lhs.add(crts);
			}
			String rhs = r.getHead().toString();
			String hr = this.relationToPredicateString(r.getHead().getRelation());
			noninputRelations.add(hr);
			//convertedRules.add(this.commaList(lhs)+" => " + rhs+".");
			convertedRules.add("1 " + this.commaList(lhs)+" => " + rhs);
		}
		inputRelations.removeAll(noninputRelations);
	}
	
	private void initOutput(){
		try {
			out = new PrintWriter(new File(Config.outDirName + File.separator + dlogName+".mln"));
			System.out.println("All input relations are treated as closed-world predicates by default! Adjustment"
					+ "might be needed");
		} catch (FileNotFoundException e) {
			throw new RuntimeException();
		}
	}
	
	private void genPredicateSchema(){
		out.println("// domains");
		for(String s : this.doms)
			out.println("*"+s);
		out.println("// input predicates");
		for(String s : this.inputRelations){
			out.println("*"+s);
		}
		out.println();
		out.println("// non-input predicates, which can appear on the lhs of each rule");
		for(String s : this.noninputRelations)
			out.println(s);
	}
	
	private void genRules(){
		//out.println("// rules converted from dlog, which are all hard");
		out.println("// hard rules converted from dlog into soft rules");
		for(String s  : this.convertedRules)
			out.println(s);
	}
	
	private void closeOutput(){
		out.flush();
		out.close();
	}
	
	private String relationToPredicateString(Relation r){
		String rName = r.toString();
		StringBuffer sb = new StringBuffer(rName.replaceAll("!", ""));
		sb.append("(");
		List attrs = r.getAttributes();
		List<String> parts = new ArrayList<String>();
		for(int i = 0; i < attrs.size() ;i ++){
			Attribute attr = (Attribute)attrs.get(i);
			parts.add(attr.getDomain().toString().toLowerCase()+"_dom");
		}
		sb.append(commaList(parts));
		sb.append(")");
		return sb.toString();
	}

	public static String commaList(List<String> parts) {
		return join(", ", parts);
	}
	
	public static String join(String sep, List<String> parts) {
		StringBuilder sb = new StringBuilder("");
		for(int i=0; i<parts.size(); i++) {
			sb.append(parts.get(i));
			if(i != parts.size()-1) sb.append(sep);
		}
		return sb.toString();
	}
}
