package chord.analyses.libanalysis.mustalias;

public enum PathKind {
	NULL, // <null, null, null, null>
    COLLECT, // <null, null, null, finalMList>
    QUERY,   // <strtInst, endInst, initMList, finalMList>
    PARTIALCOLLECT, // <null, endInst, null, finalMList>
    PARTIALCOLLECTDONE // <null, endInst, null, finalMList>
}

