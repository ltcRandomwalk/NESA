package chord.analyses.libanalysis.mustalias;

import java.io.PrintWriter;

import chord.project.Chord;
import chord.project.analyses.JavaAnalysis;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.program.Program;
import chord.analyses.invk.DomI;
import chord.analyses.method.DomM;
import chord.analyses.point.DomP;
import chord.analyses.var.DomV;
import chord.analyses.alloc.DomH;

@Chord(name="mustaliasxml2html", consumes={ "M", "P", "H" })
public class MustAliasXML2HTML extends JavaAnalysis {
	public void run() {
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		DomP domP = (DomP) ClassicProject.g().getTrgt("P");
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");

		domM.saveToXMLFile();
        domP.saveToXMLFile();
        domH.saveToXMLFile();

        OutDirUtils.copyResourceByName("web/style.css");
        OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/point/Plist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/point/P.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/results.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/results.xml");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/mustalias/web/results.xsl");

        OutDirUtils.runSaxon("results.xml", "results.xsl");

        Program.g().HTMLizeJavaSrcFiles();
	}
}
