package chord.project.analyses.composat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import chord.project.Config;
import chord.project.analyses.provenance.Tuple;
import chord.util.ProcessExecutor;
import chord.util.Timer;

/**
 * The very basic solver that invokes a MaxSAT solver directly
 * @author xin
 *
 */
public class BasicSolver implements Solver {
	private String solverPath;
	
	public BasicSolver(){
		if(SatConfig.solverToUse == SatConfig.SOLVER_MIFUMAX){
			String mifuFileName = System.getProperty("chord.provenance.mifu", "mifumax");
			this.solverPath = System.getenv("CHORD_MAIN") + File.separator + "src" + File.separator +
					"chord" + File.separator + "project" + File.separator + "analyses" + File.separator +
					"provenance" + File.separator + mifuFileName;
		}else{
			throw new RuntimeException("Unknown solver "+SatConfig.solverToUse+"!");
		}
	}	
	
	@Override
	public Set<Integer> solve(Problem p, int depth){
		try {
			File consFile = new File(Config.outDirName + File.separator +"p"+p.getId()+ ".wcnf");
			PrintWriter consWriter = new PrintWriter(consFile);
			Map<Integer,Integer> atomMap = new HashMap<Integer,Integer>();
			List<Integer> atomDic = new ArrayList<Integer>();
			long topWeight = 0;
			for(Constraint cons : p.getConstraints()){
				for(Integer l : cons.getLiterals()){
					int atom = Math.abs(l);
					if(!atomMap.containsKey(atom)){
						atomDic.add(atom);
						atomMap.put(atom, atomDic.size());
					}
				}
				if(cons.getWeight() > 0)
					topWeight += cons.getWeight();
			}
			
			topWeight++;
			
			int nvar = atomDic.size();
			int nclauses = p.getConstraints().size();			
			
			consWriter.println("c");
			consWriter.println("c Constraints for Problem "+p.getId());
			consWriter.println("c");
			consWriter.println("p wcnf "+nvar+" "+nclauses+" "+topWeight);
			
			for(Constraint cons : p.getConstraints()){
				if(cons.isHardConstraint())
					consWriter.print(topWeight);
				else
					consWriter.print((int)cons.getWeight());
				for(Integer l : cons.getLiterals()){
					int mappedAtom = atomMap.get(Math.abs(l));
					consWriter.print(" "+(l>0?mappedAtom:-mappedAtom));
				}
				consWriter.println(" 0");
			}
			
			consWriter.flush();
			consWriter.close();
			
			String cmd[] = new String[2];
			File result = new File(Config.outDirName + File.separator + "result");
			cmd[0] = solverPath;
			cmd[1] = consFile.getAbsolutePath();
			System.out.println("Start the solver.");	

			Timer t = new Timer("MifuMax");
			t.init();
			if (ProcessExecutor.executeWithRedirect(cmd, result, -1) != 0) {
				throw new RuntimeException("The solver did not terminate normally.");
			}
			t.done();
			System.out.println("Solver exclusive time: "+t.getExclusiveTimeStr());
			Set<Integer> varPos = this.interpreteResult(result);
			if(varPos == null)
				return null;
			Set<Integer> ret = new HashSet<Integer>();
			for(Integer r : varPos){
				ret.add(atomDic.get(r-1));
			}
			return ret;
		} catch (Throwable e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private Set<Integer> interpreteResult(File f) {
		try {
			Scanner sc = new Scanner(f);
			Set<Integer> ret = new HashSet<Integer>();
			while (sc.hasNext()) {
				String line = sc.nextLine();
				if (line.startsWith("s ")) {
					if (line.startsWith("s UNSATISFIABLE"))
						return null;
					if (!line.startsWith("s OPTIMUM FOUND"))
						throw new RuntimeException("Expecting a solution but got " + line);
				}
				if (line.startsWith("v ")) {
					Scanner lineSc = new Scanner(line);
					String c = lineSc.next();
					if (!c.trim().equals("v")){
						lineSc.close();
						throw new RuntimeException("Expected char of a solution line: " + c);
					}
					while (lineSc.hasNext()) {
						int i = lineSc.nextInt();
						if (i > 0) {
							ret.add(i);
						}
					}
					lineSc.close();
				}
			}
			sc.close();
//			System.out.println("Tuples to eliminate: "+ret);
			return ret;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
