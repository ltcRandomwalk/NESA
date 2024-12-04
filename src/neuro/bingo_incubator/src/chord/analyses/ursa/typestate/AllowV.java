package chord.analyses.ursa.typestate;

import chord.analyses.var.DomV;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import joeq.Compiler.Quad.RegisterFactory.Register;

@Chord(name="typestate-fullparam-java",consumes = {"V"}, produces={"allow","deny"}, namesOfSigns={"allow","deny"} ,signs={"V0:V0", "V0:V0"} )
public class AllowV extends JavaAnalysis {

	@Override
	public void run() {
		DomV domV = (DomV)ClassicProject.g().getTrgt("V");
		ProgramRel allow = (ProgramRel)ClassicProject.g().getTrgt("allow");
		allow.zero();
		for(Register v : domV)
			allow.add(v);
		allow.save();
		
		ProgramRel deny = (ProgramRel)ClassicProject.g().getTrgt("deny");
		deny.zero();
		deny.save();
	}
	
}
