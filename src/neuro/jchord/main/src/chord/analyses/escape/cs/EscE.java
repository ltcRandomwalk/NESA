package chord.analyses.escape.cs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.heapacc.DomE;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

/**
 *
 * @author Ravi Mangal
 */
@Chord(
    name = "escE-java",
    consumes = {"E", "V", "checkExcludedE"},
    produces = {"escE"}
)
public class EscE extends JavaAnalysis {
	
	public void run() {
		DomE domE = (DomE) ClassicProject.g().getTrgt("E");
        DomV domV = (DomV) ClassicProject.g().getTrgt("V");
        ProgramRel relEscE = (ProgramRel) ClassicProject.g().getTrgt("escE");
        relEscE.zero();  
        Map<String, Quad> strToQuadMap = new HashMap<String, Quad>();
        for(int idx = 0; idx < domE.size(); idx++){
			Quad q = (Quad) domE.get(idx);
			strToQuadMap.put(q.toByteLocStr(), q);
		}
        
        HashSet<Quad> skipThrEscE = new HashSet<Quad>();
        
        if(Boolean.getBoolean("chord.mln.useThrEsc")) {
        	//built set skipThrEscE
        	String thrEscFile = System.getProperty("chord.mln.threscFile");
        	if (thrEscFile != null) {
        		try {
        			Scanner sc = new Scanner(new File(thrEscFile));
        			while(sc.hasNextLine()){
        				String nextLine = sc.nextLine();
        				Quad q = strToQuadMap.get(nextLine);
        				skipThrEscE.add(q);
        				//        			skipThrEscE.add((Quad) domE.get(Integer.parseInt(nextLine)));
        			}
        			sc.close();
        		} catch (FileNotFoundException e) {
        			throw new RuntimeException(e);
        		}
        	}

//        	ProgramRel relCheckExcludedE = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedE");
//        	relCheckExcludedE.load();
//        	Iterable<Quad> tuples = relCheckExcludedE.getAry1ValTuples();
//        	for (Quad t : tuples) {
//        		skipThrEscE.add(t);
//        	}
//        	relCheckExcludedE.close();
        }

		ProgramRel relCheckExcludedE = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedE");
		relCheckExcludedE.load();
		Iterable<Quad> tuples = relCheckExcludedE.getAry1ValTuples();
		for (Quad t : tuples) {
			skipThrEscE.add(t);
		}
		relCheckExcludedE.close();
        
        int numE = domE.size();
        for (int eIdx = 0; eIdx < numE; eIdx++) {
            Quad q = (Quad) domE.get(eIdx);
        	if (skipThrEscE.contains(q)) continue;
            relEscE.add(eIdx);
        }
        relEscE.save();
	}   
}
