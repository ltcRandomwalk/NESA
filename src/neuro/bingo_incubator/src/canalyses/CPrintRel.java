package canalyses;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import chord.bddbddb.Dom;
import chord.bddbddb.Rel.AryNIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.project.analyses.provenance.Tuple;

/*
 * chord.printrel.dir      directory where all the .txt files containing the rels will be dumped.
 *
 */

@Chord(
	    name = "cprintrel-java"
	)
public class CPrintRel extends JavaAnalysis {

	public void run() {
		String printDir = null;
		ProgramRel rel;
		printDir = System.getProperty("chord.printrel.dir", Config.outDirName);
		System.out.println("Printing relations in: " + printDir);
		
		String relName = "pt0";
		rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
		rel.load();
        try {
            File file = new File(printDir, relName + ".txt");
            PrintWriter out = new PrintWriter(new FileWriter(file));
        	for(int[] indices : rel.getAryNIntTuples()){
				Tuple t = new Tuple(rel, indices);
				out.println(t);
			}
			out.flush();
            out.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
		rel.close();
	}
}
