package chord.analyses.bigcode.connection;

import gnu.trove.set.hash.TIntHashSet;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import chord.analyses.field.DomF;
import chord.analyses.var.DomV;
import chord.project.OutDirUtils;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;

public class Utils 
{	
	protected DomV domV;
	protected DomF domF;
	
	protected HashMap<jq_Method, Set<Integer>> methToStaticVars;
	protected TIntHashSet visitedStatic = new TIntHashSet();
	
	public Utils(DomV domVpar, DomF domFpar)
	{
		domV = domVpar;
		domF = domFpar;
	}	

	public int getAbsIdx(RegisterOperand ro) 
	{
		Register r = ro.getRegister();
		int vIdx = domV.indexOf(r);
		return vIdx;
	}

	public int getStaticIdx(jq_Field f) 
	{
		return domF.indexOf(f);
	}
	
	public HashMap<jq_Method, Set<Integer>> getMethToStaticVars(){
		return methToStaticVars;
	}
	
	protected int getArgIndexFromFormal(ParamListOperand list, Register rFormal, List<Register> methodVars) 
	{		
		int argsSize = list.length();
		int formalIndex = domV.indexOf(rFormal);
		int formalIdx = 0;
		
		for (int i=0; i<argsSize; i++)
		{	
			RegisterOperand ro = list.get(i);
			if (!ro.getType().isReferenceType()) 
			{
				continue;
			}
			
			Register formal = methodVars.get(formalIdx);
			
			int index = domV.indexOf(formal);
			if (formalIndex == index)
			{
				return i;
			}
			formalIdx++;
		}
		return -1;
	}

	protected int getNumberOfRefArgs(ParamListOperand list){
		
		int argsSize = list.length();
		int count = 0;
		for (int i=0; i<argsSize; i++)
		{
			RegisterOperand ro = list.get(i);
			if (!ro.getType().isReferenceType()) 
			{
				continue;
			}
			count ++;
		}
		return count;
	}
	
