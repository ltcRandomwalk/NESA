package chord.analyses.libanalysis;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;

import chord.util.Execution;
import chord.util.Utils;
import chord.util.tuple.object.Pair;
import chord.analyses.absmin.Abstraction;
import chord.analyses.absmin.AbstractionMinimizer;
import chord.analyses.absmin.Query;
import chord.analyses.absmin.GenericQueryFactory;
import chord.analyses.absmin.ScenarioKind;
import chord.analyses.method.DomM;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.parallelizer.JobDispatcher;
import chord.project.analyses.parallelizer.Mode;
import chord.project.analyses.parallelizer.ParallelAnalysis;
import chord.project.analyses.parallelizer.Scenario;
import chord.analyses.libanalysis.MethodAbstractionFactory.MethodAbstraction;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import joeq.Compiler.Quad.Quad;

public abstract class AbstractLibAnalysis extends ParallelAnalysis {
    protected MethodAbstractionFactory mFactory;
    protected Set<MethodAbstraction> mSet = new HashSet<MethodAbstraction>();
    protected GenericQueryFactory qFactory;
	private Set<String> topY, botY;

    protected boolean isScan;
    protected String staticAnalysis;
    protected String sepMajor;
    protected String sepMinor;
    protected boolean switchToRealCase;
    protected String xmlToHtmlTask;
    protected boolean sampleQueries;
    protected int sampleSize;
    
    public MethodAbstractionFactory getMethodAbstractionFactory() {
		return mFactory;
	}

	public String getSepMajor() {
		return sepMajor;
	}

	public String getSepMinor() {
		return sepMinor;
	}

	@Override
	public void init() {
        X = new Execution(getName());
        this.sepMajor         = new String("##");
        this.sepMinor         = new String("<<,>>");
        this.isScan           = X.getStringArg("coarseningStrategy", "active").equals("scan");
        this.staticAnalysis   = X.getStringArg("staticAnalysis", "");
        this.switchToRealCase = X.getBooleanArg("switchToReal", false);
        this.xmlToHtmlTask    = X.getStringArg("xmlToHtmlTask", null);
        this.sampleQueries    = X.getBooleanArg("sampleQueries", false);
        this.sampleSize       = X.getIntArg("sampleSize", 100);

		DomM domM = (DomM) ClassicProject.g().getTask("M");
		ClassicProject.g().runTask(domM);
		mFactory = new MethodAbstractionFactory(domM);
		qFactory = getQueryFactory();

        ClassicProject.g().runTask("trackedM-dlog");
        // Excluded Methods
        {
            ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt("trackedM");
			rel.load();
            System.out.println(rel.size());
            Iterable<jq_Method> result = rel.getAry1ValTuples();
            for (jq_Method m : result) {
                mSet.add(mFactory.create(m));
            }
            rel.close();
        }
	}
	
	public String[] encodeX(Set<? extends Abstraction> X) {
        String[] enc = new String[X.size()];
        //buf.append(defaults);
        int c = 0;
        for (Abstraction a : X) {
            enc[c] = new String(a.encode());
            c++;
        }
        return enc;
    }
	
