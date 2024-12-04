package chord.analyses.compomustalias;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import chord.analyses.field.DomF;
import chord.project.ClassicProject;
import joeq.Class.jq_Field;

public class GlobalAccessPath extends AccessPath {
	private static DomF domF;
	private static HashMap<String, int[]> mark;
    public final jq_Field global; // static field; non-null

    public GlobalAccessPath(jq_Field g, List<jq_Field> fields) {
        super(fields);
        assert (g != null);
        this.global = g;
    }

    public GlobalAccessPath(jq_Field g) {
        super(Collections.EMPTY_LIST);
        this.global = g;
    }

    public static void init(HashMap<String, int[]> mark) {	
    	domF = (DomF) ClassicProject.g().getTrgt("F");
    	GlobalAccessPath.mark = mark;
    }
    
    @Override
    public int hashCode() {
        return 31 * global.hashCode() + super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof GlobalAccessPath) {
            GlobalAccessPath that = (GlobalAccessPath) obj;
            return global == that.global && fields.equals(that.fields);
        }
        return false;
    }

    @Override
    public String toString() {
        return global.getName() + super.toString();
    }
    
    public boolean isAppAccessed() {
    	int idx = domF.indexOf(global);
		if (!fBelongsToLib(idx)) return true;
    	for (jq_Field f : fields) {
    		idx = domF.indexOf(f);
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
