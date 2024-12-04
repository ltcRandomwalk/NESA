package chord.analyses.libanalysis.pointsto;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alloc.DomH;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.analyses.ProgramRel;
import chord.util.Utils;

@Chord(
		name = "allH",
		sign = "H0"
		)
public class RelAllH extends ProgramRel {
	private DomH domH;
	String notTrackedPkgs;
	String[] notTrackedPkgsArr;

	@Override
	public void fill() {
		domH = (DomH) doms[0];
		notTrackedPkgs = Config.checkExcludeStr;
		
		if(notTrackedPkgs == null){
			for(int hIdx = 1; hIdx < domH.size(); hIdx++)
				add(hIdx);
		}else if(notTrackedPkgs.equalsIgnoreCase("")){
			for(int hIdx = 1; hIdx < domH.size(); hIdx++)
				add(hIdx);
		}else{
			notTrackedPkgsArr = Utils.toArray(notTrackedPkgs);
			//add(0);
			for(int hIdx = 1; hIdx < domH.size(); hIdx++){
				Quad q = (Quad) domH.get(hIdx);
				jq_Method m = q.getMethod();
				if(isTracked(m) && isNotString(q)){
					add(hIdx);
				}
			}
		}
		
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
	
	private boolean isNotString(Quad q){
		String qType = DomH.getType(q);
		boolean isNotString = true;

		if (qType.startsWith("java.lang.String") || qType.startsWith("java.lang.StringBuilder") || qType.startsWith("java.lang.StringBuffer"))
			isNotString = false;

		return (isNotString);
	}
}
