package chord.project.analyses.composat;

public class SatConfig {
	static int problemId = 0;
	static int constrId = 0;
	static int varDomSize = 0;
	static int SOLVER_MIFUMAX = 1;
	static int solverToUse = SOLVER_MIFUMAX;
	static boolean DEBUG = true;
	public static boolean VIS_PROBLEM = false;
	/**
	 * Threshold of ILP memory consumption to start node file, in gigabytes
	 */
	public static double ilpMemory = 50;
	public static boolean SAVE_PROBLEM = true;
}
