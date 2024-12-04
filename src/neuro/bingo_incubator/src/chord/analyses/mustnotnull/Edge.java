package chord.analyses.mustnotnull;

import java.util.HashMap;
import java.util.Map;

import joeq.Compiler.Quad.Quad;
import chord.util.ArraySet;
import chord.project.analyses.rhs.IEdge;
import chord.util.Utils;

/**
 * Representation of path edge or summary edge in must-not null analysis.
 * There are 3 kinds of edges with template <srcNode, dstNode>:
 * NULL: <null, null>
 * FULL: <AS, AS'> or <null, AS>
 *
 * @author Ravi Mangal
 */
public class Edge implements IEdge {
    public static final Edge NULL = new Edge();

    final public EdgeKind type;
    final public AbstractState srcNode;
    public AbstractState dstNode;

    // used only for construction of NULL edge
    protected Edge() {
        type = EdgeKind.NULL;
        srcNode = null;
        dstNode = null;
    }

    // used only for construction of FULL edge (not NULL edge)
    public Edge(AbstractState s, AbstractState d) {
        type = EdgeKind.FULL; 
        srcNode = s;
        dstNode = d;
    }

    /**
     * Two path (or summary) edges for the same program point (or method) can be
     * merged if the edges are of the same type, and their source nodes match.
     * The destination nodes are always intersected.
     */
    @Override
    public int canMerge(IEdge e, boolean mustMerge) {
        assert (!mustMerge);  // not implemented yet
        Edge that = (Edge) e;
        if (this.type != that.type) return -1;
        if (this.dstNode != null && that.dstNode != null) {
//            if (this.dstNode.canReturn != that.dstNode.canReturn) return -1;
        }
        return Utils.areEqual(this.srcNode, that.srcNode) ? 0 : -1;
    }

    @Override
    public boolean mergeWith(IEdge e) {    
        Edge that = (Edge) e;
        if (this.dstNode == null) {
            // 'this' is either NULL:<null,null> or FULL:<null,null>
            return false;
        }
        if (that.dstNode == null) {
            // 'this' is FULL:<null,AS> or FULL:<AS1,AS2> and 'that' is FULL<null,null>
            this.dstNode = that.dstNode;
            return true;
        }
        ArraySet<AccessPath> thisMS = new ArraySet<AccessPath>(this.dstNode.ms);
        ArraySet<AccessPath> thatMS = that.dstNode.ms;
  
        boolean canReturn = this.dstNode.canReturn == that.dstNode.canReturn ? this.dstNode.canReturn : false;
        
        if (thisMS.retainAll(thatMS)){
        	// Can return status remains the same
        	this.dstNode = new AbstractState(thisMS, canReturn);
        	return true;
        } else if (this.dstNode.canReturn != that.dstNode.canReturn) {
        	this.dstNode = new AbstractState(thisMS, canReturn);
        	return true;
        }
        
        return false;
    }
    
    @Override
    public String toString() {
        String t = "";
        switch(type) {
        case NULL: return "[NULL]";
        case FULL: t = "FULL"; break;
        }
        return "[" + t + ",s=[" + srcNode + "],d=[" + dstNode + "]]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Edge) {
            Edge that = (Edge) obj;
            return this.type == that.type &&
                Utils.areEqual(this.srcNode, that.srcNode) &&
                Utils.areEqual(this.dstNode, that.dstNode);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ((srcNode != null) ? srcNode.hashCode() : 0) +
               ((dstNode != null) ? dstNode.hashCode() : 0);
    }
}