	protected int getFormalIndexFromArg(ParamListOperand list, Register rArg, List<Register> methodVars) 
	{		
		int argsSize = list.length();
		int argIndex = domV.indexOf(rArg);
		int formalIdx = 0;
		for (int i=0; i<argsSize; i++)
		{	
			RegisterOperand ro = list.get(i);
			if (!ro.getType().isReferenceType()) 
			{
				continue;
			}
					
			int index = domV.indexOf(ro.getRegister());
			if (argIndex == index)
			{
				return formalIdx;
			}
			formalIdx ++;
		}
		return -1;
	}
	
	
	public VariablesPartition getInitCalleePartition(VariablesPartition callerPartition,  Quad invkQuad, jq_Method invkMethod)	
	{
		ParamListOperand list = Invoke.getParamList(invkQuad);
		
		VariablesPartition calleePartition = new VariablesPartition(VariablesPartition.createDinit());
		visitedStatic.clear();
		List<Register> methodVars = invkMethod.getLiveRefVars();
		Set<Integer> methStaticVars = methToStaticVars.get(invkMethod); 
		boolean [] visitedParams = new boolean[methodVars.size()];
		int numArgs = list.length();
		
		for (int i=0, fIdx = 0; (i<numArgs && fIdx<methodVars.size()); i++)
		{			
			RegisterOperand ao = list.get(i);
			if (!ao.getType().isReferenceType()) continue;

			Register r = methodVars.get(fIdx);
			int formalIdx = domV.indexOf(r);
			LocalVariable formalVar = LocalVariable.getNewLocalVariable(formalIdx);
		//	calleePartition.mergeLocal(LocalVariable.getNewLocalVariable(formalIdx), LocalVariable.getNewLocalVariable(formalIdx+VariablesPartition.MAX_VARS));	
			
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
				for (Variable v : aSet) 
				{
					if (v.isOld()) continue;
					
					if  (v.isStatic())
					{
						if (methStaticVars.contains(v.index)){
							if (!visitedStatic.add(v.index)) continue;
							calleePartition.addLocal(formalVar, v);	
							//calleePartition = calleePartition.merge(formalVar, v);
						}
					}
					else 
					{
						Register rToMergeArg = domV.get(v.index);
						int toMergeRefFormalIndex = getFormalIndexFromArg(list, rToMergeArg, methodVars);
						if ((toMergeRefFormalIndex != -1) && (toMergeRefFormalIndex < numArgs) && (!visitedParams[toMergeRefFormalIndex]))
						{
							visitedParams[toMergeRefFormalIndex] = true;
							Register rToMergeFormal = methodVars.get(toMergeRefFormalIndex);
							int toMergeIndex = domV.indexOf(rToMergeFormal);
							calleePartition.addLocal(formalVar,	LocalVariable.getNewLocalVariable(toMergeIndex));
							//calleePartition = calleePartition.merge(formalVar, LocalVariable.getNewLocalVariable(toMergeIndex));
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
				for (Variable v : aSet) 
				{			
					if (v.isStatic() &&  !v.isOld() && methStaticVars.contains(v.index) && visitedStatic.add(v.index)){
					
						calleePartition.addLocal(StaticVariable.getNewStaticVariable(statIdx), v);
						//calleePartition = calleePartition.merge(StaticVariable.getNewStaticVariable(statIdx), v);
					//don't need to merge with local variables because we already did it in the previous for.
					}
				}
			}
		}	
		return new VariablesPartition(calleePartition);
	}
		
	static boolean print = false;
	public VariablesPartition getTargetCallerPartition(VariablesPartition callerPart, VariablesPartition calleePart,Set<Integer> retIdxs, Quad invkQuad, jq_Method invkMethod)
	{	
		ParamListOperand list = null;
		int numArgs = 0;
	//	VariablesPartition testTargetPart = callerPart;
		VariablesPartition callerTargetPart = new VariablesPartition(callerPart);
		List<Register> methodVars = invkMethod.getLiveRefVars();
		boolean visitedArgs[] =  null;
		RegisterOperand dstReg = null;
		if (invkQuad != null)
		{
			dstReg = Invoke.getDest(invkQuad);
			list =  Invoke.getParamList(invkQuad);
			numArgs = list.length();
			visitedArgs = new boolean[numArgs];
		}
	/*	if (invkQuad != null && invkQuad.toVerboseStr().contains("setUp:()V@weka.classifiers.AbstractClassifierTest")){
			System.out.println("found "+invkQuad);
			System.out.println(callerTargetPart);
			print = true;
		}
		else
			print = false;
	*/	
		Set<Integer> methStaticVars = methToStaticVars.get(invkMethod); 
		 
		
		visitedStatic.clear();
	
			
		for (int i=0, fIdx = 0; (i<numArgs && fIdx < methodVars.size()); i++)
		{	    		
			RegisterOperand argReg = list.get(i);
			if (!argReg.getType().isReferenceType()) continue;
			Register formalReg = methodVars.get(fIdx);
			
			int formalIdx = domV.indexOf(formalReg);
			
			if (visitedArgs[i]) 
			{
				fIdx ++;
				continue;
			}
			
			visitedArgs[i] = true; 
			
			int argIdx = getAbsIdx(argReg);
			if (argIdx == -1 )
			{
				assert(false);
			}
			
			Set<Variable> calleeSet = calleePart.getSet(LocalVariable.getNewLocalVariable(formalIdx + VariablesPartition.MAX_VARS));
			if (calleeSet != null)
			{
				for (Variable v: calleeSet)
				{	
					if (v.isStatic())
					{
						if (visitedStatic.add(v.index))
						{	
							if (!v.isOld())
							{
							//	callerTargetPart.removeLocal(v);
								if (!calleeSet.contains(StaticVariable.getNewStaticVariable(v.index +  VariablesPartition.MAX_VARS))){
									callerTargetPart = callerTargetPart.remove(v);
								}
							//	testTargetPart = testTargetPart.remove(v);
							//	assert(callerTargetPart.equals(testTargetPart));
								callerTargetPart = callerTargetPart.merge(LocalVariable.getNewLocalVariable(argIdx), v);
								//callerTargetPart.mergeLocal(LocalVariable.getNewLocalVariable(argIdx), v);
							//	assert(callerTargetPart.equals(testTargetPart));
							}
							else
							{
								Set<Variable> otherVars = callerPart.getOthersExternal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS), methStaticVars);
								
								for (Variable otherVar:  otherVars)
								{
									callerTargetPart = callerTargetPart.merge(LocalVariable.getNewLocalVariable(argIdx), otherVar);
									//	callerTargetPart.mergeLocal(LocalVariable.getNewLocalVariable(argIdx), otherVar);
								//	assert(callerTargetPart.equals(testTargetPart));
								}
							}
						}
					} 
					else 
					{
						if (!v.isOld()) continue;
							
						Register rToMergeFormal = domV.get(v.index - VariablesPartition.MAX_VARS);
						int toMergeArgIndex = getArgIndexFromFormal(list, rToMergeFormal, methodVars);
						if ((toMergeArgIndex != -1) && (toMergeArgIndex < numArgs) && (!visitedArgs[toMergeArgIndex]))	
						{						
							visitedArgs[toMergeArgIndex] = true;
							RegisterOperand rToMergeArg= list.get(toMergeArgIndex);
							int toMergeIndex = domV.indexOf(rToMergeArg.getRegister());
							if (toMergeIndex == -1)
							{
								System.out.println(rToMergeArg);
								System.out.println(invkQuad);
								System.out.println(toMergeArgIndex);
								assert(false);
							}
							
							callerTargetPart = callerTargetPart.merge(LocalVariable.getNewLocalVariable(argIdx), LocalVariable.getNewLocalVariable(toMergeIndex));
							//	callerTargetPart.mergeLocal(LocalVariable.getNewLocalVariable(argIdx), LocalVariable.getNewLocalVariable(toMergeIndex));
						//	assert(callerTargetPart.equals(testTargetPart));
						}
					}
				}		
			}
			fIdx++;
		}
	
		for (int statIdx : methStaticVars) 
		{
			//boolean test = false;
			StaticVariable statVarNew = StaticVariable.getNewStaticVariable(statIdx);
			//VariablesPartition oldPart = null;
			if (//(calleePart.getSet(statVarNew) ==  null) && 
				(calleePart.getSet(statVarNew) ==  null))
			{
				//oldPart = new VariablesPartition(callerTargetPart);
				//assert(callerTargetPart.getSet(statVarNew) == null);
				continue;
				//test = true;
			}
			
			if (visitedStatic.add(statIdx))
			{		
				if (!calleePart.getSet(statVarNew).contains(StaticVariable.getNewStaticVariable(statIdx+VariablesPartition.MAX_VARS))){
					callerTargetPart = callerTargetPart.remove(statVarNew);
				}
				//callerTargetPart.removeLocal(statVarNew);
				//assert(testTargetPart.equals(callerTargetPart));
				callerTargetPart = updateStaticVariable(callerPart, calleePart, callerTargetPart, statVarNew, statVarNew, visitedStatic, methStaticVars,false);
				//updateStaticVariableLocal(callerPart, calleePart, callerTargetPart, statVarNew, statVarNew, visitedStatic, methStaticVars,false);
				//assert(testTargetPart.equals(callerTargetPart));
			}
			if (visitedStatic.add(statIdx+VariablesPartition.MAX_VARS))
			{			
				StaticVariable baseVar = StaticVariable.getNewStaticVariable(statIdx+VariablesPartition.MAX_VARS);
				Set<Variable> oldVars = callerPart.getOthersExternal(StaticVariable.getNewStaticVariable(statIdx), methStaticVars);
				for (Variable oldVar: oldVars) 
				{
					//merge all the representative of the new variable corresponding to base var and merge them with the ones which are
					// now equivalent to the base var (the old var)
					callerTargetPart = updateStaticVariable(callerPart, calleePart, callerTargetPart, oldVar, baseVar, visitedStatic, methStaticVars,false);
					//updateStaticVariableLocal(callerPart, calleePart, callerTargetPart, oldVar, baseVar, visitedStatic, methStaticVars,false);
					//assert(testTargetPart.equals(callerTargetPart));
				}
			}
		}
		if (dstReg != null && dstReg.getType().isReferenceType())
		{
			int dstIdx = getAbsIdx(dstReg);
			//doing a strong update the the destination register
			LocalVariable dstVar = LocalVariable.getNewLocalVariable(dstIdx);
			callerTargetPart = callerTargetPart.remove(dstVar);
				//callerTargetPart.removeLocal(dstVar);
				//assert(testTargetPart.equals(callerTargetPart));
			//merging now all the variables which are equivalent to the returned vars
			if (retIdxs != null)
			{
				for (int idx : retIdxs)
				{
					Set<Variable> calleeSet = calleePart.getSet(LocalVariable.getNewLocalVariable(idx));
					if (calleeSet == null)
					{
						calleeSet = VariablesPartition.getBasicSet(idx, false);
					}
					for (Variable v: calleeSet)
					{	
						
						//if v is static
						if (v.isStatic())
						{
							//if v is new, then just merge it
							if (!v.isOld())
							{
								callerTargetPart = callerTargetPart.merge(dstVar, v);
								//callerTargetPart.mergeLocal(dstVar, v);
								//assert(testTargetPart.equals(callerTargetPart));
							}
							else
							{
								//else merge all who used to be equivalent to it
								Set<Variable> otherVars = callerPart.getOthersExternal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS),methStaticVars);
								
								for (Variable otherVar:  otherVars)
								{
									callerTargetPart = callerTargetPart.merge(dstVar, otherVar);
									//callerTargetPart.mergeLocal(dstVar, otherVar);
									//assert(testTargetPart.equals(callerTargetPart));
								}
							}
						}
						else //if v is local, then it might be a parameter 
						{
							//new values of parameters don't influence the caller
							if (!v.isOld()) continue;
							
							//search for the parameters among the arguments
							Register rToMergeFormal = domV.get(v.index - VariablesPartition.MAX_VARS);
							int toMergeArgIndex = getArgIndexFromFormal(list, rToMergeFormal, methodVars);
							if ((toMergeArgIndex != -1) && (toMergeArgIndex < numArgs))	
							{						
								RegisterOperand rToMergeArg= list.get(toMergeArgIndex);
								int toMergeIndex = domV.indexOf(rToMergeArg.getRegister());
								if (toMergeIndex == -1)
								{
									System.out.println(rToMergeArg);
									System.out.println(invkQuad);
									System.out.println(toMergeArgIndex);
									assert(false);
								}
								LocalVariable toMergeVar = LocalVariable.getNewLocalVariable(toMergeIndex);
								//callerTargetPart.mergeLocal(dstVar, toMergeVar);
								callerTargetPart = callerTargetPart.merge(dstVar, toMergeVar);
								//assert(testTargetPart.equals(callerTargetPart));
								//TODO: I am not sure if I really need this
								Set<Variable> otherVars = callerPart.getOthersExternal(toMergeVar,methStaticVars);
								for (Variable otherVar:  otherVars)
								{
									callerTargetPart = callerTargetPart.merge(dstVar, otherVar);
									//callerTargetPart.mergeLocal(dstVar, otherVar);
									//assert(testTargetPart.equals(callerTargetPart));
								}
							}
						}
					}
				}
			}
		}
		return callerTargetPart;		
	}	
	
	//This function is used to update the partition of merging var, to the one of base var in the summary partition.
	public VariablesPartition updateStaticVariable(VariablesPartition initPart, VariablesPartition summaryPart, VariablesPartition targetPart,
			Variable mergingVar, Variable baseVar, TIntHashSet visitedStatic,Set<Integer> methStaticVars,  boolean local)
	{
		
		if (print) System.out.println("baseVar is "+ baseVar);
		if (print) System.out.println("mergingVar is "+ mergingVar);
		if (print) System.out.println("initpart is "+ initPart);
		
		//getting the set of the base var
		Set<Variable> aSet = summaryPart.getSet(baseVar);
		if (print) System.out.println(aSet);
		if(aSet == null)
		{
			aSet = VariablesPartition.getBasicSet(baseVar.index, baseVar.isStatic);
		}
		
		//for each one of the variables in the set
		for (Variable v : aSet) 
		{
			//go over all the static variables 
			if (v.isStatic() && visitedStatic.add(v.index))
			{
				//if v is old then merge with the merging variables all the representatives of v
				if (v.isOld())
				{
					Set<Variable> others;
					if (local)
					{
						others = initPart.getOthersLocal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS));
						if (print) System.out.println("others = "+others);
					}
					else
					{
						others = initPart.getOthersExternal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS),methStaticVars);
						
						if (print) System.out.println("others = "+others);
					}
					if (print) System.out.println("others before cleaning = "+initPart.getSet(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS)));
					if (print) System.out.println("targetPart before merging with others = "+targetPart);
					for (Variable var2: others)
					{
						targetPart = targetPart.merge(mergingVar, var2);
					}
					if (print) System.out.println("targetPart after merging with others = "+targetPart);
				}
				else
				{
					
					//otherwise update v and merge it with the merging var
					//removing only if not together with the old variable
					if (!aSet.contains(StaticVariable.getNewStaticVariable(v.index + VariablesPartition.MAX_VARS))){
						targetPart = targetPart.remove(v);
					}
					targetPart = targetPart.merge(mergingVar, v);	
					if (print) System.out.println("targetPart after merging with v = "+targetPart);
				}
			}
		}
		return targetPart;
	}
	
