package chord.analyses.mustnotnull;

public enum EdgeKind {
    NULL,  // <null, null>
    FULL   // <AS, AS'> or <null AS>
}
