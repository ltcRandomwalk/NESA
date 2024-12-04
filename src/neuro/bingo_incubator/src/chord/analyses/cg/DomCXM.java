package chord.analyses.cg;

import joeq.Class.jq_Method;

import chord.util.tuple.object.Pair;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramDom;
import chord.project.analyses.ProgramRel;
import chord.analyses.alias.Ctxt;

/**
 * Domain of <context,method> pairs.
 */
@Chord(name = "CXM", consumes = { "CM" })
public class DomCXM extends ProgramDom<Pair<Ctxt,jq_Method>> {
    protected ProgramRel relCM;

    @Override
    public void fill() {
    	relCM = (ProgramRel) ClassicProject.g().getTrgt("CM");
		relCM.load();
		Iterable<Pair<Ctxt, jq_Method>> tuples = relCM.getAry2ValTuples();
		for (Pair<Ctxt, jq_Method> t : tuples){
			add(t);
		}
		relCM.close();
    }

}
