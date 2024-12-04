package chord.analyses.superopt;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import chord.project.Config;
import chord.project.OutDirUtils;
import chord.util.ProcessExecutor;
import chord.util.Utils;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.RegisterFactory.Register;


public class EquivCheck {

	private static HashMap<Register, HashMap<String, ArrayList<Object[]>>> unoptAssigns;         
	private static HashMap<Register, HashMap<String, ArrayList<Object[]>>> optAssigns; 
	private static Register voidReg = null;
	private static String smtFileName;
	private static PrintWriter smtPW;
	
	
	public static boolean chkEquiv (HashMap<String, ArrayList<Object[]>> unoptMethBody, 
			                        HashMap<String, ArrayList<Object[]>> optMethBody, String methId) {
		if (unoptMethBody.equals(optMethBody)) return true;
		unoptAssigns = findAssigns(unoptMethBody);
		optAssigns = findAssigns(optMethBody);		
		smtFileName = methId + ".smt";
		smtPW = OutDirUtils.newPrintWriter(smtFileName);
        encodeCommonStart();
		encode(unoptAssigns, "_a");
		encode(optAssigns, "_b");
		encodeIeqOeq ();
		encodeCommonEnd();
		smtPW.close();
		boolean equiv = execCmd(smtFileName);
		return equiv;
	}
	
	private static HashMap<Register, HashMap<String, ArrayList<Object[]>>> 
	findAssigns (HashMap<String, ArrayList<Object[]>> methBody) {
		
		HashMap<Register, HashMap<String, ArrayList<Object[]>>> assigns = 
				new HashMap<Register, HashMap<String, ArrayList<Object[]>>>();
		
		for (String stmtName : methBody.keySet()) {
			ArrayList<Object[]> stmts = methBody.get(stmtName);
			for (Object[] stmt : stmts) {
				Register lhs = getLhs (stmt, stmtName);
				HashMap<String, ArrayList<Object[]>> varAssigns;
				if (assigns.containsKey(lhs))
					varAssigns = assigns.get(lhs);
				else
					varAssigns = new HashMap<String, ArrayList<Object[]>>();
				ArrayList<Object[]> lstmts;
				if (varAssigns.containsKey(stmtName))
					lstmts = varAssigns.get(stmtName);
				else
					lstmts = new ArrayList<Object[]>();
				lstmts.add(stmt);
			}
		}
		return assigns;
	}
	
