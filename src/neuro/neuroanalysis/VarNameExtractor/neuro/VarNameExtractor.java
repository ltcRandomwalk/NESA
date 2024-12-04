import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.*;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.YamlPrinter;
import com.github.javaparser.ast.stmt.Statement;


class VarNameVisitor extends VoidVisitorAdapter<Void>{
    private List<Name> names;
    private List<NameExpr> nameExprs;
    private List<SimpleName> sNames;
    private List<FieldAccessExpr> fAes;

    public VarNameVisitor(){
        names = new ArrayList<Name>();
        nameExprs = new ArrayList<NameExpr>();
        sNames = new ArrayList<SimpleName>();
        fAes = new ArrayList<FieldAccessExpr>();
    }

    public List<Name> getNames(){
        return names;
    }

    public List<NameExpr> getNameExprs(){
        return nameExprs;
    }

    public List<SimpleName> getSimpleNames(){
        return sNames;
    }

    public List<FieldAccessExpr> getFaes(){
        return fAes;
    }

    @Override
    public void visit(Name n, Void arg){
        super.visit(n, arg);
        names.add(n);
    }

    @Override
    public void visit(NameExpr expr, Void arg){
        super.visit(expr, arg);
        if(!expr.getParentNode().isPresent() || !(expr.getParentNode().get() instanceof FieldAccessExpr))
             nameExprs.add(expr);
    }

    @Override
    public void visit(VariableDeclarator vd, Void arg){
        sNames.add(vd.getName());
    }
    
    @Override
    public void visit(FieldAccessExpr fae, Void arg){
        super.visit(fae, arg);
        if(!fae.getParentNode().isPresent() || !(fae.getParentNode().get() instanceof FieldAccessExpr))
             fAes.add(fae);
    }
}

public class VarNameExtractor{

    private List<String> fillWithStrings(List<String> ret, List names){
        for(int i = 0 ;i < names.size(); i++)
            ret.add(names.get(i).toString());
        return ret;
    }

    public List<String> extractVarName(String line){
        line = line.trim();
        if(line.equals("") || line.equals("}") || line.startsWith("//") || line.startsWith("public "))
            return new ArrayList<String>();
        if((line.startsWith("for") || line.startsWith("if") || line.startsWith("synchronized")) && !line.endsWith("}")){
            if(line.endsWith("{"))
                line = line+"}";
            else 
                line = line + "{}";
        }
        try{
            Statement stm = StaticJavaParser.parseStatement(line);
            // YamlPrinter printer = new YamlPrinter(true);
            // System.out.println(printer.output(stm));
            VarNameVisitor vnv = new VarNameVisitor();
            stm.accept(vnv, null); 
            List<String> result = new ArrayList<String>();
            result = fillWithStrings(result, vnv.getNames());
            result = fillWithStrings(result, vnv.getFaes());
            result = fillWithStrings(result, vnv.getNameExprs());
            result = fillWithStrings(result,vnv.getSimpleNames());
            return result;
        }catch(Exception e){
            System.out.println("Ignore line: "+line);
            return new ArrayList<String>();
        }
    }

}