//	public void updateStaticVariableLocal(VariablesPartition initPart, VariablesPartition summaryPart, VariablesPartition targetPart,
//			Variable mergingVar, Variable baseVar, TIntHashSet visitedStatic,Set<Integer> methStaticVars,  boolean local)
//	{
//		Set<Variable> aSet = summaryPart.getSet(baseVar);
//		//VariablesPartition testTargetPart = targetPart;
//		if(aSet == null)
//		{
//			aSet = VariablesPartition.getBasicSet(baseVar.index, baseVar.isStatic);
//		}
//		
//		for (Variable v : aSet) 
//		{
//			if (v.isStatic() && visitedStatic.add(v.index))
//			{
//				if (v.isOld())
//				{
//					Set<Variable> others;
//					if (local)
//					{
//						others = initPart.getOthersLocal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS));
//					}
//					else
//					{
//						others = initPart.getOthersExternal(StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS),methStaticVars);
//					}
//					for (Variable var2: others)
//					{
//						//testTargetPart = targetPart.merge(mergingVar, var2);
//						targetPart.mergeLocal(mergingVar, var2);
//						//assert(testTargetPart.equals(targetPart));
//					}
//				}
//				else
//				{
//					//TODO: not sure about the remove...
//					//testTargetPart = testTargetPart.remove(v);
//					targetPart.removeLocal(v);
//					//assert(testTargetPart.equals(targetPart));
//					targetPart.mergeLocal(mergingVar, v);								
//					//testTargetPart = testTargetPart.merge(mergingVar, v);
//					//assert(testTargetPart.equals(targetPart));
//				}
//			}
//		}		
//		//return targetPart;
//	}
//	
	public VariablesPartition updateLocalVariable(VariablesPartition initPart, VariablesPartition summaryPart, VariablesPartition targetPart,
			Variable mergingVar, Variable baseVar, TIntHashSet visitedStatic, TIntHashSet visitedLocal)
	{		
		Set<Variable> localSet = summaryPart.getSet(baseVar);
		
		if(localSet == null)
		{
			localSet = VariablesPartition.getBasicSet(baseVar.index, baseVar.isStatic);
		}

		for (Variable v : localSet) 
		{		
			if (v.isStatic) {
				if (!visitedStatic.add(v.index)) continue;
			} else {
				if (!visitedLocal.add(v.index)) continue;
			}
			
			if (!v.isOld())
			{
				if (!localSet.contains(StaticVariable.getNewStaticVariable(v.index + VariablesPartition.MAX_VARS))){
					targetPart = targetPart.remove(v);
				}
				targetPart = targetPart.merge(v, mergingVar);
			}
			else
			{	
				Variable newVar = v.isStatic ? StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS)
					: LocalVariable.getNewLocalVariable(v.index - VariablesPartition.MAX_VARS);
				Set<Variable> vOthers = initPart.getOthersLocal(newVar);
				for (Variable vOther : vOthers)
				{
					targetPart = targetPart.merge(vOther, mergingVar);
				}
			}			
		}
		return targetPart;
	}
	
