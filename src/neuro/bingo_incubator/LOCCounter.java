import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Scanner;

public class LOCCounter {
	private static String libPrefix = "/home/rmangal/";
	private static String[] inputs = { libPrefix + "tsp_files" };
	private static String[] libPaths = { "/home/rmangal/jdk1.6.0_26/"};
	private static String appPrefix = "/home/rmangal/pjbench-read-only/";
	private static String[] appPaths = {
			appPrefix + "tsp/src"
			};
	private static long app = 0;
	private static long lib = 0;
	private static int miss = 0;
	private static long totalApp = 0;
	private static long totalLib = 0;

	/**
	 * @param args
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException {
		for (String input : inputs) {
			System.out.println("=============Processing: " + input+"=============");
			app = 0;
			lib = 0;
			miss = 0;
			Scanner sc = new Scanner(new File(input));
			while (sc.hasNext()) {
				String file = sc.nextLine().trim();
				if (!file.equals("")) {
					String files[] = file.split(" ");
					String path = files[1];
					fileCount(path);
				}
			}
			System.out.println("App: "+app);
			System.out.println("Lib: "+lib);
			System.out.println("Total: "+(app+lib));
			System.out.println("Miss: "+miss);
			totalApp += app;
			totalLib += lib;
		}
		System.out.println("==================TOTAL=================");
		System.out.println("Total App: "+totalApp);
		System.out.println("Total Lib: "+totalLib);
		System.out.println("Total: "+(totalApp+totalLib));
	}

	private static long fileCount(String path) {
		long result;
		for (String libdir : libPaths) {
			LineNumberReader lnr;
			try {
				lnr = new LineNumberReader(new FileReader(new File(libdir
						+ File.separator + path)));
				lnr.skip(Long.MAX_VALUE);
				result = lnr.getLineNumber();
				lnr.close();
			} catch (Exception e) {
				continue;
			}
			lib += result;
			return result;
		}
		for (String appdir : appPaths) {
//			System.out.println(appdir
//					+ File.separator + path);
			LineNumberReader lnr;
			try {
				lnr = new LineNumberReader(new FileReader(new File(appdir
						+ File.separator + path)));
				lnr.skip(Long.MAX_VALUE);
				result = lnr.getLineNumber();
				lnr.close();
			} catch (Exception e) {
				continue;
			}
			app += result;
			return result;
		}
		System.out.println(path);
		miss++;
		return -1;
//		throw new RuntimeException("Can't find " + path
//				+ " .Check the source path configuration!");
	}
}

