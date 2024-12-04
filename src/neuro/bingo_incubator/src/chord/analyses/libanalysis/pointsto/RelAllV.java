package chord.analyses.libanalysis.pointsto;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;

@Chord(
		name = "allV",
		consumes = { "filV" },
		sign = "V0"
		)
public class RelAllV extends ProgramRel {
	private DomV domV;
	private ProgramRel filV;
	String notTrackedPkgs;
	String[] notTrackedPkgsArr;

	@Override
	public void fill() {
		domV = (DomV) doms[0];
		filV = (ProgramRel) ClassicProject.g().getTrgt("filV");
		filV.load();
		Iterable<Register> vItr = filV.getAry1ValTuples();
		
		notTrackedPkgs = Config.checkExcludeStr;

		if(notTrackedPkgs == null){
			for(Register v : vItr)
				add(v);
		}else if(notTrackedPkgs.equalsIgnoreCase("")){
			for(Register v : vItr)
				add(v);
		}else{
			notTrackedPkgsArr = Utils.toArray(notTrackedPkgs);
			for(Register v : vItr){
				jq_Method m = domV.getMethod(v);
				if(isTracked(m)){
					add(v);

				}
			}
		}
		
		filV.close();
	}

	private boolean isTracked(jq_Method m){
		String cName = m.getDeclaringClass().getName();
		boolean isInc = true;

		for (String c : notTrackedPkgsArr) {
			if (cName.startsWith(c)){
				isInc = false;
				break;
			}
		}

		return (isInc);
	}
}