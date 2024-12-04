package chord.analyses.bigcode.connection;

import gnu.trove.set.hash.TIntHashSet;

import java.util.List;
import java.util.Set;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.var.DomV;

public class UtilsNullness extends Utils {

	public UtilsNullness(DomV domVpar, DomF domFpar) {
		super(domVpar, domFpar);
	}

	public VariablesPartitionNullness getInitCalleePartitionNullness(VariablesPartitionNullness callerPartition,  Quad invkQuad, jq_Method invkMethod){
		
		ParamListOperand list = Invoke.getParamList(invkQuad);
		//copying the empty partition to a new partition
		VariablesPartitionNullness calleePartition = new VariablesPartitionNullness(VariablesPartitionNullness.createDinit());
		visitedStatic.clear();
		List<Register> methodVars = invkMethod.getLiveRefVars();
		Set<Integer> methStaticVars = methToStaticVars.get(invkMethod); 
		boolean [] visitedParams = new boolean[methodVars.size()];
		int numArgs = list.length();

		
		//getting the null set of the calleer
		Set<Variable> nullSet = callerPartition.getSet(VariablesPartitionNullness.NULL_VAR);
		for (Variable v: nullSet)
		{
			//if v is old or null, ignore
			if (v == VariablesPartitionNullness.NULL_VAR || v.isOld()) continue; 
			//if v is static, add v to the nullset of the callee partition
			if (v.isStatic() && methStaticVars.contains(v.index) && visitedStatic.add(v.index))
			{	
				calleePartition.addLocal(VariablesPartitionNullness.NULL_VAR, v);
			}
			else if (!v.isStatic)
			{
				//if v is not static, then check if it is one of the arguments and if so add the 
				// matching parameter to the null set
				Register r = domV.get(v.index);
				int toMergeRefFormalIndex = getFormalIndexFromArg(list, r, methodVars);
				if ((toMergeRefFormalIndex != -1) && (toMergeRefFormalIndex < numArgs) && (!visitedParams[toMergeRefFormalIndex]))
				{
					visitedParams[toMergeRefFormalIndex] = true;
					Register rToMergeFormal = methodVars.get(toMergeRefFormalIndex);
					int toMergeIndex = domV.indexOf(rToMergeFormal);			
					calleePartition.addLocal(VariablesPartitionNullness.NULL_VAR , LocalVariable.getNewLocalVariable(toMergeIndex));
				}
			}
		}
	
		
		for (int i=0, fIdx = 0; (i<numArgs && fIdx<methodVars.size()); i++)
		{			
			RegisterOperand ao = list.get(i);
			
			if (!ao.getType().isReferenceType()) continue;
			
			Register r = methodVars.get(fIdx);
			int formalIdx = domV.indexOf(r);
			LocalVariable formalVar = LocalVariable.getNewLocalVariable(formalIdx);	
			
			if (visitedParams[fIdx]) 
			{
				fIdx ++;
				continue;
			}
			
			visitedParams[fIdx] = true;		
			
			int aIdx = getAbsIdx(ao);
			
			Set<Variable> aSet = callerPartition.getSet(LocalVariable.getNewLocalVariable(aIdx));
			
			if (aSet != null)
			{
				//sanity check, we should have already took care of all null vars.
				
				if(aSet.contains(VariablesPartitionNullness.NULL_VAR))
				{
					//this should happen only in case of same register sent twice
//					System.out.println(invkQuad.toVerboseStr());
//					System.out.println(callerPartition);
//					System.out.println("arg is "+ ao);
//					System.out.println("formal is  " + fIdx);
					continue;
				}
				for (Variable v : aSet) 
				{
					if (v.isOld()) continue;
					
					if  (v.isStatic() && methStaticVars.contains(v.index))
					{
						if (!visitedStatic.add(v.index)) continue;
						calleePartition.addLocal(formalVar, v);		
					}
					else 
					{
						Register rToMergeArg = domV.get(v.index);
						
						int toMergeRefFormalIndex = getFormalIndexFromArg(list, rToMergeArg, methodVars);
						if (rToMergeArg.equals(ao))
						{
							//sanity check
							assert(toMergeRefFormalIndex == fIdx);
						}
						if ((toMergeRefFormalIndex != -1) && (toMergeRefFormalIndex < numArgs) && (!visitedParams[toMergeRefFormalIndex]))
						{
							visitedParams[toMergeRefFormalIndex] = true;
							Register rToMergeFormal = methodVars.get(toMergeRefFormalIndex);
							int toMergeIndex = domV.indexOf(rToMergeFormal);			
							calleePartition.addLocal(formalVar,	LocalVariable.getNewLocalVariable(toMergeIndex));
						}
					}
				}
			}
			//don't need to merge this because we will merge this in the previous if
			fIdx++;
		}

		for (int statIdx : methStaticVars) 
		{			
		//	calleePartition = calleePartition.StaticVariable.getNewStaticVariable(statIdx), StaticVariable.getNewStaticVariable(statIdx+VariablesPartition.MAX_VARS));	
			if (!visitedStatic.add(statIdx)) continue;
			Set<Variable> aSet = callerPartition.getSet(StaticVariable.getNewStaticVariable(statIdx));
			if (aSet != null)
			{
				//sanity check, we should have already took care of null vars.
				assert(!aSet.contains(VariablesPartitionNullness.NULL_VAR));
				
				for (Variable v : aSet) 
				{		
					//don't need to merge with local variables because we already did it in the previous for.
					if (v.isStatic() &&  !v.isOld() && methStaticVars.contains(v.index) && visitedStatic.add(v.index))
					{
						calleePartition.addLocal(StaticVariable.getNewStaticVariable(statIdx), v);
						//calleePartition = calleePartition.merge(StaticVariable.getNewStaticVariable(statIdx), v);
						
					}
				}
			}
		}
		for (int i = getNumberOfRefArgs(list); i<methodVars.size(); i++)
		{
			//initialize all the local vars to null
			Register r = methodVars.get(i);
			//sanity check : we are not initializing parameters
			assert(getArgIndexFromFormal(list, r, methodVars) == -1);
			calleePartition.addLocal(VariablesPartitionNullness.NULL_VAR, LocalVariable.getNewLocalVariable(domV.indexOf(r)));
		}
		//copying to new object because we modified on place for better performance
		calleePartition = new VariablesPartitionNullness(calleePartition);
		return 	calleePartition;
	}

