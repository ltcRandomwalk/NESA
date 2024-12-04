package chord.analyses.experiment.solver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Scanner;

import chord.util.ProfileUtil;

import static chord.util.CollectionUtil.*;
import static chord.util.ExceptionUtil.fail;

/** Read instantiated rules from the result of a Datalog analysis.
 */
public class ResultLoader {
  public static List<LookUpRule> getRules(String configFiles[]) {
    ProfileUtil.start("MAIN", "ResultLoader.getRules");
    // System.out.printf("DBG: load instantiated rules\n");
    List<LookUpRule> rules = newArrayList();

    for (String conFile : configFiles) {
      try {
        Scanner sc = new Scanner(new File(conFile));
        while (sc.hasNext()) {
          String line = sc.nextLine().trim();
          if (!line.equals("")) {
            LookUpRule rule = LookUpRule.make(line);
            rules.add(rule);
          }
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    for (LookUpRule r : rules) r.update();

    ProfileUtil.stop("MAIN", "ResultLoader.getRules");
    return rules;
  }
}
