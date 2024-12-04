package chord.analyses.composba;

import gnu.trove.set.hash.TIntHashSet;

import java.util.BitSet;

import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.Utils;

public class BitEdge<F> implements IEdge {
	final public BitAbstractState srcNode;
	public BitAbstractState dstNode;

	public BitEdge() {
		srcNode = new BitAbstractState(new BitEnv<Register>());
		dstNode = new BitAbstractState(new BitEnv<Register>());
	}

	public BitEdge(BitAbstractState s, BitAbstractState d) {
		assert (s != null && d != null);
		srcNode = s;
		dstNode = d;
	}

	public BitEdge(String str) {
		String[] parts;
		parts = str.split("DESTNODE");
		if (!parts[0].equals(""))
			srcNode = new BitAbstractState(new BitEnv<Register>(), parts[0]);
		else
			srcNode = new BitAbstractState(new BitEnv<Register>());

		if (parts.length == 2 && !parts[1].equals("") && srcNode.badValue == false)
			dstNode = new BitAbstractState(new BitEnv<Register>(), parts[1]);
		else
			dstNode = new BitAbstractState(new BitEnv<Register>());
	}
		
	@Override
	public boolean isBadValue()
	{
		if (srcNode.badValue == true || dstNode.badValue == true) return true;
		return false;
	}
	
	/**
	 * Two path (or summary) edges for the same program point (or method) can be
	 * merged if the edges are of the same type, their source nodes match, and
	 * one's destination node subsumes the other's.
	 */
	@Override
	public int canMerge(IEdge e, boolean mustMerge) {
		assert (!mustMerge);  // not implemented yet
		BitEdge<F> that = (BitEdge<F>) e;

		return Utils.areEqual(this.srcNode, that.srcNode) ? 0 : -1;
	}

	@Override
	public boolean isSrcEqual(IEdge e) {
		BitEdge<F> that = (BitEdge<F>) e;
		if (this.srcNode == null) 
			return that.srcNode == null;
		return this.srcNode.envLocal.equals(that.srcNode.envLocal);
	}

	@Override
	public boolean subsumes(IEdge e) {
		BitEdge<F> that = (BitEdge<F>) e;
		return this.srcNode.subsumes(that.srcNode);
	}

	
	public int isMatching(IEdge e) {
		BitEdge<F> that = (BitEdge<F>) e;
		if (Utils.areEqual(this.srcNode, that.srcNode)) {
			return that.dstNode.envLocal.envMap.values().size();
		} else 
			return -1;
	
	}
	
	@Override
	public boolean mergeWith(IEdge e) {    
		BitEdge<F> that = (BitEdge<F>) e;
		
		assert(that.dstNode != null && this.dstNode != null);
		
		/*if (that.dstNode == null) {
			return false;
		}
		if (this.dstNode == null) {
			this.dstNode = that.dstNode;
			return true;
		}*/

		BitEnv<Register> newEnvLocal = new BitEnv<Register>(this.dstNode.envLocal);
		newEnvLocal.insert(that.dstNode.envLocal);

		BitSet newReturnVarEnv =  new BitSet();
		newReturnVarEnv.or(this.dstNode.returnVarEnv);
		newReturnVarEnv.or(that.dstNode.returnVarEnv);

		BitAbstractState newAbstractState = new BitAbstractState(newEnvLocal, newReturnVarEnv);

		if(newAbstractState.equals(this.dstNode))
			return false;
		else{
			this.dstNode = newAbstractState;
			return true;
		}
	}

	@Override
	public String toString() {
		return "[SOURCE=[" + srcNode + "],DEST=[" + dstNode + "]]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof BitEdge) {
			BitEdge<F> that = (BitEdge<F>) obj;
			return Utils.areEqual(this.srcNode, that.srcNode) &&
					Utils.areEqual(this.dstNode, that.dstNode);
		}
		return false;
	}

	
	public boolean isAppAccessed(TIntHashSet vSet) {
		if (srcNode.isAppAccessed(vSet)) return true;
		//else if (dstNode.isAppAccessed(null)) return true;
		else return false;
	}
	
	
	public String toParsableString(TIntHashSet vSet) {
		String srcStr = srcNode.toParsableString(vSet);
		String dstStr = dstNode.toParsableStringRetEnv();
		return srcStr + "DESTNODE" + dstStr;
	}

	@Override
	public int hashCode() {
		return ((srcNode != null) ? srcNode.hashCode() : 0) +
				((dstNode != null) ? dstNode.hashCode() : 0);
	}
}
		
