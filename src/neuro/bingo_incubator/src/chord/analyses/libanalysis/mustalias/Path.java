package chord.analyses.libanalysis.mustalias;

import java.util.LinkedHashSet;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Inst;
import chord.analyses.typestate.AccessPath;
import chord.analyses.typestate.Edge;
import chord.analyses.typestate.TypeState;
import chord.project.analyses.rhs.IEdge;
import chord.util.ArraySet;
import chord.util.Utils;

public class Path implements IEdge{
	public static final Path NULL = new Path(PathKind.NULL);

	Set<jq_Method> initMethodList;
	Set<jq_Method> finalMethodList;
	PathKind pathKind;

	Inst startInst;
	Inst endInst;

	public Path(PathKind pathKind){
		assert(pathKind != PathKind.QUERY || pathKind != PathKind.PARTIALCOLLECT || pathKind != PathKind.PARTIALCOLLECTDONE );
		if(pathKind == PathKind.COLLECT){
			this.startInst = null;
			this.endInst = null;
			this.initMethodList = null;
			this.finalMethodList = new LinkedHashSet<jq_Method>();
			this.pathKind = PathKind.COLLECT;
		}else if(pathKind == PathKind.NULL){
			this.startInst = null;
			this.endInst = null;
			this.initMethodList = null;
			this.finalMethodList = null;
			this.pathKind = PathKind.NULL;
		}
	}

	public Path(Inst startInst, Inst endInst){
		this.startInst = startInst;
		this.endInst = endInst;
		this.initMethodList = new LinkedHashSet<jq_Method>();
		this.finalMethodList = new LinkedHashSet<jq_Method>();
		this.pathKind = PathKind.QUERY;
	}

	public Path(Inst endInst){
		this.startInst = null;
		this.endInst = endInst;
		this.initMethodList = null;
		this.finalMethodList = new LinkedHashSet<jq_Method>();
		this.pathKind = PathKind.PARTIALCOLLECT;
	}

	public Path(Inst startInst, Inst endInst, Set<jq_Method> initMethodList, Set<jq_Method> finalMethodList, PathKind pathKind){
		assert(pathKind != PathKind.NULL);
		assert(finalMethodList != null);

		if(pathKind == PathKind.COLLECT){
			assert(startInst == null);
			assert(endInst == null);
			assert(initMethodList == null);
		}else if(pathKind == PathKind.QUERY){
			assert(startInst != null);
			assert(endInst != null);
			assert(initMethodList != null);
		}else{
			assert(startInst == null);
			assert(endInst != null);
			assert(initMethodList == null);
		}

		this.startInst = startInst;
		this.endInst = endInst;

		if(initMethodList != null)
			this.initMethodList = new LinkedHashSet<jq_Method>(initMethodList);
			else
				this.initMethodList = null;

		this.finalMethodList = new LinkedHashSet<jq_Method>(finalMethodList);

		this.pathKind = pathKind;
	}

	@Override
	public String toString(){		
		if(this.pathKind == PathKind.NULL)
			return "PathKind: NULL";

		String toPrint = "PathKind: " + pathKind + "\nStartInst:" + startInst +  "\nEndInst:" + endInst +"\nInitMList: ";

		if(initMethodList == null)
			toPrint += "null";
		else{
			for(jq_Method m : initMethodList)
				toPrint += m + ", ";
		}

		toPrint += "\nFinalMList: ";
		if(finalMethodList == null)
			toPrint += "null";
		else{
			for(jq_Method m : finalMethodList)
				toPrint += m + ", ";
		}

		return toPrint;
	}

	@Override
	public int canMerge(IEdge edge, boolean mustMerge) {
		assert (!mustMerge);  // not implemented yet
		Path that = (Path) edge;
		if (this.pathKind != that.pathKind || this.startInst != that.startInst || this.endInst != that.endInst) return -1;

		int flag = 0;
		if(this.finalMethodList != null && that.finalMethodList != null){
			if (!this.finalMethodList.containsAll(that.finalMethodList) && !that.finalMethodList.containsAll(this.finalMethodList))
				return -1;
		}

		return Utils.areEqual(this.initMethodList, that.initMethodList) ? 0 : -1;
	}

	@Override
	public boolean mergeWith(IEdge edge) {
		Path that = (Path) edge;
		if (that.finalMethodList == null || this.finalMethodList == null) {
			// 'that' is NULL && 'this' is NULL
			return false;
		}

		if (this.finalMethodList.containsAll(that.finalMethodList)){
			return false;
		}

		this.finalMethodList = that.finalMethodList;
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj instanceof Path) {
			Path that = (Path) obj;
			return this.startInst == that.startInst &&
					this.endInst == that.endInst &&
					Utils.areEqual(this.initMethodList, that.initMethodList) &&
					Utils.areEqual(this.finalMethodList, that.finalMethodList) &&
					this.pathKind == that.pathKind;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (((startInst != null) ? startInst.hashCode() : 0) +
				((endInst != null) ? endInst.hashCode() : 0) +
				((initMethodList != null) ? initMethodList.hashCode() : 0) +
				((finalMethodList != null) ? finalMethodList.hashCode() : 0));
	}
}

