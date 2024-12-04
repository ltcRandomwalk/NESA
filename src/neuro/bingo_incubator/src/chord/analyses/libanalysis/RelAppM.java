package chord.analyses.libanalysis;

import chord.analyses.method.DomM;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;
import chord.project.ClassicProject;

@Chord(name = "appM", consumes = { "libM" }, sign = "M0")
public class RelAppM extends ProgramRel {
    @Override
    public void fill() {
        DomM domM = (DomM) doms[0];
        ProgramRel relM = (ProgramRel) ClassicProject.g().getTrgt("libM");
        relM.load();
        for (int mIdx = 0; mIdx < domM.size(); mIdx++) {
            if (!relM.contains(mIdx)) {
                add(mIdx);
            }
        }
        relM.close();
    }
}

