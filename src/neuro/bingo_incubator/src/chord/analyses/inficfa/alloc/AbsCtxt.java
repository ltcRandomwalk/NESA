package chord.analyses.inficfa.alloc;

import joeq.Compiler.Quad.Quad;
import java.io.Serializable;

import chord.analyses.alias.Ctxt;
import chord.analyses.inficfa.BitAbstractState;

public class AbsCtxt extends Ctxt implements Serializable {
    private BitAbstractState absState;
    private Quad h;
    static final Quad[] emptyElems = new Quad[0];

    public AbsCtxt(BitAbstractState absState, Quad h) {
    	super(emptyElems);
        this.absState = absState;
        this.h = h;
    }

    public BitAbstractState getState(){
    	return absState;
    }
    
    public Quad getQuad(){
    	return h;
    }
    
    public int hashCode() {
        int i = 5381;
        int hCode = (h == null) ? 9999 : h.getID();
        i = ((i << 5) + i) + hCode; // i*33 + q
        i = ((i << 5) + i) + ((this.absState == null) ? 9999 : this.absState.hashCode()); // i*33 + q
        return i;
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof AbsCtxt))
            return false;
        AbsCtxt that = (AbsCtxt) o;
        if(this.h == null || that.h == null){
        	if(this.h != null || that.h != null) return false;
        }else{
        	if (!this.h.equals(that.h))	 return false;
        }
        if(this.absState == null || that.absState == null){
        	if(this.absState != null || that.absState != null) return false;
        }else{
        	if (!this.absState.equals(that.absState)) return false;
        }
        return true;
    }
    
    public String toString() {
        String s = "[";
        s += h == null ? "null" : h.toByteLocStr();
        s += "::";
        s += absState == null ? "null" : absState.toString();
        return s + "]";
    }
}
