package chord.analyses.mln;

import static chord.util.ExceptionUtil.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import chord.bddbddb.Dom;
import chord.project.analyses.provenance.Tuple;
import chord.util.ProcessExecutor;
import joeq.Compiler.Quad.Quad;

/*
 *
 * -Dchord.mln.solverPath
 * -Dchord.mln.programs
 * -Dchord.mln.conf
 * -Dchord.mln.solverType
 * 
 * -Dchord.mln.edb
 * -Dchord.mln.feedback
 * -Dchord.mln.out
 * -Dchord.mln.refined
 * -Dchord.mln.loadgc
 * -Dchord.mln.storegc
 * -Dchord.mln.loadRev
 * -Dchord.mln.log
 * -Dchord.mln.memStatsPath
 * -Dchord.mln.mcslsLimit
 * -Dchord.mln.groundingTimeout
 * 
 * @author Ravi Mangal
 */
public class MLNRunner {
	String solverPath;
	String solverType;
	String edbPath;
	String outPath;
	String feedbackPath;
	String mlnPrograms;
	String refinedConsFile = null;
	String loadGcPath = null;
	String storeGcPath = null;
	String loadRevConsPath = null;
	String confPath;
	String logPath = null;
	String memStatsPath;
	String mcslsLimit;
	String groundingTimeout;
	Set<String> matchRels;
	
	public MLNRunner(Set<String> matchRels) {
	//	readOptions();
		if (matchRels != null) {
			this.matchRels = matchRels;
		} else {
			this.matchRels = new HashSet<String>();
		}
	}
	
	private void readOptions() {
		solverPath = System.getProperty("chord.mln.solverPath", "./mln.jar");
		solverType = System.getProperty("chord.mln.solverType", "exact");
		edbPath = System.getProperty("chord.mln.edb", "./problem.edb");
		feedbackPath = System.getProperty("chord.mln.feedback", "./feedback.edb");
		outPath = System.getProperty("chord.mln.out", "./out");
		mlnPrograms =
			System
				.getProperty("chord.mln.programs",
					"./kobj-bit-init-dlog.mln,./pro-cspa-kobj-dlog.mln,./pro-downcast-dlog.mln");
		refinedConsFile = System.getProperty("chord.mln.refined");
		loadGcPath = System.getProperty("chord.mln.loadgc");
		storeGcPath = System.getProperty("chord.mln.storegc");
		loadRevConsPath = System.getProperty("chord.mln.loadRev");
		confPath = System.getProperty("chord.mln.conf", "./mln.conf");
		logPath = System.getProperty("chord.mln.log", "./log");
		memStatsPath = System.getProperty("chord.mln.memStatsPath", "./memStats");
		mcslsLimit = System.getProperty("chord.mln.mcslsLimit", "50");
		groundingTimeout = System.getProperty("chord.mln.groundingTimeout", "-1");
	}
	
	public Map<String, Set<Tuple>> runMLN() throws IOException, InterruptedException {
		readOptions();
		File result = new File(logPath);
		List<String> command = new ArrayList<String>();
		command.add("/usr/bin/time");
		command.add("-v");
		command.add("-o");
		command.add(memStatsPath);
		command.add("java");
		command.add("-Xmx64g");
		command.add("-jar");
		command.add(solverPath);
		command.add("-conf");
		command.add(confPath);
		command.add("-i");
		//if (refinedConsFile == null) {
		command.add(mlnPrograms);
		//} else {
		//	command.add(mlnPrograms + "," + refinedConsFile);
		//}
		command.add("-e");
		command.add(edbPath);
		command.add("-r");
		command.add(outPath);
		if (loadGcPath != null) {
			command.add("-loadgc");
			command.add(loadGcPath);
		}
		if (storeGcPath != null) {
			command.add("-storegc");
			command.add(storeGcPath);
		}
		if (loadRevConsPath != null) {
			command.add("-loadrev");
			command.add(loadRevConsPath);
		}
		if(refinedConsFile != null){
			command.add("-loadfeedback");
			command.add(refinedConsFile);
		}

		command.add("-verbose");
		command.add("2");
		command.add("-numGIter");
		command.add("1");
		command.add("-fullyGround");
		command.add("-printVio");
		command.add("-ignoreWarmGCWeight");
		command.add("-solver");
		command.add(solverType);
		command.add("-mcslsTimeout");
		command.add("18000");
		command.add("-mcslsLimit");
		command.add(mcslsLimit);
		command.add("-groundingTimeout");
		command.add(groundingTimeout);

		String[] cmdAry = command.toArray(new String[command.size()]);
		
		try {
			if (ProcessExecutor.executeWithRedirect(cmdAry, result, -1) != 0) {
				fail("Solver did not terminate correctly.");
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
		
		Map<String, Set<Tuple>> derivedTuples = interpretResult(outPath);
		return derivedTuples;
	}
	
	private Map<String, Set<Tuple>> interpretResult(String outPath)
		throws IOException {
		BufferedReader br =
			new BufferedReader(new InputStreamReader(new FileInputStream(
				outPath)));
		Map<String, Set<Tuple>> ret = new HashMap<String, Set<Tuple>>();
		String line;
		//File resInfo = new File(outPath+"lineNum");
		//PrintWriter pw = new PrintWriter(resInfo);
		while ((line = br.readLine()) != null) {
			if (line.trim().equals("")) {
				continue;
			}
			if (line.startsWith("//")) {
				continue;
			}
			if (line.startsWith("!")) {
				br.close();
				throw new RuntimeException("Result produced by the MLN engine should not contain negated tuples");
			} else if (line.contains("RevOR") || line.contains("dom_")) {
				continue;
			} else {
				Tuple current = new Tuple(line);
				if (matchRels.contains(current.getRelName())) {
					Set<Tuple> tuples = ret.get(current.getRelName());
					if (tuples == null) {
						tuples = new HashSet<Tuple>();
						ret.put(current.getRelName(), tuples);
					}
					tuples.add(current);
					/*Dom[] doms = current.getDomains();
					int[] domsIndx = current.getIndices();
					StringBuilder sb = new StringBuilder();
					for(int i=0;i<doms.length;i++){
						Dom d = doms[i];
						int indx = domsIndx[i];
						Object o = d.get(indx);
						if(o instanceof Quad){
							Quad q = (Quad)o;
							sb.append(q.toVerboseStr()+":"+q.toJavaLocStr());
						}else{
							sb.append(o.toString());
						}
						if(i<doms.length-1)
							sb.append(",");
					}
					pw.write(sb.toString()+"\n");
					*/
				}
				
			}
		}
		//pw.close();
		br.close();
		return ret;
	}
}
