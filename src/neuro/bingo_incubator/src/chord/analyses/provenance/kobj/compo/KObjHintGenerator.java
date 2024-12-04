package chord.analyses.provenance.kobj.compo;

import java.util.List;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.Ctxt;
import chord.analyses.var.DomV;
import chord.project.ClassicProject;
import chord.project.analyses.provenance.ConstraintItem;
import chord.project.analyses.provenance.FormatedConstraint;
import chord.project.analyses.provenance.Tuple;

/**
 * The hint is generated as one line string in the format of:
 * <kind> <method> <context>
 * @author xin
 *
 */
public class KObjHintGenerator implements HintGenerator {
	private DomV domV;
	
	public KObjHintGenerator(){
		domV = (DomV) ClassicProject.g().getTrgt("V");
		ClassicProject.g().runTask(domV);
	}
	
	
	@Override
	public String hintForQuery(Tuple t) {
		jq_Method m = null;
		String relName = t.getRelName();
		if(relName.equals("unsafeDowncast")){
			Register v = (Register)t.getValue(0);
			m = domV.getMethod(v); 
		}
		else if(relName.equals("polySite")){
			Quad i = (Quad)t.getValue(0);
			m = i.getMethod();
		}
		else
			throw new RuntimeException("Unsupported query type: "+relName+".");
		return this.genHint("Query",m, null);
	}

	@Override
	public String hintForDlogGRule(ConstraintItem ci) {
		String tag = "Rule";
		Tuple ht = ci.headTuple;
		String relName = ht.getRelName();
		if(relName.equals("HM")){
			jq_Method m = (jq_Method)ht.getValue(1);
			return this.genHint(tag, m, null);
		}
		else if(relName.startsWith("CM") || relName.startsWith("reachableCM") || relName.equals("rootCM") || relName.startsWith("CMCM")){
			jq_Method m = (jq_Method)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, m, ctxt);
		}
		else if(relName.startsWith("Allow")){
			Quad q = (Quad)ht.getValue(0);
			jq_Method m = q.getMethod();
			return this.genHint(tag, m, null);
		}
		else if(relName.startsWith("CHC") || relName.startsWith("COC")){
			Quad q = (Quad)ht.getValue(1);
			jq_Method m = q.getMethod();
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, m, ctxt);
		}
		else if(relName.startsWith("CCMatch")){
			return this.genHint(tag, null, (Ctxt)ht.getValue(0));
		}
		else if(relName.startsWith("IHM") || relName.startsWith("ctxtInsIHM") || relName.startsWith("kobjSenIHM")
				|| relName.startsWith("kobjSenICM") || relName.startsWith("ctxtInsSpecIM") || relName.startsWith("kobjSenSpecIM")
				|| relName.startsWith("ctxtInsStatIM") || relName.startsWith("ctxtCpyStatIM")){
			Quad q = (Quad)ht.getValue(0);
			return this.genHint(tag, q.getMethod(), null);
		}
		else if(relName.startsWith("reachableCI") || relName.startsWith("DI")){
			Quad q = (Quad)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, q.getMethod(), ctxt);
		}
		else if(relName.startsWith("CI")){
			Quad q = (Quad)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, q.getMethod(), ctxt);
		}
		else if(relName.startsWith("CCM")){
			jq_Method m = (jq_Method)ht.getValue(2);
			Ctxt ctxt = (Ctxt)ht.getValue(1);
//			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, m, ctxt);
		}
		else if(relName.startsWith("CV") || relName.startsWith("DV")){
			Register v = (Register)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, domV.getMethod(v), ctxt);
		}
		else if(relName.startsWith("RobjVa")){
			Register v = (Register)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, domV.getMethod(v), ctxt);
		}
		else if(relName.startsWith("Rget")){
			Register v = (Register)ht.getValue(1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, domV.getMethod(v), ctxt);
		}
		else if(relName.startsWith("Rput")){
			Register v = (Register)ht.getValue(ht.getIndices().length-1);
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, domV.getMethod(v), ctxt);
		}
		else if(relName.startsWith("CFC")){
			Ctxt ctxt = (Ctxt)ht.getValue(0);
			return this.genHint(tag, null, ctxt);
		}
		else if(relName.equals("FC")){
			return this.genHint(tag, null, null);
		}
		else if(relName.equals("reachableT")){
			return this.genHint(tag, null, null);
		}
		else if(relName.equals("reachableCast")){
			Register r = (Register)ht.getValue(1);
			return this.genHint(tag, domV.getMethod(r), null);
		}
		else if(relName.startsWith("ptsV") || relName.startsWith("unsafeDowncast")){
			Register r = (Register)ht.getValue(0);
			return this.genHint(tag, domV.getMethod(r), null);
		}
		else if(relName.equals("insvIM") || relName.equals("virtI") || relName.equals("polySite")){
			Quad q = (Quad)ht.getValue(0);
			return this.genHint(tag, q.getMethod(), null);
		}
		
		throw new RuntimeException("Rule not recognized: "+ci);
	}
	
	@Override
	public String hintForInput(FormatedConstraint con, List<Tuple> tuplePool) {
		int ti = con.constraint[0];
		Tuple t = tuplePool.get(ti);
		Quad h = (Quad)t.getValue(0);
		return this.genHint("Param", h.getMethod(), null);
	}

	@Override
	public String hintForModelCons(Tuple t, int w) {
		throw new RuntimeException("TODO");
	}

	@Override
	public String hintForAbsCost(Tuple t) {
		Quad h = (Quad)t.getValue(0);
		return this.genHint("AbsCost", h.getMethod(), null);
	}

	private String genHint(String label, jq_Method m, Ctxt ctxt){
		StringBuffer ret = new StringBuffer();
		ret.append(label);
		if(m != null)
			ret.append(" ["+m.toString()+"]");
		else
			ret.append(" NA");
		if(ctxt != null)
			ret.append(" ["+ctxt.toString()+"]");
		else
			ret.append(" NA");
		return ret.toString();
	}

}
