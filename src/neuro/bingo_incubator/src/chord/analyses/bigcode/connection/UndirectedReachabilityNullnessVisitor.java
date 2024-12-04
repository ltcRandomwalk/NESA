package chord.analyses.bigcode.connection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import chord.util.ArraySet;
import joeq.Class.jq_Field;
import joeq.Class.jq_Type;
import joeq.Compiler.BytecodeAnalysis.Bytecodes.ACONST_NULL;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.ConstOperand;
import joeq.Compiler.Quad.Operand.IConstOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Quad; 
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.QuadVisitor.EmptyVisitor;
import joeq.Compiler.Quad.Operator.Return.THROW_A;

public class UndirectedReachabilityNullnessVisitor extends EmptyVisitor 
{
	private static final VariablesPartitionNullness emptyRetPart = VariablesPartitionNullness.createDinit(null, null);
 
	public DstNodeNullness iDstNode;
	public DstNodeNullness oDstNode;
	private HashMap<Quad, Set<VariablesPartitionNullness>> results;
	private Utils utils;
	
	
	public UndirectedReachabilityNullnessVisitor(Utils utils) 
	{
		this.utils = utils;
	}

	@Override
	public void visitReturn(Quad q) {
		
		int retIdx = -1;
		Set<Integer> retIdxs = new ArraySet<Integer>(1);
		if (!(q.getOperator() instanceof THROW_A)) 
		{ 
			 Operand rx = Return.getSrc(q); 
			 if (rx instanceof RegisterOperand) 
			 {
				 RegisterOperand ro = (RegisterOperand) rx; 
				 if	 (ro.getType().isReferenceType()) 
				 { 
					 retIdx = utils.getAbsIdx(ro);
				 }
			 }
			 else if (rx instanceof ConstOperand)
			 {
				 if (rx.toString().contains("null"))
				 {
					 retIdx = VariablesPartitionNullness.NULL_INT;
				 }
			 }
			 else 
			 {
				 if (rx == null)
				 {
					 retIdx = VariablesPartitionNullness.NULL_INT;
				 }
				 else
				 {
				 	System.out.println(q);
				 	System.out.println(rx);
					System.out.println(rx.getClass());
					assert(false);
				 }	
			 }			 
		 }
		 
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		addToResults(q,ipart);
		
		if (retIdx != -1)
		{
			retIdxs.add(retIdx);
		}
		oDstNode = new DstNodeNullness(opart,  true, retIdxs);
	}

	
	@Override
	public void visitCheckCast(Quad q)
	{
		visitMove(q);
	}
	
	@Override
	public void visitNew(Quad q)
	{
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;

		if (ipart != emptyRetPart)
		{
			RegisterOperand lo = New.getDest(q);
			jq_Type t = lo.getType();
			if (!t.isReferenceType())
				return;
	
			int lIdx = utils.getAbsIdx(lo);
			opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
		}
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}
	
	
	@Override
	public void visitNewArray(Quad q)
	{
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		if (ipart != emptyRetPart)
		{
			RegisterOperand lo = NewArray.getDest(q);
			jq_Type t = lo.getType();
			if (!t.isReferenceType())
				return;
	
			int lIdx = utils.getAbsIdx(lo);
			opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
		}
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}
	
	

	@Override
	public void visitMove(Quad q) {
			
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		if (ipart != emptyRetPart)
		{
			RegisterOperand lo = Move.getDest(q);
			jq_Type t = lo.getType();
			int lIdx = utils.getAbsIdx(lo);
			if (!t.isReferenceType())
				return;
			
			Operand rx = Move.getSrc(q);
			if (rx instanceof RegisterOperand) 
			{
				RegisterOperand ro = (RegisterOperand) rx;
				int rIdx = utils.getAbsIdx(ro);
				
				if (lIdx != rIdx)
				{
					opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
					opart = (VariablesPartitionNullness) opart.merge(LocalVariable.getNewLocalVariable(lIdx), LocalVariable.getNewLocalVariable(rIdx));
				}
			}
			else if (rx instanceof ConstOperand)
			{
				opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
				if (rx.toString().contains("null"))
				{
					opart = (VariablesPartitionNullness) opart.merge(LocalVariable.getNewLocalVariable(lIdx), VariablesPartitionNullness.NULL_VAR);
				}
			}
			else
			{
				System.out.println(rx);
				System.out.println(q);
				//assert(false);
				System.out.println("Connection: UNSUPPORTED");
			}
		}
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}

	@Override
	public void visitALoad(Quad q) 
	{	
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		if (ipart != emptyRetPart)
		{		
			Operator op = q.getOperator();
			if (!((ALoad) op).getType().isReferenceType())
				return;
	
			RegisterOperand bo = (RegisterOperand) ALoad.getBase(q);
			int bIdx = utils.getAbsIdx(bo);
			
			RegisterOperand lo = ALoad.getDest(q);
			int lIdx = utils.getAbsIdx(lo);
			if (bIdx != lIdx)
			{
				opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
				opart = (VariablesPartitionNullness) opart.merge(LocalVariable.getNewLocalVariable(lIdx), LocalVariable.getNewLocalVariable(bIdx));
			}
		}
		
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}

