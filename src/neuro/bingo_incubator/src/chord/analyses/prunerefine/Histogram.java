package chord.analyses.prunerefine;

public class Histogram {
	int[] counts = new int[1000];
	
	void clear() {
		for (int i = 0; i < counts.length; i++)
			counts[i] = 0;
	}
	
	public int getCountOf(int i){
		if(i < counts.length)
			return counts[i];
		else
			return -1;
	}
	
	public void add(int i) {
		if (i >= counts.length) {
			int[] newCounts = new int[Math.max(counts.length*2,i+1)];
			System.arraycopy(counts, 0, newCounts, 0, counts.length);
			counts = newCounts;
		}
		counts[i]++;
	}
	public void add(Histogram h) {
		for (int i = 0; i < counts.length; i++)
			counts[i] += h.counts[i];
	}
	@Override public String toString() {
		StringBuilder buf = new StringBuilder();
		for (int n = 0; n < counts.length; n++) {
			if (counts[n] == 0) continue;
			if (buf.length() > 0) buf.append(" ");
			buf.append(n+":"+counts[n]);
		}
		return '['+buf.toString()+']';
	}
}
