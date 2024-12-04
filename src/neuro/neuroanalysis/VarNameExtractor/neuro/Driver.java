import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;

public class Driver {
    
    private static String consNames(String name, String line){
        String ret = "";
        name = name.substring(1, name.length()-1);
        String names1[] = name.split(",");
        for(String n : names1){
            n = n.trim();
           if(!n.matches("R[0-9]+")){
                if(name.startsWith("this:"))
                    ret+=(VarNameProcessor.processThis(name)+",");
                else{
                    ret+= (VarNameProcessor.processVar(name)+",");
                }
            }
        }
        VarNameExtractor vne = new VarNameExtractor();
        List<String> names = vne.extractVarName(line);
        for(String name1 : names)
            ret += (name1+",");
        return ret;
    }

    public static void main(String args[]) throws Exception{
        String varNameFile = args[0];
        String outFile = args[1];
        Scanner sc = new Scanner(new File(varNameFile));
        PrintWriter pw = new PrintWriter(new File(outFile));
        while(sc.hasNext()){
            String aliasLine = sc.nextLine();
            System.out.println(aliasLine);
            String name1 = sc.nextLine();
            String name2 = sc.nextLine();
            String nameLine1 = sc.nextLine();
            String nameLine2 = sc.nextLine();
            String names1 = consNames(name1, nameLine1);
            String names2 = consNames(name2, nameLine2);
            if(!aliasLine.equals("") && !names1.equals("") && !names2.equals("")){
                pw.println(aliasLine);
                pw.println(names1);
                pw.println(names2);
            }
            sc.nextLine();
        }

        sc.close();
        pw.flush();
        pw.close();
    }

}
