package chord.analyses.mln.kobj;

import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import joeq.Class.jq_Type;

@Chord(name = "manualExcludedT",
        sign = "T0:T0"
)
public class ManualExcludedRel extends ProgramRel {

    @Override
    public void fill(){
        this.add(jq_Type.parseType("java.lang.StringBuilder"));
        this.add(jq_Type.parseType("java.lang.String"));
        this.add(jq_Type.parseType("java.lang.StringBuffer"));
    }
}
