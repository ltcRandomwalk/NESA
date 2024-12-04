package chord.analyses.composba;

import joeq.Class.jq_Method;
import chord.util.Utils;

public class WrappedSE<PE extends IEdge, SE extends IEdge> implements IWrappedSE<PE, SE> {
	private final jq_Method m;
    private final SE se;
    private IWrappedPE<PE, SE> wpe;
    private int len;

    public WrappedSE(SE se, jq_Method m, IWrappedPE<PE, SE> pe, int len) {
        assert (len >= 0);
        this.se = se;
        this.wpe = pe;
        this.len = len;
        this.m = m;
    }

    public void update(IWrappedPE<PE, SE> newWPE, int newLen) {
        assert (newLen >= 0);
        this.wpe = newWPE;
        this.len = newLen;
    }

    public int getLen() { return len; }
    
    public jq_Method getMethod() { return m; }

    @Override
    public SE getSE() { return se; }

    @Override
    public IWrappedPE<PE, SE> getWPE() { return wpe; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((m == null) ? 0 : m.hashCode());
        result = prime * result + ((se == null) ? 0 : se.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WrappedSE)) return false;
        WrappedSE that = (WrappedSE) obj;
        return this.m == that.m && Utils.areEqual(this.se, that.se);
    }
    @Override
    public String toString() {
        return "WrappedSE [Method=" + m + ", SE=" + se + "]";
    }
}
