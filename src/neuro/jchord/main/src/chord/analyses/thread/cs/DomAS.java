/*
 * Copyright (c) 2008-2010, Intel Corporation.
 * Copyright (c) 2006-2007, The Trustees of Stanford University.
 * All rights reserved.
 * Licensed under the terms of the New BSD License.
 */
package chord.analyses.thread.cs;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Quad;
import chord.project.ClassicProject;
import chord.project.Project;
import chord.project.analyses.ProgramDom;
import chord.analyses.alias.Ctxt;
import chord.analyses.alias.DomC;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.util.tuple.object.Pair;

/**
 * Domain of abstract threads.
 * <p>
 * An abstract thread is a double <tt>(c,m)</tt> denoting the thread
 * which starts at method 'm' in abstract context 'c'.
 *
 * @see chord.analyses.thread.ThreadsAnalysis
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
/*public class DomAS extends ProgramDom<Pair<Ctxt, jq_Method>> {
        private DomC domC;
        private DomM domM;
        public String toXMLAttrsString(Pair<Ctxt, jq_Method> aVal) {
                if (domC == null)
                        domC = (DomC) ClassicProject.g().getTrgt("C");
                if (domM == null)
                        domM = (DomM) ClassicProject.g().getTrgt("M");
                if (aVal == null)
                        return "";
                int c = domC.indexOf(aVal.val0);
                int m = domM.indexOf(aVal.val1);
                return "Cid=\"C" + c + "\" Mid=\"M" + m + "\"";
        }
}*/

public class DomAS extends ProgramDom<Pair<Quad, jq_Method>> {
    private DomI domI;
    private DomM domM;
    public String toXMLAttrsString(Pair<Ctxt, jq_Method> aVal) {
            if (domI == null)
                    domI = (DomI) ClassicProject.g().getTrgt("I");
            if (domM == null)
                    domM = (DomM) ClassicProject.g().getTrgt("M");
            if (aVal == null)
                    return "";
            int i = domI.indexOf(aVal.val0);
            int m = domM.indexOf(aVal.val1);
            return "Iid=\"I" + i + "\" Mid=\"M" + m + "\"";
    }
}