package chord.analyses.prunerefine;

//Current status
public class Status {
	public static int maxRunAbsSize = -1;
	public static int lastRunAbsSize = -1;
	public static long lastClientTime;
	public static long lastRelevantTime;
	int numUnproven;
	int absSize;
	int runAbsSize;
	int absHashCode;
	long clientTime;
	long relevantTime;
	String absSummary;
}