//	public VariablesPartition updateLocalVariableLocal(VariablesPartition initPart, VariablesPartition summaryPart, VariablesPartition targetPart,
//			Variable mergingVar, Variable baseVar, TIntHashSet visitedStatic, TIntHashSet visitedLocal)
//	{		
//		Set<Variable> localSet = summaryPart.getSet(baseVar);
//		
//		if(localSet == null)
//		{
//			localSet = VariablesPartition.getBasicSet(baseVar.index, baseVar.isStatic);
//		}
//
//		for (Variable v : localSet) 
//		{		
//			if (v.isStatic) {
//				if (!visitedStatic.add(v.index)) continue;
//			} else {
//				if (!visitedLocal.add(v.index)) continue;
//			}
//			
//			if (!v.isOld())
//			{
//				targetPart = targetPart.remove(v);
//				targetPart = targetPart.merge(v, mergingVar);
//			}
//			else
//			{	
//				Variable newVar = v.isStatic ? StaticVariable.getNewStaticVariable(v.index - VariablesPartition.MAX_VARS)
//					: LocalVariable.getNewLocalVariable(v.index - VariablesPartition.MAX_VARS);
//				Set<Variable> vOthers = initPart.getOthersLocal(newVar);
//				for (Variable vOther : vOthers)
//				{
//					targetPart = targetPart.merge(vOther, mergingVar);
//				}
//			}			
//		}
//		return targetPart;
//	}
//
	public void setMethodToStaticVars(
			HashMap<jq_Method, Set<Integer>> methToStaticVars) {
		this.methToStaticVars = methToStaticVars;
		
	}
	
	static public void exhaustiveGC() {
		long free1 = 0;
		long free2 = Runtime.getRuntime().freeMemory();
		while (free1 < free2) {
			free1 = free2;
			System.gc();
			free2 = Runtime.getRuntime().freeMemory();
		}
	}
	
	public static void serializeRanges(String fileName,
			HashMap<Integer, Integer> intToOccurrTimesTD, HashMap<Integer, Integer> intToOccurrTimesBU ) {
		
		PrintWriter resultsOut  = OutDirUtils.newPrintWriter(fileName);
		int count = 0;
		int i,k;
		int max;
		for (i=0; count < intToOccurrTimesTD.keySet().size(); i++)
		{
			if (intToOccurrTimesTD.get(i) != null)
			{
				count ++;
			}
		}
		count = 0;
		for (k=0; count < intToOccurrTimesBU.keySet().size(); k++)
		{
			if (intToOccurrTimesBU.get(k) != null)
			{
				count ++;
			}
		}
		max = i > k ? i : k;
		resultsOut.print("Analysis Name,");
		for (int j=0; j<max; j++)
		{
			resultsOut.print(j+",");
		}
		//resultsOut.print("11-"+max);
		resultsOut.println();
		resultsOut.print("TOPDOWN,");
		for (int j=0; j<max; j++)
		{
			if (intToOccurrTimesTD.get(j) != null)
				resultsOut.print(intToOccurrTimesTD.get(j)+",");
			else
				resultsOut.print(0+",");
		}
//		int countTD = 0;
//		for (int j=11; j<max; j++)
//		{
//			if (intToOccurrTimesTD.get(j) != null)
//				countTD += intToOccurrTimesTD.get(j);
//		}
//		resultsOut.print(countTD);
		resultsOut.println();
		resultsOut.print("BOTTOMUP,");
		for (int j=0; j<max; j++)
		{
			if(intToOccurrTimesBU.get(j) != null)
				resultsOut.print(intToOccurrTimesBU.get(j)+",");
			else
				resultsOut.print(0+",");
		}
//		int countBU = 0;
//		for (int j=11; j<max; j++)
//		{
//			if (intToOccurrTimesBU.get(j) != null)
//				countBU += intToOccurrTimesBU.get(j);
//		}
//		resultsOut.print(countBU);
		resultsOut.close();
	}
	
	public static void serializeRangesSingle(String fileName,
			HashMap<Integer, Integer> intToOccurrTimes)
	{	
		PrintWriter resultsOut  = OutDirUtils.newPrintWriter(fileName);
		int count = 0;
		int i;
		for (i=0; count < intToOccurrTimes.keySet().size(); i++)
		{
			if (intToOccurrTimes.get(i) != null)
			{
				count ++;
			}
		}
		
		for (int j=0; j<i; j++)
		{
			if (intToOccurrTimes.get(j) != null)
				resultsOut.println(j +"," +intToOccurrTimes.get(j));
			else
				resultsOut.println(j+","+0);
		}
		resultsOut.close();
	}
}
	
