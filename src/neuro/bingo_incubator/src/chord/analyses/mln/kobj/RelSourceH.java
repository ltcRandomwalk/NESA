package chord.analyses.mln.kobj;

import chord.analyses.alloc.DomH;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operator.New;


@Chord(
    name = "sourceH",
    sign = "H0:H0",
    consumes = {"checkExcludedH"}
)
public class RelSourceH extends ProgramRel {
	@Override
	public void fill() {
		ProgramRel relCheckExcludedH = (ProgramRel) ClassicProject.g().getTrgt("checkExcludedH");
		relCheckExcludedH.load();

		DomH domH = (DomH) ClassicProject.g().getTrgt("H");
		ClassicProject.g().runTask(domH);
		int numH = domH.getLastA() + 1;
		for (int hIdx = 1; hIdx < numH; hIdx++) {
			Quad q = (Quad) domH.get(hIdx);
			if (q.getOperator() instanceof New && New.getType(q).getType().getName().contains("Source")
				&& !relCheckExcludedH.contains(hIdx)) {
				add(q);
			}
		}
		relCheckExcludedH.close();
	}
}