	private static Register getLhs (Object[] stmt, String stmtName) {
		Register retval;
		if (stmtName.equals("MobjVarAsgnInst")) {
			retval = (Register) stmt[1];
		} else if (stmtName.equals("MobjValAsgnInst")) {
			retval = (Register) stmt[1];
		} else if (stmtName.equals("MgetInstFldInst")) {
			retval = (Register) stmt[1];
		} else if (stmtName.equals("MgetStatFldInst")) {
			retval = (Register) stmt[1];
		} else if (stmtName.equals("MI")) {
			Quad q = (Quad) stmt[1];
			RegisterOperand vo = Invoke.getDest(q);
			if (vo != null)
				retval = vo.getRegister();
			else
				retval = voidReg;
		} else retval = null;
		return retval;
	}
	
	
	private static void encodeCommonStart () {
		smtPW.println("(define-sort Elt () Int)");
		smtPW.println("(define-sort Set () (Array Elt Bool))");
		smtPW.println("(define-fun smt_set_emp () Set ((as const Set) false))");
		smtPW.println("(define-fun smt_set_mem ((x Elt) (s Set)) Bool (select s x))");
		smtPW.println("(define-fun smt_set_add ((s Set) (x Elt)) Set  (store s x true))");
		smtPW.println("(define-fun smt_set_cup ((s1 Set) (s2 Set)) Set ((_ map or) s1 s2))");
		
		smtPW.println("");
		smtPW.println("(declare-fun pts (Set) Set)");
		smtPW.println("(assert (forall ((u Set) (v Set)) (= (smt_set_cup (pts u) (pts v)) (pts (smt_set_cup u v)))))");
		
		smtPW.println("");
		smtPW.println("(define-fun test () Bool (=> (and ieq struct_cons) oeq))");
		smtPW.println("(assert (not test))");
		
	}
	
	
	private static void encodeCommonEnd () {
		smtPW.println("(define-fun struct_cons () Bool (and struct_cons_a struct_cons_b))");
		smtPW.println("(check-sat)");
		smtPW.println("(exit)");
	}
	
	
	private static void encodeIeqOeq () {
		
	}
	
	
	private static void encode (HashMap<Register, HashMap<String, ArrayList<Object[]>>> assigns, String suffix) {
		HashSet<String> z3SetDecls = new HashSet<String>();
		HashSet<String> z3VarDecls = new HashSet<String>();
		HashSet<String> z3Assertions = new HashSet<String>();
		String constSetPrefix = "cst";
		String constVarPrefix = "cstv";
		int constCounter = 0;
		for (Register lhs : assigns.keySet()) {
			StringBuilder sb = new StringBuilder();
			HashMap<String, ArrayList<Object[]>> varAssigns = assigns.get(lhs);
			if (varAssigns == null) continue;
			boolean firstTime = true;
			for (String stmtName : varAssigns.keySet()) {
				ArrayList<Object[]> stmts = varAssigns.get(stmtName);
				if (stmts == null) continue;
				if (stmtName.equals("MobjVarAsgnInst")) {
					for (Object[] stmt : stmts) {
						Register rhs = (Register) stmt[2];
						String rhsZ3Name = rhs.toString() + suffix;
						if (!firstTime)
							sb.append("(smt_set_cup (" + sb.toString() + ") " + "pts(" + rhsZ3Name + ")");
						else
							sb.append("pts(" + rhsZ3Name + ")");
						firstTime = false;
						z3SetDecls.add("(declare-const " + rhsZ3Name + " Set)");
					}
				} else if (stmtName.equals("MobjValAsgnInst")) {
					String newConstSet = constSetPrefix + constCounter + suffix;
					String newConstVar = constVarPrefix + constCounter + suffix;
					z3SetDecls.add("(declare-const " + newConstSet + " Set)");
					z3VarDecls.add("(declare-const " + newConstVar + " Elt)");
					constCounter++;
					z3Assertions.add("(assert (= " + newConstSet + " (smt_set_add smt_set_emp " + newConstVar + ")))");
					if (!firstTime)
						sb.append("(smt_set_cup (" + sb.toString() + ") " + "pts(" + newConstSet + ")");
					else
						sb.append("pts(" + newConstSet + ")");
					firstTime = false;
					
				} else if (stmtName.equals("MgetInstFldInst")) {
					
				} else if (stmtName.equals("MgetStatFldInst")) {
					
				} else if (stmtName.equals("MputInstFldInst")) {
					
				} else if (stmtName.equals("MputStatFldInst")) {
					
				} else if (stmtName.equals("MI")) {
					
				} else {
					
				}
			}				
		}
	}
	
	private static boolean execCmd(String fname) {
		File resFile;
		String resName = Config.workDirName + File.separator + basename(Config.outDirName) + File.separator + "res";
		resFile = new File(resName);
		String[] cmdArr = new String[3];
		cmdArr[0] = "z3";
		cmdArr[1] = "-smt2";
		cmdArr[2] = fname;

		int timeout = getTimeout();
		try {
			System.out.println ("Executing z3 -smt2 on " + fname);
			ProcessExecutor.executeWithRedirect(cmdArr, resFile, timeout);
		} catch(Throwable t) { //just log exceptions
			t.printStackTrace();
		}
		
    	boolean retval = false;
    	if (resFile.exists()) {
			List<String> l = Utils.readFileToList(resName);
			System.out.println("Z3 result: " + l.get(0));
			if (l.get(0).trim().equals("unsat"))
				retval = true;
			else
				retval = false;
			resFile.delete();
    	}
		return retval;
	}
	
	private static int getTimeout() {
		return 0;
	}
	
	private static String basename(String fname) {
		String[] parts = fname.split(File.separator);
		return parts[parts.length - 1];
	}

}