	public VariablesPartitionNullness getTargetCallerPartitionNullness(
			VariablesPartitionNullness callerPart,
			VariablesPartitionNullness calleePart, Set<Integer> retIdxs,
			Quad invkQuad, jq_Method invkMethod) 
	{	
		
		ParamListOperand list = null;
		VariablesPartitionNullness callerTargetPart = callerPart;
		List<Register> methodVars = invkMethod.getLiveRefVars();
		RegisterOperand dstReg = null;
		Set<Integer> methStaticVars = methToStaticVars.get(invkMethod);
		int numArgs = 0;
		boolean visitedArgs[] = null;
		if (invkQuad != null)
		{
			list = Invoke.getParamList(invkQuad);
			dstReg = Invoke.getDest(invkQuad);
			numArgs = list.length();
			visitedArgs = new boolean[numArgs];
		}

		visitedStatic.clear();
		Set<Variable> nullSet = calleePart.getSet(VariablesPartitionNullness.NULL_VAR);
		for (Variable v: nullSet)
		{
			//all the static null vars of callee should be null in caller
			if (v.isStatic() && (!v.isOld()) && (visitedStatic.add(v.index))) {
				
				callerTargetPart = (VariablesPartitionNullness) callerTargetPart.remove(v);
				callerTargetPart = (VariablesPartitionNullness) callerTargetPart
						.merge(VariablesPartitionNullness.NULL_VAR, v);
			} 	
		}
		
		for (int i = 0, fIdx = 0; (i < numArgs && fIdx < methodVars.size()); i++) {
			
			RegisterOperand argReg = list.get(i);
			//if it is not reference type, then skip without increasing the formal idx
			if (!argReg.getType().isReferenceType())
				continue;

			Register formalReg = methodVars.get(fIdx);

			int formalIdx = domV.indexOf(formalReg);

			if (visitedArgs[i]) {
				fIdx++;
				continue;
			}

			visitedArgs[i] = true;

			int argIdx = getAbsIdx(argReg);
			if (argIdx == -1) {
				assert (false);
			}
			
			Set<Variable> calleeSet = calleePart.getSet(LocalVariable
					.getNewLocalVariable(formalIdx
							+ VariablesPartition.MAX_VARS));
			
			if (calleeSet != null && calleeSet != nullSet) {
				//old value of the formal var was merged to someone: need to merge the matching argument
				for (Variable v : calleeSet) {
					if (v.isStatic()) {
						if (visitedStatic.add(v.index)) {
							if (!v.isOld()) {
								// if v is not old, merge it
								if (!calleeSet.contains(StaticVariable.getNewStaticVariable(v.index + VariablesPartition.MAX_VARS))){
									callerTargetPart = (VariablesPartitionNullness) callerTargetPart.remove(v);
								}
								callerTargetPart = (VariablesPartitionNullness) callerTargetPart
										.merge(LocalVariable
												.getNewLocalVariable(argIdx), v);
							} else {
								//if we merged it with someone old: take the representatives.
								Set<Variable> otherVars = callerPart
										.getOthersExternal(
												StaticVariable
														.getNewStaticVariable(v.index
																- VariablesPartition.MAX_VARS),
												methStaticVars);

								for (Variable otherVar : otherVars) {
									callerTargetPart = (VariablesPartitionNullness)  callerTargetPart
											.merge(LocalVariable
													.getNewLocalVariable(argIdx),
													otherVar);
								}
							}
						}
					}
					else {
						// if v local and not new, skip it, as references are sent by value
						if (!v.isOld())
							continue;
						// v is old
						Register rToMergeFormal = domV.get(v.index
								- VariablesPartition.MAX_VARS);
						//take the matching formalIdx
						int toMergeArgIndex = getArgIndexFromFormal(list,
								rToMergeFormal, methodVars);
						//sanity check;
						assert(toMergeArgIndex < numArgs);
						if ((toMergeArgIndex != -1)
							&& (toMergeArgIndex < numArgs)
							&& (!visitedArgs[toMergeArgIndex])) 
						{
							visitedArgs[toMergeArgIndex] = true;
							RegisterOperand rToMergeArg = list.get(toMergeArgIndex);
							int toMergeIndex = domV.indexOf(rToMergeArg
									.getRegister());
							if (toMergeIndex == -1) {
								System.out.println(rToMergeArg);
								System.out.println(invkQuad);
								System.out.println(toMergeArgIndex);
								assert (false);
							}

							callerTargetPart = (VariablesPartitionNullness) callerTargetPart.merge(
									LocalVariable.getNewLocalVariable(argIdx),
									LocalVariable.getNewLocalVariable(toMergeIndex));
						}
					}
				}
			}
			fIdx++;
		}

		//go over static vars
		for (int statIdx : methStaticVars) {
			StaticVariable statVarNew = StaticVariable.getNewStaticVariable(statIdx);

			//the set didn't change
			if (calleePart.getSet(statVarNew) == null) {
				continue;
			}
			
			if (visitedStatic.add(statIdx)) {
				//if v is new, update it.
				if(!calleePart.getSet(statVarNew).contains(StaticVariable
						.getNewStaticVariable(statIdx+ VariablesPartition.MAX_VARS))){
					callerTargetPart = (VariablesPartitionNullness) callerTargetPart.remove(statVarNew);
				}
				callerTargetPart = updateStaticVariableNullness(callerPart, calleePart,
						callerTargetPart, statVarNew, statVarNew,
						visitedStatic, methStaticVars, false);
			}
			if (visitedStatic.add(statIdx + VariablesPartition.MAX_VARS)) {
				//if v is old, update the corresponding old values
				StaticVariable baseVar = StaticVariable
						.getNewStaticVariable(statIdx+ VariablesPartition.MAX_VARS);
				Set<Variable> oldVars = callerPart.getOthersExternal(
						StaticVariable.getNewStaticVariable(statIdx),
						methStaticVars);
				for (Variable oldVar : oldVars) {
					callerTargetPart = updateStaticVariableNullness(callerPart,
							calleePart, callerTargetPart, oldVar, baseVar,
							visitedStatic, methStaticVars, false);
				}
			}
		}
	
//		if (invkQuad != null  && 
//				invkQuad.toString().contains("5: INVOKEVIRTUAL_A T2, toString:")) 
//		{
//			System.out.println(invkQuad);
//			System.out.println("looking for "+ "11!toString:()Ljava/lang/String;@org.apache.lucene.search.BooleanClause");
//		}
	
		//return
		if (dstReg != null
				&& dstReg.getType().isReferenceType()) {
			int dstIdx = getAbsIdx(dstReg);
			LocalVariable dstVar = LocalVariable.getNewLocalVariable(dstIdx);
			
			boolean mustBeNull = true;
			//doing a strong update to the dstVar
			callerTargetPart = (VariablesPartitionNullness) callerTargetPart.remove(dstVar);
			if (retIdxs != null)
			{
				for (int idx : retIdxs) {
					if (idx == VariablesPartitionNullness.NULL_INT) continue;
					if (!nullSet.contains(LocalVariable.getNewLocalVariable(idx)))
					{
						mustBeNull = false;
						break;
					}
				}
			}
			else
			{
				mustBeNull = false;
			}
			//ret is null only if all the possibles values are null
			if (mustBeNull)
			{
				callerTargetPart = (VariablesPartitionNullness) callerTargetPart.merge(VariablesPartitionNullness.NULL_VAR, dstVar);		
			}
			else
			{
				if (retIdxs !=  null)
				{
					for (int idx : retIdxs) {
						Set<Variable> calleeSet = calleePart.getSet(LocalVariable
								.getNewLocalVariable(idx));
						if (calleeSet == nullSet) continue;
						else if (calleeSet == null) {
							calleeSet = VariablesPartition.getBasicSet(idx, false);
						}
						for (Variable v : calleeSet) 
						{	
							if (v.isStatic()) {
								if (!v.isOld()) {
									callerTargetPart = (VariablesPartitionNullness) callerTargetPart
											.merge(dstVar, v);
								} else {
									Set<Variable> otherVars = callerPart
											.getOthersExternal(
													StaticVariable
															.getNewStaticVariable(v.index
																	- VariablesPartition.MAX_VARS),
													methStaticVars);
		
									for (Variable otherVar : otherVars) {
										callerTargetPart = (VariablesPartitionNullness) callerTargetPart.merge(
												dstVar, otherVar);
									}
								}
							} else {
								if (!v.isOld())
									continue;
									
								//merge all the variables with the return variable with the destination
								Register rToMergeFormal = domV.get(v.index - VariablesPartition.MAX_VARS);
								int toMergeArgIndex = getArgIndexFromFormal(list, rToMergeFormal, methodVars);
								assert(toMergeArgIndex < numArgs);
								if ((toMergeArgIndex != -1)
										&& (toMergeArgIndex < numArgs)) {
									RegisterOperand rToMergeArg = list
											.get(toMergeArgIndex);
									int toMergeIndex = domV.indexOf(rToMergeArg.getRegister());
									if (toMergeIndex == -1) {
										System.out.println(rToMergeArg);
										System.out.println(invkQuad);
										System.out.println(toMergeArgIndex);
										assert (false);
									}
									LocalVariable toMergeVar = LocalVariable.getNewLocalVariable(toMergeIndex);
									callerTargetPart = (VariablesPartitionNullness) callerTargetPart.merge(dstVar,
											toMergeVar);
									Set<Variable> otherVars = callerPart.getOthersExternal(toMergeVar,
													methStaticVars);
									for (Variable otherVar : otherVars) {
										callerTargetPart = (VariablesPartitionNullness) callerTargetPart.merge(
												dstVar, otherVar);
									}
								}
							}
						}
					}
				}
			}
		}
		return callerTargetPart;
	}