	@Override
	public String apply(String line) {
		// If no queries contained is incoming request, we assume that all valid queries are to be returned
		X.logs("Setting methods to be treated as no-ops");
		Set<String> queries;
		Set<MethodAbstraction> abstractions = new HashSet<MethodAbstraction>();
		Scenario inScenario = new Scenario(line,sepMajor);

		if (inScenario.getType().equalsIgnoreCase(ScenarioKind.COARSEN.toString())) {
			ClassicProject.g().resetTaskDone("trackedM-dlog");
			ClassicProject.g().runTask("trackedM-dlog");
			ProgramRel relM = (ProgramRel)ClassicProject.g().getTrgt("trackedM");
			relM.load();

			if (!inScenario.getIn().equalsIgnoreCase("")) {
				for (String s : Abstraction.splitAbstractions(inScenario.getIn(),sepMinor)) {
					MethodAbstraction a = mFactory.create(s);
					abstractions.add(a);
				}
				for (MethodAbstraction m : abstractions) {
					if (m.getLevel() == 1)
						relM.remove(m.getAbs());
				}
			}
			relM.save();

		} else if (inScenario.getType().equalsIgnoreCase(ScenarioKind.SWITCHTOREAL.toString())) {
			ClassicProject.g().resetTaskDone("libM");
			ClassicProject.g().runTask("libM");
			ProgramRel relM = (ProgramRel)ClassicProject.g().getTrgt("libM");
			relM.load();

			if (!inScenario.getIn().equalsIgnoreCase("")) {
				for (String s : Abstraction.splitAbstractions(inScenario.getIn(),sepMinor)) {
					MethodAbstraction a = mFactory.create(s);
					abstractions.add(a);
				}
				for (MethodAbstraction m : abstractions) {
					if (m.getLevel() == 1)
						relM.remove(m.getAbs());
				}
			}
			relM.save();
		} else {    
			return inScenario.toString();
		}

		ClassicProject.g().resetTaskDone(this.staticAnalysis);
		ClassicProject.g().runTask(this.staticAnalysis);

		Set<String> allUnproven = getErrQueries();

		if (inScenario.getOut().equalsIgnoreCase("")) {
			String[] arr = allUnproven.toArray(new String[0]);
			inScenario.setOut(Query.concatQueries(arr, sepMinor));
			return inScenario.toString();
		}
		queries = new HashSet<String>(Arrays.asList(Query.splitQueries(inScenario.getOut(),sepMinor)));

		allUnproven.retainAll(queries);

		inScenario.setOut(Query.concatQueries(allUnproven.toArray(new String[0]),sepMinor));
		return inScenario.toString();
	}
	
	@Override
	public JobDispatcher getJobDispatcher() {
		return new AbstractionMinimizer(isScan, (Set<Query>) null, mSet, qFactory, mFactory, this,
				sepMajor, sepMinor, xmlToHtmlTask, switchToRealCase, sampleQueries, sampleSize) {
			Set<String> topY, botY;
			@Override
			public void doneTopUnprovenQueries(Set<String> topY) {
				this.topY = topY;
			}
			@Override
			public void doneBotUnprovenQueries(Set<String> botY) {
				this.botY = botY;
			}
			@Override
			public void doneTrackedQueries(Set<String> trackedY) {
				// note: topY and botY cleared by abstraction minimizer after call to doneTrackedQueries
				// hence print results here.

				PrintWriter out;

				out = Utils.openOut(X.path("TrackedQueries.txt"));
				for (String query : trackedY) {
					Query q = qFactory.create(query);
					out.println(q.toString());
				}
				out.close();

				queriesToXML(topY, "B");

				out = Utils.openOut(X.path("BestUnprovenQueries.txt"));
				for (String query : topY) {
					Query q = qFactory.create(query);
					out.println(q.toString());
				}
				out.close();

				out = Utils.openOut(X.path("WorstUnprovenQueries.txt"));
				for (String query : botY) {
					Query q = qFactory.create(query);
					out.println(q.toString());
				}
				out.close();

				Set<String> allQueries = getAllQueries();
				out = Utils.openOut(X.path("AllRelevantQueries.txt"));
				for (String query : allQueries) {
					Query q = qFactory.create(query);
					out.println(query + " " + q.toString());
				}
				out.close();

				HashSet<String> alwaysProvenQueries = new HashSet<String>();
				for (String y: allQueries) {
					if (!botY.contains(y)) {
						alwaysProvenQueries.add(y);                    
					}
				}

				queriesToXML(alwaysProvenQueries, "W");

				out = Utils.openOut(X.path("AlwaysProvenQueries.txt"));
				for (String query : alwaysProvenQueries) {
					Query q = qFactory.create(query);
					out.println(query + " " + q.toString());
				}
				out.close();
			}
		};
	}
	
	public abstract Set<String> getErrQueries();

	public abstract Set<String> getAllQueries();

	public abstract void queriesToXML(Set<String> queries, String fileNameAppend);
	
	public abstract GenericQueryFactory getQueryFactory();
}

