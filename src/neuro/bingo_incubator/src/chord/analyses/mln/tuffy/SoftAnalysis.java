package chord.analyses.mln.tuffy;

import java.io.PrintWriter;

import chord.analyses.alloc.DomH;
import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.bddbddb.Dom;
import chord.bddbddb.Rel.IntAryNIterable;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.OutDirUtils;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

@Chord(name="soft-java", 
consumes = { "VT", "HT", "cha", "sub", "MmethArg", "MmethRet", "IinvkArg0", "IinvkArg", "IinvkRet",
		"MI", "statIM", "specIM", "virtIM", "MobjValAsgnInst", "MobjVarAsgnInst", "MgetInstFldInst", 
		"MputInstFldInst", "MgetStatFldInst", "MputStatFldInst", "classT", "staticTM", "staticTF", "clinitTM" }
		)
public class SoftAnalysis extends JavaAnalysis {
	@Override
	public void run() {
		
//		ClassicProject.g().runTask("cipa-0cfa-noreflect-dlog");
		
		PrintWriter  domContents = OutDirUtils.newPrintWriter("domContents");
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		printDoms(domV, domContents);
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		printDoms(domH, domContents);
		DomF domF = (DomF) ClassicProject.g().getTrgt("F");
		printDoms(domF, domContents);
		domContents.close();
		
		PrintWriter  mlnEvidence = OutDirUtils.newPrintWriter("0cfa.db");
		ProgramRel relVT = (ProgramRel) ClassicProject.g().getTrgt("VT");
		relVT.load();
		print(relVT, mlnEvidence);
		
		ProgramRel relHT = (ProgramRel) ClassicProject.g().getTrgt("HT");
		relHT.load();
		print(relHT, mlnEvidence);
		
		ProgramRel relcha = (ProgramRel) ClassicProject.g().getTrgt("cha");
		relcha.load();
		print(relcha, mlnEvidence);
		
		ProgramRel relsub = (ProgramRel) ClassicProject.g().getTrgt("sub");
		relsub.load();
		print(relsub, mlnEvidence);
		
		ProgramRel relMmethArg = (ProgramRel) ClassicProject.g().getTrgt("MmethArg");
		relMmethArg.load();
		print(relMmethArg, mlnEvidence);
		
		ProgramRel relMmethRet = (ProgramRel) ClassicProject.g().getTrgt("MmethRet");
		relMmethRet.load();
		print(relMmethRet, mlnEvidence);
		
		ProgramRel relIinvkArg0 = (ProgramRel) ClassicProject.g().getTrgt("IinvkArg0");
		relIinvkArg0.load();
		print(relIinvkArg0, mlnEvidence);
		
		ProgramRel relIinvkArg = (ProgramRel) ClassicProject.g().getTrgt("IinvkArg");
		relIinvkArg.load();
		print(relIinvkArg, mlnEvidence);
		
		ProgramRel relIinvkRet = (ProgramRel) ClassicProject.g().getTrgt("IinvkRet");
		relIinvkRet.load();
		print(relIinvkRet, mlnEvidence);
		
		ProgramRel relMI = (ProgramRel) ClassicProject.g().getTrgt("MI");
		relMI.load();
		print(relMI, mlnEvidence);
		
		ProgramRel relstatIM = (ProgramRel) ClassicProject.g().getTrgt("statIM");
		relstatIM.load();
		print(relstatIM, mlnEvidence);
		
		ProgramRel relspecIM = (ProgramRel) ClassicProject.g().getTrgt("specIM");
		relspecIM.load();
		print(relspecIM, mlnEvidence);
		
		ProgramRel relvirtIM = (ProgramRel) ClassicProject.g().getTrgt("virtIM");
		relvirtIM.load();
		print(relvirtIM, mlnEvidence);
		
		ProgramRel relMobjValAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("MobjValAsgnInst");
		relMobjValAsgnInst.load();
		print(relMobjValAsgnInst, mlnEvidence);
		
		ProgramRel relMobjVarAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("MobjVarAsgnInst");
		relMobjVarAsgnInst.load();
		print(relMobjVarAsgnInst, mlnEvidence);
		
		ProgramRel relMgetInstFldInst = (ProgramRel) ClassicProject.g().getTrgt("MgetInstFldInst");
		relMgetInstFldInst.load();
		print(relMgetInstFldInst, mlnEvidence);
		
		ProgramRel relMputInstFldInst = (ProgramRel) ClassicProject.g().getTrgt("MputInstFldInst");
		relMputInstFldInst.load();
		print(relMputInstFldInst, mlnEvidence);
		
		ProgramRel relMgetStatFldInst = (ProgramRel) ClassicProject.g().getTrgt("MgetStatFldInst");
		relMgetStatFldInst.load();
		print(relMgetStatFldInst, mlnEvidence);
		
		ProgramRel relMputStatFldInst = (ProgramRel) ClassicProject.g().getTrgt("MputStatFldInst");
		relMputStatFldInst.load();
		print(relMputStatFldInst, mlnEvidence);
		
		ProgramRel relclassT = (ProgramRel) ClassicProject.g().getTrgt("classT");
		relclassT.load();
		print(relclassT, mlnEvidence);
		
		ProgramRel relstaticTM = (ProgramRel) ClassicProject.g().getTrgt("staticTM");
		relstaticTM.load();
		print(relstaticTM, mlnEvidence);
		
		ProgramRel relstaticTF = (ProgramRel) ClassicProject.g().getTrgt("staticTF");
		relstaticTF.load();
		print(relstaticTF, mlnEvidence);
		
		ProgramRel relclinitTM = (ProgramRel) ClassicProject.g().getTrgt("clinitTM");
		relclinitTM.load();
		print(relclinitTM, mlnEvidence);
		
		mlnEvidence.close();

		System.setProperty("chord.ctxt.kind", "cs");
		System.setProperty("chord.kcfa.k", "2");
		ClassicProject.g().runTask("cipa-0cfa-noreflect-dlog");
	//	ClassicProject.g().runTask("ctxts-java");
	//	ClassicProject.g().runTask("argCopy-dlog");
	//	ClassicProject.g().runTask("cspa-kcfa-noreflect-dlog");
	//	ClassicProject.g().runTask("cspa-kcfa-VH-dlog");
		
		PrintWriter  mlnTrainData = OutDirUtils.newPrintWriter("0cfa_train.db");
	//	ProgramRel relVH_CS = (ProgramRel) ClassicProject.g().getTrgt("VH_CS");
		ProgramRel relVH_CS = (ProgramRel) ClassicProject.g().getTrgt("VH");
		relVH_CS.load();
		print(relVH_CS, mlnTrainData);
		
/*		ProgramRel relIHM = (ProgramRel) ClassicProject.g().getTrgt("IHM");
		relIHM.load();
		print(relIHM, mlnTrainData);
		
		ProgramRel relVV = (ProgramRel) ClassicProject.g().getTrgt("VV");
		relVV.load();
		print(relVV, mlnTrainData);
		
		ProgramRel relspecIMV = (ProgramRel) ClassicProject.g().getTrgt("specIMV");
		relspecIMV.load();
		print(relspecIMV, mlnTrainData);
		
		ProgramRel relobjValAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("objValAsgnInst");
		relobjValAsgnInst.load();
		print(relobjValAsgnInst, mlnTrainData);
		
		ProgramRel relobjVarAsgnInst = (ProgramRel) ClassicProject.g().getTrgt("objVarAsgnInst");
		relobjVarAsgnInst.load();
		print(relobjVarAsgnInst, mlnTrainData);
		
		ProgramRel relgetInstFldInst = (ProgramRel) ClassicProject.g().getTrgt("getInstFldInst");
		relgetInstFldInst.load();
		print(relgetInstFldInst, mlnTrainData);
		
		ProgramRel relputInstFldInst = (ProgramRel) ClassicProject.g().getTrgt("putInstFldInst");
		relputInstFldInst.load();
		print(relputInstFldInst, mlnTrainData);
		
		ProgramRel relgetStatFldInst = (ProgramRel) ClassicProject.g().getTrgt("getStatFldInst");
		relgetStatFldInst.load();
		print(relgetStatFldInst, mlnTrainData);
		
		ProgramRel relputStatFldInst = (ProgramRel) ClassicProject.g().getTrgt("putStatFldInst");
		relputStatFldInst.load();
		print(relputStatFldInst, mlnTrainData);
		
		ProgramRel relreachableT = (ProgramRel) ClassicProject.g().getTrgt("reachableT");
		relreachableT.load();
		print(relreachableT, mlnTrainData);
		
		ProgramRel relVHfilter = (ProgramRel) ClassicProject.g().getTrgt("VHfilter");
		relVHfilter.load();
		print(relVHfilter, mlnTrainData);
		
		ProgramRel relFH = (ProgramRel) ClassicProject.g().getTrgt("FH");
		relFH.load();
		print(relFH, mlnTrainData);
		
		ProgramRel relHFH = (ProgramRel) ClassicProject.g().getTrgt("HFH");
		relHFH.load();
		print(relHFH, mlnTrainData);
		
		ProgramRel relrootM = (ProgramRel) ClassicProject.g().getTrgt("rootM");
		relrootM.load();
		print(relrootM, mlnTrainData);
		
		ProgramRel relreachableI = (ProgramRel) ClassicProject.g().getTrgt("reachableI");
		relreachableI.load();
		print(relreachableI, mlnTrainData);
		
		ProgramRel relreachableM = (ProgramRel) ClassicProject.g().getTrgt("reachableM");
		relreachableM.load();
		print(relreachableM, mlnTrainData);
		
		ProgramRel relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relIM.load();
		print(relIM, mlnTrainData);
		
		ProgramRel relMM = (ProgramRel) ClassicProject.g().getTrgt("MM");
		relMM.load();
		print(relMM, mlnTrainData);
*/		
		
		mlnTrainData.close();
		
	}

	public void print(ProgramRel rel, PrintWriter pw) {
		IntAryNIterable tuples = rel.getAryNIntTuples();
		int n = rel.getDoms().length;
		for (int[] tuple : tuples) {
			String s = rel.getName()+"(";
			for (int i = 0; i < n; i++) {
				s += tuple[i];
				if (i < n - 1)
					s += ", ";
			}
			s += ")";
			pw.println(s);
		}
	}
	
	public void printDoms(Dom dom, PrintWriter pw){
		for (int i = 0; i < dom.size(); i++)
            pw.println(dom.getName() + "(" + i + "::" + dom.toUniqueString(i) + ")");
	}
}