	VariablesPartitionNullness updateStaticVariableNullness(
			VariablesPartitionNullness initPart,
			VariablesPartitionNullness summaryPart,
			VariablesPartitionNullness targetPart, Variable mergingVar,
			Variable baseVar, TIntHashSet visitedStatic,
			Set<Integer> methStaticVars, boolean local) {
		
		//get all the variables together with the base var
		Set<Variable> aSet = summaryPart.getSet(baseVar);

		if (aSet == null) {
			aSet = VariablesPartition.getBasicSet(baseVar.index,
					baseVar.isStatic);
		}

		for (Variable v : aSet) {
			if (v.isStatic() && visitedStatic.add(v.index)) {
				if (v.isOld()) {
					Set<Variable> others;
					if (local) {
						others = initPart.getOthersLocal(StaticVariable
								.getNewStaticVariable(v.index
										- VariablesPartition.MAX_VARS));
					} else {
						others = initPart.getOthersExternal(
								StaticVariable.getNewStaticVariable(v.index
										- VariablesPartition.MAX_VARS),
								methStaticVars);
					}
					for (Variable var2 : others) {
						targetPart = (VariablesPartitionNullness) targetPart.merge(mergingVar, var2);
					}
				} else {
					// removing only if not together with the old var
					if (!aSet.contains(StaticVariable.getNewStaticVariable(v.index
										+ VariablesPartition.MAX_VARS))){
						targetPart = (VariablesPartitionNullness) targetPart.remove(v);
					}
					targetPart = (VariablesPartitionNullness) targetPart.merge(mergingVar, v);
				}
			}
		}
		return targetPart;
	}

	@Override
	public VariablesPartition getInitCalleePartition(VariablesPartition callerPartition,  Quad invkQuad, jq_Method invkMethod){
		assert(false);
		return null;
	}
}