	@Override
	public void visitGetfield(Quad q) {

		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		if (ipart != emptyRetPart)
		{	
			jq_Field f = Getfield.getField(q).getField();
			if (!f.getType().isReferenceType())
				return;
	
			RegisterOperand lo = Getfield.getDest(q);
			int lIdx = utils.getAbsIdx(lo);		
				
			Operand bx = Getfield.getBase(q);
			if (bx instanceof RegisterOperand) 
			{
				RegisterOperand bo = (RegisterOperand) bx;
				int bIdx = utils.getAbsIdx(bo);
				if (bIdx != lIdx)
				{
					opart = (VariablesPartitionNullness) opart.remove(LocalVariable.getNewLocalVariable(lIdx));
					opart = (VariablesPartitionNullness) opart.merge(LocalVariable.getNewLocalVariable(lIdx), LocalVariable.getNewLocalVariable(bIdx));
				}
			}
		}
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}

	@Override
	public void visitAStore(Quad q) 
	{
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;

		if (ipart != emptyRetPart)
		{
			Operator op = q.getOperator();
			if (!((AStore) op).getType().isReferenceType())
				return;
			
			Operand rx = AStore.getValue(q);
			if (!(rx instanceof RegisterOperand))
				return;
			
			RegisterOperand ro = (RegisterOperand) rx;
			RegisterOperand bo = (RegisterOperand) AStore.getBase(q);
			
			int rIdx = utils.getAbsIdx(ro);
			int bIdx = utils.getAbsIdx(bo);
			
			Variable v1 =  LocalVariable.getNewLocalVariable(rIdx);
			Variable v2 =  LocalVariable.getNewLocalVariable(bIdx);
			if (!(opart.isNull(v1) || opart.isNull(v2))){
				opart = (VariablesPartitionNullness) opart.merge(v1, v2);
			}
		}	
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}


	@Override
	public void visitPutfield(Quad q) 
	{
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
			
		if (ipart != emptyRetPart)
		{	
			Operand rx = Putfield.getSrc(q);
			if (!(rx instanceof RegisterOperand))
				return;
			
			Operand bx = Putfield.getBase(q);
			if (!(bx instanceof RegisterOperand))
				return;
				
			RegisterOperand ro = (RegisterOperand) rx;
			jq_Type t = ro.getType();
			if (!t.isReferenceType())
				return;
			int rIdx = utils.getAbsIdx(ro);
			
			RegisterOperand bo = (RegisterOperand) bx;
			int bIdx = utils.getAbsIdx(bo);
		
			Variable v1 =  LocalVariable.getNewLocalVariable(rIdx);
			Variable v2 =  LocalVariable.getNewLocalVariable(bIdx);
			if (!(opart.isNull(v1) || opart.isNull(v2))){
				opart = (VariablesPartitionNullness) opart.merge(v1, v2);
			}
		}	
		oDstNode = new DstNodeNullness(opart,false, null);
		addToResults(q,ipart);
	}


	@Override
	public void visitPutstatic(Quad q)
	{
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		if (ipart != emptyRetPart)
		{
			jq_Field f = Putstatic.getField(q).getField();
			if (!f.getType().isReferenceType())
				return;
		
			Variable v1 = StaticVariable.getNewStaticVariable(utils.getStaticIdx(f));
			
	
			Operand rx = Putstatic.getSrc(q);
			if (rx instanceof RegisterOperand)
			{
				RegisterOperand ro = (RegisterOperand) rx;
				int rIdx = utils.getAbsIdx(ro);
				Variable v2 = LocalVariable.getNewLocalVariable(rIdx);
				opart = (VariablesPartitionNullness) opart.remove(v1);
				opart = (VariablesPartitionNullness) opart.merge(v1, v2);
			}
			else if (rx instanceof ConstOperand) 
			{
				if (utils.getMethToStaticVars().get(q.getMethod()).contains(v1.index)){
					opart = (VariablesPartitionNullness) opart.remove(v1);
					if (rx.toString().contains("null"))
					{
						opart = (VariablesPartitionNullness) opart.merge(v1, VariablesPartitionNullness.NULL_VAR);
					}
				}
				else
				{
					System.out.println("skipped put assignment "+ q);
				}
			}
			else
			{
				System.out.println(q);
				System.out.println(rx);
				assert(false);
			}
		}
		
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}

	@Override
	public void visitGetstatic(Quad q) 
	{	
		VariablesPartitionNullness ipart = iDstNode.partition;
		VariablesPartitionNullness opart = ipart;
		
		if (ipart != emptyRetPart)
		{
			jq_Field f = Getstatic.getField(q).getField();
			if (!f.getType().isReferenceType())
				return;
	
			RegisterOperand lo = Getstatic.getDest(q);
			int lIdx = utils.getAbsIdx(lo);
			Variable v1 = LocalVariable.getNewLocalVariable(lIdx);
			opart = (VariablesPartitionNullness)  opart.remove(v1);
	
			Variable v2 = StaticVariable.getNewStaticVariable(utils.getStaticIdx(f));
			opart = (VariablesPartitionNullness) opart.merge(v1, v2);
		}
		oDstNode = new DstNodeNullness(opart, false, null);
		addToResults(q,ipart);
	}

	private void addToResults(Quad q, VariablesPartition opart) {
		
		//Set<VariablesPartition> quadResults = results.get(q);
		//if (quadResults == null)
		//{
		//	quadResults = new HashSet<VariablesPartition>();
		//	results.put(q, quadResults);
		//}
		//quadResults.add(opart);
		assert(opart != null);
	}


	public HashMap<Quad, Set <VariablesPartitionNullness>> getResults()
	{
		return this.results;
	}
}


