package canalyses;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import chord.project.Chord;
import chord.project.Config;
import chord.project.analyses.ProgramRel;

@Chord(name = "ret", sign = "ZI0,ZF0,ZV0,ZV1:ZI0_ZF0_ZV0xZV1")
public class RelRet extends ProgramRel {
	private DomZI domZI;
	private DomZF domZF;
	private DomZV domZV;

	@Override
	public void fill() {
		try {
			domZI = (DomZI) doms[0];
			domZF = (DomZF) doms[1];
			domZV = (DomZV) doms[2];
			File f = new File(Config.workDirName + "/Ret.tuples");
			BufferedReader br = new BufferedReader(new FileReader(f));

			while (br.ready()) {
				String line = br.readLine();
				if (line.startsWith("#"))
					continue;

				String[] tokens = line.split(" ");

				int v1 = Integer.parseInt(tokens[0]);
				int v2 = Integer.parseInt(tokens[1]);
				int v3 = Integer.parseInt(tokens[2]);
				int v4 = Integer.parseInt(tokens[3]);

				int v1Idx = domZI.get(v1);
				int v2Idx = domZF.get(v2);
				int v3Idx = domZV.get(v3);
				int v4Idx = domZV.get(v4);

				add(v1Idx, v2Idx, v3Idx, v4Idx);
			}

			br.close();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
