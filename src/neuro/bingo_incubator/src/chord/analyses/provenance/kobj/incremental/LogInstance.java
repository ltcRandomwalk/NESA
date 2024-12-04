package chord.analyses.provenance.kobj.incremental;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import chord.project.analyses.provenance.FormatedConstraint;

public class LogInstance {
	public static void logClauses(List<FormatedConstraint> gcs, String filePath, int top) {
		try {
			PrintWriter pw = new PrintWriter(new File(filePath));
			int nc = gcs.size();
			int nv = 0;
			for(FormatedConstraint e : gcs){
				for(int x : e.constraint){
					if (x < 0) x = -x;	
					if(nv < x) nv = x;
				}				
			}
			
			pw.print("p wcnf");
			pw.print(" " + nv);
			pw.print(" " + gcs.size());
			pw.println(" " + top);

			for(FormatedConstraint con : gcs){
				if(con.weight > 0)
					pw.print(con.weight);
				else
					pw.print(top);
				for(int l : con.constraint){
					pw.print(" "+l);
				}
				pw.println(" 0");
			}
			
			
			pw.close();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.err.println("Cannot open the log file: " + filePath);
			e.printStackTrace();
		}
	}

}
