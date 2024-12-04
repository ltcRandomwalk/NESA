package chord.analyses.cg;

import joeq.Class.jq_Method;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.util.tuple.object.Pair;

/**
 * Relation containing each tuple (cxm, c,m) such that element cxm is composed of ctxt c
 * and method m.
 */
@Chord(
    name = "CXMCM",
    sign = "CXM0,C0,M0:CXM0_C0_M0"
)
public class RelCXMCM extends ProgramRel {
    private DomCXM domCXM;
    private DomC domC;
    private DomM domM;

    @Override
    public void fill() {
    	domCXM = (DomCXM) doms[0];
    	domC = (DomC) doms[1];
    	domM = (DomM) doms[2];
    	
    	int numCM = domCXM.size();

        for (int cmIdx = 0; cmIdx < numCM; cmIdx++) {
            Pair<Ctxt, jq_Method> p = domCXM.get(cmIdx);
			add(cmIdx,domC.getOrAdd(p.val0),domM.getOrAdd(p.val1));
		}
    }		
}