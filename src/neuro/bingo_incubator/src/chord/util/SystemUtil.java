package chord.util;

import java.io.File;

import chord.project.Config;

public final class SystemUtil {
	static public String path(String ... xs) {
		boolean first = true;
		StringBuilder b = new StringBuilder();
		for (String x : xs) {
			if (!first) b.append(File.separator); else first = false;
			b.append(x);
		}
		return b.toString();
	}

  public static void moveFile(String from, String to) {
    String d = Config.outDirName;
    if (!new File(path(d,from)).renameTo(new File(path(d,to))))
      System.out.printf("WARNING: can't move %s to %s\n", from, to);
  }

  private SystemUtil() { /* no instantiation */ }
}
