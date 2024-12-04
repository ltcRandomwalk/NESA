package chord.analyses.incrsolver;

import chord.project.analyses.JavaAnalysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import joeq.Class.jq_Class;
import joeq.Compiler.Quad.Quad;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.point.DomP;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.ITask;


@Chord(name = "incr-domcdumper",
consumes = { "P" }
)
public class DomCDumper extends JavaAnalysis {
	List<ITask> tasks;
	private String libraryPrefix = "(java.|javax.|sun.|sunw.|launcher.|com.sun.|com.ibm.|org.apache.harmony.|org.w3c.|org.xml.|org.ietf.|org.omg.|slib.).*";
	private String summaryDir;
	private String appName;
	
	
	@Override
	public void run() {
		tasks = new ArrayList<ITask>();
		tasks.add(ClassicProject.g().getTask("cipa-0cfa-dlog"));
		tasks.add(ClassicProject.g().getTask("ctxts-java"));
		System.setProperty("chord.ctxt.kind", "co");
		summaryDir = System.getProperty("chord.incrsolver.summaryDir");
		appName = System.getProperty("chord.incrsolver.appName");
		runAllTasks();
		printLibContexts();
	}
	
	private final void runAllTasks() {
		for (ITask t : tasks) {
			ClassicProject.g().resetTaskDone(t);
			ClassicProject.g().runTask(t);
		}
	}
	
	private void printLibContexts() {
		String relDirName = summaryDir + "/preload";
		File relDir = new File(relDirName);
		File sumDir = new File(summaryDir);
		if (!sumDir.exists()) {
			try{
		        sumDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("summary dir " + summaryDir + " does not exist - unable to create it.");
		     }        
		}
		if (!relDir.exists()) {
			try{
		        relDir.mkdir();
		     } catch(SecurityException se){
		        System.out.println("preload dir " + relDirName + " does not exist - unable to create it.");
		     }        
		}
		PrintWriter domcPW;
		try {
			domcPW = new PrintWriter(new File(relDirName + "/" + appName + "_C.txt"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		DomP domP = (DomP) ClassicProject.g().getTrgt("P");
		DomC domC = (DomC) ClassicProject.g().getTrgt("C");
		for (int i = 0; i < domC.size(); i++) {
			Ctxt ctxt = (Ctxt) domC.get(i);
			int cnt = ctxt.length();
			Quad[] qArr;
			boolean valid;
			if (cnt > 0) {
				qArr = ctxt.getElems();
				valid = true;
				for (int j = 0; j < cnt; j++) {
					jq_Class cl = qArr[j].getMethod().getDeclaringClass();
					if (!cl.getName().matches(libraryPrefix)) {
						valid = false;
						break;
					}
				}
			} else {
				qArr = null;
				valid = true;
			}
			if (valid) {
				StringBuilder sb = new StringBuilder();
				sb.append("[");
				if (qArr != null) {
					for (int j = 0; j < qArr.length; j++) {
						int idx = domP.indexOf(qArr[j]);
						if (j != 0) sb.append(",");
						sb.append(domP.toUniqueString(idx));
					}
				}
				sb.append("]");
				domcPW.println(sb);
			}
		}
		domcPW.close();
	}
}

