package chord.analyses.libanalysis;

import chord.analyses.absmin.AbstractionFactory;
import chord.analyses.absmin.Abstraction;
import joeq.Class.jq_Method;
import chord.analyses.method.DomM;
import chord.util.Utils;

public class MethodAbstractionFactory implements AbstractionFactory {
	private final DomM domM;
	public MethodAbstractionFactory(DomM domM) {
		this.domM = domM;
	}

    @Override
    public MethodAbstraction create(String enc) {
        return new MethodAbstraction(enc);
    }

	public MethodAbstraction create(jq_Method m) {
		return new MethodAbstraction(m);
	}

	public class MethodAbstraction extends Abstraction {
		private jq_Method abs;
		private String enc;
		public jq_Method getAbs() {
			return abs;
		}

		public String getEnc() {
			return enc;
		}

		public MethodAbstraction(jq_Method m) {
			this.abs = m;
			this.setLevel(0);
			this.setMaxLevel(1);
			this.setMinLevel(0);
			this.mod = 1;
			this.enc = this.encode();
		}

		public MethodAbstraction(String enc) {
			mod = 1;
			this.setMinLevel(0);
			this.setMaxLevel(1);
            String[] parts = Utils.split(enc, ":",false, false, 0);
            if (domM.getName().equals(parts[0])) {
                abs = domM.get(Integer.parseInt(parts[1]));
                this.setLevel(Integer.parseInt(parts[2]));
                this.enc = enc;
                mod = 0;
            }
		}

		public MethodAbstraction(MethodAbstraction a) {
			this.abs = a.abs;
			this.enc = new String(a.enc);
			this.setMinLevel(a.getMinLevel());
			this.setMaxLevel(a.getMaxLevel());
			this.setLevel(a.getLevel());
			this.mod = a.mod;
		}
  
		@Override
		public String encode() {
			if(mod == 1) {
				enc = new String("");
				enc += domM.getName() + ":" + domM.indexOf(abs) + ":" + this.getLevel();
				mod = 0;
			}
			return enc;
		}

		@Override
		public String encodeForXML() {
			String encXML = "";
			encXML += domM.getName() + domM.indexOf(abs);
			return encXML;
		}

		@Override
		public void copy(Abstraction a) {
			if (!(a instanceof MethodAbstraction))
				return;
			MethodAbstraction ma = (MethodAbstraction) a;
			this.abs = ma.abs;
			this.enc = ma.encode();
			this.setMinLevel(ma.getMinLevel());
			this.setMaxLevel(ma.getMaxLevel());
			this.setLevel(ma.getLevel());
			mod = 0;
		}

		@Override
		public Abstraction copy() {
			return new MethodAbstraction(this);
		}

		@Override
		public int hashCode() { return enc.hashCode(); }

		@Override
		public boolean equals(Object q) {
			if (this.encode().equalsIgnoreCase(((MethodAbstraction)q).encode()))
				return true;
			return false;
		}

		@Override
		public String toString() {
			return abs.toString();
		}
  
		@Override
		public int compareTo(Abstraction a) {
			return this.encode().compareTo(a.encode());
		}
	}
}

