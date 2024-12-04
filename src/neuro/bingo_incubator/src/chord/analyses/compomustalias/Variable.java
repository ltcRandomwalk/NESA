package chord.analyses.compomustalias;

import java.util.ArrayList;

import gnu.trove.map.hash.TObjectIntHashMap;
import joeq.Class.jq_Field;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.field.DomF;
import chord.analyses.var.DomV;

public class Variable {
public static DomV domV;
public static DomF domF;
public static TObjectIntHashMap<String> strToVNdx;
public static TObjectIntHashMap<String> strToFNdx;
public static ArrayList<String> vNdxToStrTrainApp;
public static ArrayList<String> fNdxToStrTrainApp;

public boolean badValue;
private Register local;
private jq_Field global;
private boolean isRet = false;

	public Variable(Register r){
		if (domV.indexOf(r) == -1) {
			//System.out.println("Bad reg for Variable: " + domV.toUniqueString(r));
			//for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			    //System.out.println(ste);
			//}
		}
		this.local = r;
	}

	public Variable(Register r, boolean isRet){
		this(r);
		this.isRet = isRet;
	}
	
	public Variable(jq_Field g){
		this.global = g;
	}
	
	public Variable(String s) {
		if (!s.equals("")) {
			String[] varParts;
			varParts = s.split("ISRET:");
			if (varParts.length == 2) {
				int idx = Integer.parseInt(varParts[0]);
				String ls = null;
				if (idx >= 0 && idx < vNdxToStrTrainApp.size())
					ls = vNdxToStrTrainApp.get(idx);
				if (ls != null && strToVNdx.containsKey(ls))
					this.local = domV.get(strToVNdx.get(ls));
				else {
					badValue = true;
					//System.out.println("var: badValue in local" + idx + "   " + ls);
				}
				
				if (varParts[1].equals("0")) 
					this.isRet = false;
				else if (varParts[1].equals("1"))
					this.isRet = true;
				else {
					badValue = true;
				}
			} else if (varParts.length == 1) {
				int idx = Integer.parseInt(varParts[0]);
				String gs = null;
				if (idx >= 0 && idx < fNdxToStrTrainApp.size())
					gs = fNdxToStrTrainApp.get(idx);
				if (gs != null && strToFNdx.containsKey(gs))
					this.global = domF.get(strToFNdx.get(gs));
				else
					badValue = true;
				this.isRet = false;
			} else {
				badValue = true;
			}
		}
	}
	
	public boolean isGlobal(){
		return global != null;
	}
	
	public boolean isLocal(){
		return local != null;
	}
	
	public Register getLocal(){
		return local;
	}
	
	public jq_Field getGlobal(){
		return global;
	}
	
	public boolean isRet() {
		return isRet;
	}

	public void setRet(boolean isRet) {
		this.isRet = isRet;
	}

	public Variable lift(){
		if(isRet == true)
			throw new RuntimeException("Variable has been lifted: "+this);
		if(this.isGlobal())
			throw new RuntimeException("Cannot lift a global variable "+this);
		Variable ret = new Variable(this.local,true);
		return ret;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((global == null) ? 0 : global.hashCode());
		result = prime * result + (isRet ? 1231 : 1237);
		result = prime * result + ((local == null) ? 0 : local.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Variable other = (Variable) obj;
		if (global == null) {
			if (other.global != null)
				return false;
		} else if (!global.equals(other.global))
			return false;
		if (isRet != other.isRet)
			return false;
		if (local == null) {
			if (other.local != null)
				return false;
		} else if (!local.equals(other.local))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Variable [local=" + local +"("+(local==null?0:local.hashCode())+")"+ ", global=" + global + ", isRet=" + isRet + "]";
	}

	public String toParsableString() {
		StringBuilder ret = new StringBuilder();
		if (local != null) {
			//ret += domV.toUniqueString(local);
			//if (domV.indexOf(local) == -1) System.out.println ("bad Ndx: " + domV.toUniqueString(local));
			ret.append(domV.indexOf(local));
			int isRetReg;
			if (isRet) isRetReg = 1; else isRetReg = 0;
			//ret += "ISRET:" + isRetReg;
			ret.append("ISRET:" + isRetReg);
		} else if (global != null){
			//ret += domF.toUniqueString(global);
			ret.append(domF.indexOf(global));
		}
		return ret.toString();
	}
	
	public boolean matches(AccessPath ap){
		if(ap instanceof RegisterAccessPath && this.isLocal()){
			RegisterAccessPath rap = (RegisterAccessPath)ap;
			if(rap.var.equals(this.getLocal()))
				return true;
		}
		if(ap instanceof GlobalAccessPath && this.isGlobal()){
			GlobalAccessPath gap = (GlobalAccessPath)ap;
			if(gap.global.equals(this.getGlobal()))
				return true;
		}
		return false;
	}
	
	public AccessPath replacePrefix(AccessPath ap){
		if(this.isGlobal())
			return new GlobalAccessPath(global,ap.fields);
		else{
			RegisterAccessPath ret = new RegisterAccessPath(local,ap.fields);
			if(this.isRet)
				ret.isRet=true;
			return ret;
			}
	}

	public AccessPath createAccessPath(){
		if(this.isGlobal())
			return new GlobalAccessPath(global);
		else
			return new RegisterAccessPath(local);
	}
}
