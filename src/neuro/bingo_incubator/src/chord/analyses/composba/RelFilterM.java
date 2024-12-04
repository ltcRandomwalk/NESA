package chord.analyses.composba;


import joeq.Class.jq_Method;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

/**
 * Relation containing each method m such that method m is
 * either equals or hashCode or toString.
 */
@Chord(
    name = "filterM",
    sign = "M0:M0"
)
public class RelFilterM extends ProgramRel {
	private boolean filteringOn;
	
	public void init() { 
		filteringOn = Boolean.getBoolean("chord.composba.filterM");
	}
	
    public void fill() {
        DomM domM = (DomM) doms[0];
        int numM = domM.size();
        for (int mIdx = 0; mIdx < numM; mIdx++) {
            jq_Method m = domM.get(mIdx);
            if (m == null) continue;
            if(m.isAbstract()) continue;
            if(filteringOn && isFilterM(m))
            	add(mIdx);
        }
    }
    
    private boolean isFilterM(jq_Method m){
    	String nameAndDesc = m.getName() + ":" + m.getDesc();
    	if (nameAndDesc.equals("equals:(Ljava/lang/Object;)Z") || 
    		nameAndDesc.equals("hashCode:()I") ||
    		nameAndDesc.equals("toString:()Ljava/lang/String;")){
    		return true;
    	}
    	return false;
    }
}
