package chord.analyses.composba;


public interface IWrappedSE<PE extends IEdge, SE extends IEdge> {
    public SE getSE();
    public IWrappedPE<PE,SE> getWPE();
}
