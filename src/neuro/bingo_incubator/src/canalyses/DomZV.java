package canalyses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramDom;

@Chord(name = "ZV")
public class DomZV extends ProgramDom<Integer>{
	@Override
	public void init(){
		try{
			File f = new File(Config.workDirName+"/ZV");
			BufferedReader br = new BufferedReader(new FileReader(f));
			int maxI = -1;
			maxI = Integer.parseInt(br.readLine());
			br.close();
			for(int i=0;i<=maxI;i++){
				add(i);
			}
		}catch(Exception e){
			throw new RuntimeException("Cannot read ZV for DomZV");
		}
	}
	
	@Override
	public void fill(){
		
	}
}
