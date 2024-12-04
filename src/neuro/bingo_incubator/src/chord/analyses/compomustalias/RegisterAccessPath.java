package chord.analyses.compomustalias;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import chord.analyses.field.DomF;
import chord.analyses.method.DomM;
import chord.project.ClassicProject;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Class.jq_Field;

public class RegisterAccessPath extends AccessPath {
	private static DomF domF;
	private static HashMap<String, int[]> mark;
    public final Register var; // non-null
    public boolean isRet = false;

    public RegisterAccessPath(Register v, List<jq_Field> fields) {
        super(fields);
        assert (v != null);
        this.var = v;
    }

    public RegisterAccessPath(Register v) {
        super(Collections.EMPTY_LIST);
        this.var = v;
    }

    public static void init(HashMap<String, int[]> mark) {	
    	domF = (DomF) ClassicProject.g().getTrgt("F");
    	RegisterAccessPath.mark = mark;
    }
    
    @Override
    public int hashCode() {
        return 31 * var.hashCode() + super.hashCode()+(this.isRet?17:0);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof RegisterAccessPath) {
            RegisterAccessPath that = (RegisterAccessPath) obj;
            return var == that.var && fields.equals(that.fields)&&this.isRet == that.isRet;
        }
        return false;
    }

    @Override
    public String toString() {
        return var + super.toString()+"(isRet = "+isRet+")";
    }
    
    public boolean isAppAccessed() {
    	for (jq_Field f : fields) {
    		int idx = domF.indexOf(f);
    		if (!fBelongsToLib(idx)) return true;
    	}
    	return false;
    }
    
    private boolean fBelongsToLib(int i){
		int type = 0;
		if (mark.containsKey("F")) {
			type |= ((int[])mark.get("F"))[i];
		}
		if (type <= 0) return false;         // Unknown
		else if (type <= 1) return false;    // Don't care
		else if (type <= 3) return true;     // Library
		else if (type <= 5) return false;    // Application
		else if (type <= 7) return false;    // Could be either marked as Boundary - mix of App and Lib OR as App (currently 
		                                     // marking as app.
		return false;
	}
}
