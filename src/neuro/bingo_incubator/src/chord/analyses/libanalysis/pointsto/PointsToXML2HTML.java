package chord.analyses.libanalysis.pointsto;

import java.io.PrintWriter;

import chord.project.Chord;
import chord.project.analyses.JavaAnalysis;
import chord.project.OutDirUtils;
import chord.project.ClassicProject;
import chord.program.Program;
import chord.analyses.method.DomM;
import chord.analyses.var.DomV;
import chord.analyses.alloc.DomH;

@Chord(name="pointstoxml2html", consumes={ "M", "V", "H" })
public class PointsToXML2HTML extends JavaAnalysis {
	public void run() {
		DomM domM = (DomM) ClassicProject.g().getTrgt("M");
		DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		DomH domH = (DomH) ClassicProject.g().getTrgt("H");

		domM.saveToXMLFile();
        domV.saveToXMLFile();
        domH.saveToXMLFile();

        OutDirUtils.copyResourceByName("web/style.css");
        OutDirUtils.copyResourceByName("chord/analyses/method/Mlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/method/M.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/var/Vlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/var/V.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/Hlist.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/alloc/H.xsl");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/results.dtd");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/results.xml");
        OutDirUtils.copyResourceByName("chord/analyses/libanalysis/pointsto/web/results.xsl");

        OutDirUtils.runSaxon("results.xml", "results.xsl");

        Program.g().HTMLizeJavaSrcFiles();
	}
}

