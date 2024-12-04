package chord.analyses.cg;

import joeq.Class.jq_Method;
import chord.analyses.method.DomM;
import chord.analyses.prunerefine.Query;
import chord.project.ClassicProject;
import chord.project.analyses.ProgramRel;

public class CGQuery extends Query{

	DomM domM;
	jq_Method m1, m2;

	public CGQuery(Object[] tuple){
		assert tuple.length == 2;
		assert (tuple[0] instanceof jq_Method) && (tuple[1] instanceof jq_Method);
		m1 = (jq_Method) tuple[0];
		m2 = (jq_Method) tuple[1];
		domM = (DomM) ClassicProject.g().getTrgt("M");
	}
	
	public CGQuery(String[] tuple){
		assert tuple.length == 2;
		domM = (DomM) ClassicProject.g().getTrgt("M");
		m1 = domM.get(Integer.parseInt(tuple[0]));
		m2 = domM.get(Integer.parseInt(tuple[1]));
		
	}

	@Override public int hashCode() { return m1.hashCode() * 37 + m2.hashCode(); }

	@Override public boolean equals(Object _that) {
		CGQuery that = (CGQuery)_that;
		return m1.equals(that.m1) && m2.equals(that.m2);
	}

	@Override
	public void addToRel(ProgramRel rel) { rel.add(m1, m2); }

	@Override public String toString() { return m1.toString()+"|"+m2.toString(); }

	@Override
	public String encode() { 
		return "M"+domM.indexOf(m1)+","+domM.indexOf(m2); 
	}

	@Override 
	public int compareTo(Query _that) {
		if(!(_that instanceof CGQuery))
			return 0;
		else{
			CGQuery that = (CGQuery)_that;
			int a, b;
			a = domM.indexOf(this.m1);
			b = domM.indexOf(that.m1);
			if (a != b) return a < b ? -1 : +1;
			a = domM.indexOf(this.m2);
			b = domM.indexOf(that.m2);
			if (a != b) return a < b ? -1 : +1;
			return 0;
		}
	}
}
