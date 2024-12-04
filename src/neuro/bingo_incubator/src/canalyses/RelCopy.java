package canalyses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "copy",
		sign = "ZV0,ZV1:ZV0xZV1"
)
public class RelCopy extends ProgramRel {
	private DomZV domZV;
	
	@Override
	public void fill(){
		try{
			domZV = (DomZV) doms[0];
			File f = new File(Config.workDirName+"/Copy.tuples");
			BufferedReader br = new BufferedReader(new FileReader(f));
			
			while(br.ready()){
				String line = br.readLine();
				if(line.startsWith("#"))
					continue;

				String[] tokens = line.split(" ");
				
				int v1 = Integer.parseInt(tokens[0]);
				int v2 = Integer.parseInt(tokens[1]);
				
				int v1Idx = domZV.get(v1);
				int v2Idx = domZV.get(v2);
				add(v1Idx,v2Idx);
			}
			
			br.close();
			
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
}
