package chord.analyses.ursa.classifier.datarace;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import chord.instr.Instrumentor;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.expr.FieldAccess;

public class CheckExcludeInstrumentor extends Instrumentor {
	private Set<Integer> escapeEs = null;
	
	public CheckExcludeInstrumentor(Map<String, String> argsMap) {
		super(argsMap);
	}

	@Override
	public void edit(FieldAccess e) throws CannotCompileException {
		if(escapeEs == null){
			escapeEs = new HashSet<Integer>();
			ProgramRel escE = (ProgramRel)ClassicProject.g().getTrgt("escE");
			escE.load();
			for(int ae[] : escE.getAryNIntTuples())
				escapeEs.add(ae[0]);
		}
		
		CtField field;
		CtClass type;
		try {
			field = e.getField();
			type = field.getType();
		} catch (NotFoundException ex) {
			throw new CannotCompileException(ex);
		}
		boolean isPrim = type.isPrimitive();

		if(e.isReader() || isPrim){
			int eId = set(Emap, e);
			if(!this.escapeEs.contains(eId))
				return;
		}
		super.edit(e);
	}

}
