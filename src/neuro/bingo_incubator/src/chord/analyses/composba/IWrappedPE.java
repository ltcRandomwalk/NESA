package chord.analyses.composba;


import joeq.Compiler.Quad.Inst;

public interface IWrappedPE<PE extends IEdge, SE extends IEdge> {
    public Inst getInst();
    public PE getPE();
    public IWrappedPE<PE, SE> getWPE();
    public IWrappedSE<PE, SE> getWSE();
}
