package canalyses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "ie",
		sign = "ZC0,ZI0,ZC1,ZF0:ZC0xZC1_ZI0_ZF0"
)
public class RelIE extends ProgramRel {
	private DomZC domZC;
	private DomZI domZI;
	private DomZF domZF;
	
	@Override
	public void fill(){
		try{
			domZC = (DomZC) doms[0];
			domZI = (DomZI) doms[1];
			domZF = (DomZF) doms[3];
			
			File f = new File(Config.workDirName+"/Ie.tuples");
			BufferedReader br = new BufferedReader(new FileReader(f));
			
			while(br.ready()){
				String line = br.readLine();
				if(line.startsWith("#"))
					continue;
				String[] tokens = line.split(" ");
				
				int v1 = Integer.parseInt(tokens[0]);
				int v2 = Integer.parseInt(tokens[1]);
				int v3 = Integer.parseInt(tokens[2]);
				int v4 = Integer.parseInt(tokens[3]);
				
				int v1Idx = domZC.get(v1);
				int v2Idx = domZI.get(v2);
				int v3Idx = domZC.get(v3);
				int v4Idx = domZF.get(v4);
				
				add(v1Idx,v2Idx,v3Idx,v4Idx);
			}
			
			br.close();
			
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
}
