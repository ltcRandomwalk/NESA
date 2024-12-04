package chord.analyses.derivsz;

import java.util.HashSet;
import java.util.Random;

import gnu.trove.set.hash.TIntHashSet;


public class TupleStats {	
	static Random rand = new Random(System.currentTimeMillis());
	
	HashSet<TIntHashSet> frntSetList;
	HashSet<TIntHashSet> currDisjunctFrntSetList;
	
	public TupleStats() {
		frntSetList = new HashSet<TIntHashSet>();
		currDisjunctFrntSetList = new HashSet<TIntHashSet>();
	}

	public void updateCountersCurrDisjunct(TupleStats other) {
		if (currDisjunctFrntSetList.isEmpty())
			currDisjunctFrntSetList.addAll(other.frntSetList);
		else {
			HashSet<TIntHashSet> tempList = new HashSet<TIntHashSet>();
			for (TIntHashSet fSet : currDisjunctFrntSetList) {
				for (TIntHashSet otherFSet : other.frntSetList) {
					TIntHashSet newSet = new TIntHashSet();
					newSet.addAll(fSet);
					newSet.addAll(otherFSet);
					tempList.add(newSet);
				}
			}
			currDisjunctFrntSetList.clear();
			currDisjunctFrntSetList.addAll(tempList);
			tempList.clear();
		}
	}
	
	public static int randInt(int min, int max) {
	    // nextInt is normally exclusive of the top value,
	    // so add 1 to make it inclusive
	    int randomNum = rand.nextInt((max - min) + 1) + min;
	    //System.out.println("rand: min, max,rand:" + min + "  " + max + "  " + randomNum);
	    return randomNum;
	}
}
