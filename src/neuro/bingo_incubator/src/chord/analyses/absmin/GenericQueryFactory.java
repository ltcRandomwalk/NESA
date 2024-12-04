package chord.analyses.absmin;

import chord.project.analyses.ProgramDom;
import chord.util.Utils;

public class GenericQueryFactory implements QueryFactory {
	private final ProgramDom[] doms;
	public GenericQueryFactory(ProgramDom[] doms) {
		this.doms = doms;
	}

	public String encode(Object[] vals) {
		int n = doms.length;
		assert (vals.length == n);
        String enc = new String("");
        for (int i = 0; i < n; i++) {
            enc += doms[i].indexOf(vals[i]);
            if (i < n - 1) enc += ",";
        }
		return enc;
	}

	public String encode(int[] idxs) {
        int n = doms.length;
		assert (idxs.length == n);
        String enc = new String("");
        for (int i = 0; i < n; i++) {
            enc += idxs[i];
            if (i < n - 1) enc += ",";
        }
		return enc;
	}

	@Override
	public GenericQuery create(String enc) {
		return new GenericQuery(enc);
	}

	public class GenericQuery extends Query {
		private final String enc;

		public GenericQuery(String enc) { this.enc = enc; }

		@Override
		public String encode() { return enc; }

		@Override 
		public String encodeForXML() {
			int n = doms.length;
			String[] idxs = Utils.split(enc, ",", false, false, 0);
			String encXML = new String("");
			for (int i = 0; i < n; i++) {
				encXML += doms[i].getName() + "id=\"" + doms[i].getName() + idxs[i] + "\"";
				if (i < n - 1) encXML += " ";
			}
			return encXML;
		}

		@Override
		public int compareTo(Query q) {
			return this.enc.compareTo(q.encode());
		}

		@Override
		public String toString() {
			int n = doms.length;
			String[] idxs = Utils.split(enc, ",", false, false, 0);
			String s = "";
			for (int i = 0; i < n; i++) {
				if (s.length() > 0) s += ", ";
				Object o = doms[i].get(Integer.parseInt(idxs[i]));
				s += doms[i].toUniqueString(o);
           	}
       		return s;
       	}

		@Override
		public boolean equals(Object o) {
			return this.enc.equals(((GenericQuery) o).enc);
		}

		@Override
		public int hashCode() { return enc.hashCode(); }
	};
}

