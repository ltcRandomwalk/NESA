package chord.analyses.experiment.solver;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import chord.project.Config;
import chord.util.ProfileUtil;

import static chord.util.ExceptionUtil.fail;
import static chord.util.SystemUtil.moveFile;
import static chord.util.SystemUtil.path;

public class Mifumax extends WcnfSolver {
  @Override public List<Integer> solve(
      List<FormattedConstraint> clauses,
      String problemName
  ) {
    try {
      File qf = new File(path(Config.outDirName, "refine.wcnf"));
      PrintWriter qw = new PrintWriter(qf);
      saveWcnf(clauses, qw);
      qw.flush(); qw.close();

      System.out.println("MIFU: MiFuMax constraints saved");
      // Run MiFuMax.
      System.out.println("MIFU: MiFuMax started");
      long startTime = System.nanoTime();
      long timeout = Integer.getInteger("chord.experiment.solver.timeout", 60);
      timeout *= 1000l;

      ProcessBuilder pb = new ProcessBuilder(mifuPath, qf.getAbsolutePath());
      pb.redirectErrorStream(true);
      final Process p = pb.start();
      final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
      File mifumaxOutput = new File(path(Config.outDirName, "mifumax.out"));
      final PrintWriter rpw = new PrintWriter(mifumaxOutput);

      Timer t = new Timer();
      t.schedule(new TimerTask() {
          @Override
          public void run() {
              p.destroy();
          }
      }, timeout);   // it will kill the process after timeout seconds (if it's not finished yet).

      Thread th = new Thread() {
    	  public void run() {
    		  try {
    			  String line;
    			  while ((line = in.readLine()) != null) {
    				  rpw.println(line);
    			  }
    			  in.close();
    		      rpw.flush(); rpw.close();
    		  } catch (Exception e) {
    			  fail("Error in writing Mifumax output to file");
    		  }
    	  }
      };
      th.start();

      if (p.waitFor() != 0) fail("MiFuMax returned non-zero.");
      t.cancel();

      th.join();
      if(p != null){
    	  if(p.getOutputStream() != null){
    		  p.getOutputStream().close();
    	  }
    	  if(p.getErrorStream() != null){
    		  p.getErrorStream().close();
    	  }
    	  if(p.getInputStream() != null){
    		  p.getInputStream().close();
    	  }
    	  p.destroy();
      }

      long stopTime = System.nanoTime();
      System.out.printf("MIFU: MiFuMax done in %.01f seconds%n",
    		  (1e-9)*(stopTime-startTime));

      List<Integer> assignment = parseMiFuMax(mifumaxOutput);

      if (debug()) saveMifumaxFiles(problemName);
      return assignment;
    } catch (Exception e) { fail(e); return null; }
  }

  private void saveMifumaxFiles(String problemName) {
    moveFile("refine.wcnf", String.format("refine_%s.wcnf", problemName));
    moveFile("mifumax.out", String.format("mifumax_%s.out", problemName));
  }

  private List<Integer> parseMiFuMax(File f) throws IOException {
      ProfileUtil.start("MAIN", "parseMiFuMax");
      try {
        return parseMiFuMaxUntimed(f);
      } finally {
        ProfileUtil.stop("MAIN", "parseMiFuMax");
      }
  }

  private List<Integer> parseMiFuMaxUntimed(File f) throws IOException {
      List<Integer> result = Lists.newArrayList();
      Scanner sc = new Scanner(f);
      while (sc.hasNext()) {
          String line = sc.nextLine();
          if (line.startsWith("s ")) {
              if (line.startsWith("s UNSATISFIABLE"))
                  return null;
              if (!line.startsWith("s OPTIMUM FOUND"))
                  throw new RuntimeException("Expecting a solution but got " + line);
          }
          if (line.startsWith("v ")) {
              Scanner lineSc = new Scanner(line);
              String c = lineSc.next();
              if (!c.equals("v"))
                  throw new RuntimeException("Expected char of a solution line: " + c);
              while (lineSc.hasNext()) result.add(lineSc.nextInt());
          }
      }
      return result;
  }

  public Mifumax() {
    String mifumax = System.getProperty("chord.experiment.solver.mifumax", "mifumax");
    //TODO read mifumax from the jar file
    mifuPath = path(System.getenv("CHORD_MAIN"), "src","chord","project","analyses","provenance",mifumax);
    mifuPath = System.getProperty("chord.experiment.solver.mifupath", mifuPath);
  }

  private String mifuPath;

  static boolean debug() {
      return Boolean.getBoolean("chord.experiment.solver.debug");
  }
}
