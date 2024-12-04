public class VarNameProcessor {
    public static String processThis(String thisLine){
        if(!thisLine.startsWith("this:"))
            throw new RuntimeException("Not a this line: "+thisLine);
        String segs[] = thisLine.split("/");
        String className = segs[segs.length-1];
        if(className.endsWith(";"))
            return className.substring(0, className.length()-1);
        else
            return className;
    }

    public static String processVar(String varLine){
        if(varLine.startsWith("this:") || !varLine.contains(":"))
            throw new RuntimeException("Not a var line: "+varLine);
        String segs[] = varLine.split(":");
        return segs[0];
    }
}
