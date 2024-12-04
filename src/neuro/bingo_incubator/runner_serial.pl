#!/usr/bin/perl

use strict;
use Getopt::Long;
use feature "switch";

######################################################################################
# Configuration of environment variables, benchmarks, analyses, and options.
#
# All things that need to be configured are in this section. They include:
# 1. Programs - benchmarks on which you want to run analyses.
# 2. Analyses - analyses you want to run on benchmarks.
# 3. Options  - system properties you want to pass to Chord. There are four levels of options:
# Higest priority options: those passed on the command line of this script, using "-D key=val" syntax.
# Second priority options: those defined in bench_options_map below. They are options specific to an (analysis, benchmark) pair.
# Third  priority options: those defined in local_options_map below. They are options specific to an analysis (but independent of the benchmark).
# Lowest priority options: those defined in global_options below. They are options independent of both the analysis and benchmark.

my $chord_main_dir = &getenv("CHORD_MAIN");
my $chord_incubator_dir = &getenv("CHORD_INCUBATOR");
my $pjbench_dir = &getenv("PJBENCH");
my $mainBench_dir = &getenv("PAG_BENCH");
my $mln_dir = &getenv("MLN");


my $dacapo_dir = "dacapo/benchmarks/";
my $ashes_dir = "ashesJSuite/benchmarks/";
my $boof_dir = "boofcv/benchmarks/";

# Map from program name to program directory relative to $pjbench_dir
my %benchmarks = (
    "array_demo" => "array_demo",
    "test_case" => "test_case",
    "cache4j" => "cache4j",
    "jdbm" => "jdbm",
    "tsp" => "tsp",
    "elevator" => "elevator",
    "hedc" => "hedc",
    "weka" => "weka",
    "weblech" => "weblech-0.0.3",
    "weblech-0.0.3" => "weblech-0.0.3",
    "sor" => "sor",
    "ftp" => "ftp",
    "pool" => "pool",
    "jigsaw" => "jigsaw/Jigsaw",
    "moldyn" => "java_grande/moldyn",
    "series" => "java_grande/series",
    "montecarlo" => "java_grande/montecarlo",
    "raytracer" => "java_grande/raytracer",
    "section1" => "java_grande/grande/src/section1",
    "section2" => "java_grande/grande/src/section2",
    "section3" => "java_grande/grande/src/section3",
    "fourierTrans" => "$boof_dir/fourierTrans/",
    "imageFilter" => "$boof_dir/imageFilter/",
    "imageSegment" => "$boof_dir/imageSegment/",
    "imageStitch" => "$boof_dir/imageStitch/",
    "videoMosaic" => "$boof_dir/videoMosaic/",
    "videoStabilize" => "$boof_dir/videoStabilize/",
    "sceneConstruction" => "$boof_dir/sceneConstruction/",
    "odometryStereo" => "$boof_dir/odometryStereo/",
    "calibrateMono" => "$boof_dir/calibrateMono/",
    "calibrateStereo" => "$boof_dir/calibrateStereo/",
    "fitPolygon" => "$boof_dir/fitPolygon/",
    "lineDetection" => "$boof_dir/lineDetection/",
    "overheadView" => "$boof_dir/overheadView/",
    "pointFeatureTracker" => "$boof_dir/pointFeatureTracker/",
    "poseOfCalib" => "$boof_dir/poseOfCalib/",
    "stereoDisparity" => "$boof_dir/stereoDisparity/",
    "stereoTwoViews" => "$boof_dir/stereoTwoViews/",
    "trackerMeanShift" => "$boof_dir/trackerMeanShift/",
    "lusearch" => "$dacapo_dir/lusearch/",
    "hsqldb" => "$dacapo_dir/hsqldb/",
    "avrora" => "$dacapo_dir/avrora/",
    "antlr" => "$dacapo_dir/antlr/",
    "bloat" => "$dacapo_dir/bloat/",
    "chart" => "$dacapo_dir/chart/",
    "fop" => "$dacapo_dir/fop/",
    "luindex" => "$dacapo_dir/luindex/",
    "batik" => "$dacapo_dir/batik/",
    "pmd" => "$dacapo_dir/pmd/",
    "sunflow" => "$dacapo_dir/sunflow/",
    "xalan" => "$dacapo_dir/xalan/",
    "gj" => "$ashes_dir/gj/",
    "javasrc-p" => "$ashes_dir/javasrc-p/",
    "jpat-p" => "$ashes_dir/jpat-p/",
    "kawa-c" => "$ashes_dir/kawa-c/",
    "rhino-a" => "$ashes_dir/rhino-a/",
    "sablecc-j" => "$ashes_dir/sablecc-j/",
    "sablecc-w" => "$ashes_dir/sablecc-w/",
    "schroeder-m" => "$ashes_dir/schroeder-m/",
    "schroeder-s" => "$ashes_dir/schroeder-s/",
    "soot-c" => "$ashes_dir/soot-c/",
    "soot-j" => "$ashes_dir/soot-j/",
    "symjpack-t" => "$ashes_dir/symjpack-t/",
    "toba-s" => "$ashes_dir/toba-s/",
    "mergesort" => "contest/mergesort/",
    "manager" => "contest/manager/",
    "airlinestickets" => "contest/airlinestickets/",
    "abc" => "temp/",
    "confget" => "../c-pointer-analysis/confget/",
    "test" => "../c-pointer-analysis/test",
);
my @programs = keys %benchmarks;

my @analyses = ("thresc_hybrid", "thresc_metaback", "typestate_metaback","pointsto_libanalysis", "mustalias_libanalysis", "compotypestate", "typestate", "mustalias", "mustalias-td", "infernet", "compomustalias", "mustalias-tdbu","mustalias-bu", "cg-prune", "typeEnvCFA", "liboverlap", "composba", "composbaBaseline", "compoRTA", "compoCHA", "connection", "allocEnvCFA", "0cfa", "superopt", "allocEnvCFAClients", "kCFAClients", "provenance-instr", "print-polysite", "provenance-temp", "softanalysis", "provenance-kcfa", "provenance-kobj", "provenance-kobj-incre","provenance-kobj-composat", "provenance-kobj-compogen","provenance-typestate", "softrefine-kcfa", "kobj", "experiment", "mln-kobj", "mlnconvertor", "cpts_expt",
    "mln-kobj1", "mln-polysite-oracle", "mln-polysite-batch", "mln-polysite-inter", "mln-polysite-problem","mln-downcast-oracle", "mln-downcast-batch", "mln-downcast-problem", "mln-downcast-inter", "mln-polysite-precg-oracle", "mln-polysite-precg-problem","mln-downcast-precg-oracle", "mln-downcast-precg-problem","mln-datarace-oracle", "mln-pts-oracle", "mln-pts-problem", "mln-pts-woinit-oracle", "mln-datarace-batch", "mln-datarace-problem", "mln-datarace-inter", "mln-thresc-oracle", "mln-thresc-batch", "mln-thresc-inter", "incrsolver", "derivsz",
    "dlogwrapper", "incr-dtreedumper", "incr-summgenerator", "incr-domcdumper", "incr-libaxiomdumper", "mln-infoflow-oracle", "mln-infoflow-problem", "mln-infoflow-batch", "mln-infoflow-inter", "nullderef-maxsat", "mln-nullderef-oracle", "mln-nullderef-problem", "mln-nullderef-batch", "c-cipa-0cfa", "c-cspa-kcfa","mln-cpts-batch","mln-cpts-oracle",
    "mln-cipa-pts-learn", "mln-steensgaard-learn", "ursa-datarace-train");

# Lowest priority options, but still higher than $chord_main_dir/chord.properties
my @global_options = (
    "-Dchord.ext.java.analysis.path=$chord_incubator_dir/classes:$chord_incubator_dir/lib/guava-17.0.jar:$chord_incubator_dir/lib/gurobi.jar",
    "-Dchord.ext.dlog.analysis.path=$chord_incubator_dir/src/",
    "-Dchord.max.heap=8192m",
    "-Dchord.bddbddb.max.heap=8192m"
);

# Medium priority options
my %local_options_map = (
    "thresc_hybrid" =>
    [
        "-Dchord.rhs.timeout=300000",
        "-Dchord.escape.optimize=false",
        "-Dchord.escape.both=false",
        "-Dchord.print.results=true",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.run.analyses=cipa-0cfa-dlog,queryE,path-thresc-java,full-thresc-java"
    ],
    "cpts_expt" =>
    [
	"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.run.analyses=feedback,c-cipa-0cfa-compute-dlog,c-cipa-0cfa-test-dlog,cprintrel-java"
    ],
    "thresc_metaback" =>
    [
        "-Dchord.iter-thresc-java.optimize=false",
        "-Dchord.iter-thresc-java.explode=1000",
        "-Dchord.iter-thresc-java.disjuncts=5",
        "-Dchord.iter-thresc-java.timeout=600000",
        "-Dchord.iter-thresc-java.iterlimit=100",
        "-Dchord.iter-thresc-java.xmlToHtmlTask=thresc-xml2html",
        "-Dchord.iter-thresc-java.jobpatch=100",
        "-Dchord.iter-thresc-java.negate=true",
        "-Dchord.iter-thresc-java.prune=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.rhs.timeout=600000",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.rhs.trace=shortest",
		#"-Dchord.reuse.scope=true",
		#"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
		#"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.run.analyses=queryE,cipa-0cfa-dlog,iter-thresc-java",
        "-Dchord.scope.exclude=com.,sun.",
		#"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "typestate_metaback" =>
    [
        "-Dchord.iter-typestate-java.optimize=false",
        "-Dchord.iter-typestate-java.explode=1000",
        "-Dchord.iter-typestate-java.disjuncts=5",
        "-Dchord.iter-typestate-java.timeout=300000",
        "-Dchord.iter-typestate-java.iterlimit=100",
        "-Dchord.iter-typestate-java.xmlToHtmlTask=typestate-xml2html",
        "-Dchord.iter-typestate-java.jobpatch=30",
        "-Dchord.iter-typestate-java.negate=true",
        "-Dchord.iter-typestate-java.prune=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.rhs.timeout=300000",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.rhs.trace=shortest",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.run.analyses=cipa-0cfa-dlog,iter-typestate-java",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.scope.exclude=com.,sun."
    ],

    "pointsto_libanalysis" =>
    [
        "-Dchord.run.analyses=pointstolibanalysis",
        "-Dchord.pointstolibanalysis.staticAnalysis=mod-0-cfa-dlog",
        "-Dchord.pointstolibanalysis.xmlToHtmlTask=pointstoxml2html",

        "-Dchord.scope.exclude=",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",

        "-Dchord.analysis.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "mustalias_libanalysis" =>
    [
        "-Dchord.run.analyses=mustaliaslibanalysis",
        "-Dchord.mustaliaslibanalysis.staticAnalysis=mustalias-java",
        "-Dchord.mustaliaslibanalysis.xmlToHtmlTask=mustaliasxml2html",
        "-Dchord.mustaliaslibanalysis.type=noop",
        "-Dchord.typestate.specfile=generic_typestatespec.txt",
        "-Dchord.typestate.cipa=cipa-java",
        "-Dchord.typestate.cicg=cicg-java",
        "-Dchord.typestate.maxdepth=2",
        "-Dchord.rhs.merge=naive",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.rhs.trace=shortest",

        "-Dchord.scope.exclude=",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",

        "-Dchord.analysis.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "compotypestate" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,compotypestate",
        "-Dchord.typestate.specfile=generic_typestatespec.txt",
        "-Dchord.typestate.cipa=cipa-java",
        "-Dchord.typestate.cicg=cicg-java",
        "-Dchord.typestate.maxdepth=2",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.max.heap=120g",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        #"-Dchord.scope.exclude=com.,sun.",
        #"-Dchord.reuse.scope=true",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        #"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "typestate" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,typestate-java",
        "-Dchord.typestate.specfile=generic_typestatespec.txt",
        "-Dchord.typestate.cipa=cipa-java",
        "-Dchord.typestate.cicg=cicg-java",
        "-Dchord.typestate.maxdepth=2",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.max.heap=120g",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        #"-Dchord.scope.exclude=com.,sun.",
        #"-Dchord.reuse.scope=true",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "mustalias" =>
    [
        "-Dchord.run.analyses=mustaliasoracle-java",
        "-Dchord.mustaliaslibanalysis.type=oracle",
        "-Dchord.typestate.specfile=generic_typestatespec.txt",
        "-Dchord.typestate.cipa=cipa-java",
        "-Dchord.typestate.cicg=cicg-java",
        "-Dchord.typestate.maxdepth=2",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",

        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "mustalias-td" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,hybrid-mustalias-java",
        "-Dchord.mustalias.tdlimit=-1",
        "-Dchord.mustalias.bupelimit=1000000",
        "-Dchord.mustalias.cipa=cipa-java",
        "-Dchord.mustalias.cicg=cicg-java",
        "-Dchord.mustalias.maxdepth=2",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.mustalias.buallms=true",
        "-Dchord.mustalias.statistics=true",
        "-Dchord.mustalias.jumpempty=false",
        "-Dchord.rhs.merge=pjoin",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
#           "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
#           "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "compomustalias" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,compomustalias",
        "-Dchord.mustalias.tdlimit=5",
        "-Dchord.mustalias.bulimit=1",
        "-Dchord.mustalias.bupelimit=1000000",
        "-Dchord.mustalias.cipa=cipa-java",
        "-Dchord.mustalias.cicg=cicg-java",
        "-Dchord.mustalias.maxdepth=2",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.mustalias.buallms=true",
        "-Dchord.mustalias.statistics=true",
        "-Dchord.mustalias.jumpempty=false",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.max.heap=120g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        #"-Dchord.reuse.scope=true",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "mustalias-tdbu" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,hybrid-mustalias-java",
        "-Dchord.mustalias.tdlimit=5",
        "-Dchord.mustalias.bupelimit=1000000",
        "-Dchord.mustalias.cipa=cipa-java",
        "-Dchord.mustalias.cicg=cicg-java",
        "-Dchord.mustalias.maxdepth=2",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.mustalias.buallms=true",
        "-Dchord.mustalias.statistics=true",
        "-Dchord.mustalias.jumpempty=false",
        "-Dchord.rhs.merge=pjoin",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
#           "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
#           "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "mustalias-bu" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,bu-mustalias-java",
        "-Dchord.mustalias.bupelimit=1000000",
        "-Dchord.mustalias.cipa=cipa-java",
        "-Dchord.mustalias.cicg=cicg-java",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.mustalias.buallms=true",
        "-Dchord.mustalias.statistics=true",
        "-Dchord.mustalias.jumpempty=false",
        "-Dchord.bottom-up.merge=pjoin",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
#           "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
#           "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],

    "infernet" =>
    [
        "-Dchord.run.analyses=infernetComm",
        "-Dchord.mustaliaslibanalysis.staticAnalysis=mustalias-java",
        "-Dchord.mustaliaslibanalysis.type=noop",
        "-Dchord.typestate.specfile=generic_typestatespec.txt",
        "-Dchord.typestate.cipa=cipa-java",
        "-Dchord.typestate.cicg=cicg-java",
        "-Dchord.typestate.maxdepth=2",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.rhs.pathgen.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.infernet.wrapper=mustAliasInfernetWrapper-java",

        "-Dchord.scope.exclude=",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",

        "-Dchord.analysis.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org."
    ],
    "cg-prune" =>
    [
        "-Dchord.run.analyses=cg-java",
        "-Dchord.prunerefine.addToView=prunerefine-test",
        "-Dchord.prunerefine.verbose=1",
        "-Dchord.prunerefine.maxIters=3",
        "-Dchord.klimited-prunerefine-java.initTaskNames=cinsencg-java",
        "-Dchord.klimited-prunerefine-java.taskNames=cspa-kcfa-dlog,reachMM-kcfa-dlog",
        "-Dchord.klimited-prunerefine-java.relevantTaskName=reachMM-kcfa-relevant-dlog",
        "-Dchord.klimited-prunerefine-java.transTaskName=reachMM-kcfa-trans-dlog",
        "-Dchord.klimited-prunerefine-java.useObjectSensitivity=false",
        "-Dchord.klimited-prunerefine-java.queryFactoryClass=chord.analyses.cg.CGQueryFactory",
        "-Dchord.klimited-prunerefine-java.inQueryRel=inCsreachMM",
        "-Dchord.klimited-prunerefine-java.outQueryRel=outCsreachMM",
        "-Dchord.klimited-prunerefine-java.queryRel=csreachMM",
        "-Dchord.klimited-prunerefine-java.pruneCtxts=true",
        "-Dchord.klimited-prunerefine-java.refineSites=false",
        "-Dchord.klimited-prunerefine-java.pruningTypeStrategy=is",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "typeEnvCFA" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-noreflect-dlog,typeEnvCFA-java",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "liboverlap" =>
    [
        "-Dchord.run.analyses=liboverlap",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=120g",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "composba" =>
    [
        #"-Dchord.run.analyses=cipa-0cfa-dlog,reachableMIM-dlog,composba",
        "-Dchord.run.analyses=composba",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        "-Dchord.fixCPU=false",
        "-Dchord.CPUID=07",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=120g",
        "-Dchord.reuse.scope=true",
        "-Dchord.reuse.rels=true",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.allocEnvCFA.heapEditable=false",
        "-Dchord.allocEnvCFA.0CFA=false",
        "-Dchord.allocEnvCFA.handleReflection=true",
        "-Dchord.allocEnvCFA.useExtraFilters=false",
        "-Dchord.composba.simulateFwd=false",
        "-Dchord.composba.fullCG=true",
        "-Dchord.composba.localityOpt=true",
        "-Dchord.composba.filterM=true",
        "-Dchord.composba.simulateSuperPerf=false",
        "-Dchord.composba.superPerfIgnoreAppCallbk=false",
        "-Dchord.composba.useLibPrefix=true",
        "-Dchord.composba.degrade=false",
        "-Dchord.composba.stats=false"
    ],
    "composbaBaseline" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,reachableMIM-dlog,composba",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=120g",
        "-Dchord.allocEnvCFA.heapEditable=false",
        "-Dchord.allocEnvCFA.0CFA=false",
        "-Dchord.allocEnvCFA.handleReflection=true",
        "-Dchord.allocEnvCFA.useExtraFilters=false",
        "-Dchord.composba.simulateFwd=false",
        "-Dchord.composba.fullCG=true",
        "-Dchord.composba.localityOpt=true",
        "-Dchord.composba.filterM=true",
        "-Dchord.composba.simulateSuperPerf=false",
        "-Dchord.composba.superPerfIgnoreAppCallbk=false",
        "-Dchord.composba.useLibPrefix=true",
        "-Dchord.composba.degrade=false",
        "-Dchord.composba.stats=false"
    ],
    "compoRTA" =>
    [
        #"-Dchord.run.analyses=cipa-0cfa-dlog,reachableMIM-dlog,composba",
        "-Dchord.run.analyses=compoRTA",
        "-Dchord.reflect.kind=none",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        "-Dchord.fixCPU=false",
        "-Dchord.CPUID=07",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=64g",
        "-Dchord.reuse.scope=false",
        "-Dchord.reuse.rels=false",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.compoRTA.simulateSuperPerf=false",
        "-Dchord.compoRTA.superPerfIgnoreAppCallbk=false",
        "-Dchord.compoRTA.useLibPrefix=true",
        "-Dchord.compoRTA.degrade=false",
        "-Dchord.compoRTA.stats=false",
        "-Dchord.compoRTA.verify=false"
    ],
    "compoCHA" =>
    [
        #"-Dchord.run.analyses=cipa-0cfa-dlog,reachableMIM-dlog,composba",
        "-Dchord.run.analyses=compoCHA",
        "-Dchord.reflect.kind=none",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        "-Dchord.fixCPU=false",
        "-Dchord.CPUID=07",
        #"-Dchord.scope.exclude=com.,sun.",
        #"-Dchord.max.heap=64g",
        "-Dchord.jvmargs=-ea -Xmx64g -Xss32m -XX:MaxPermSize=512m",
        "-Dchord.reuse.scope=false",
        "-Dchord.reuse.rels=false",
        "-Dchord.scope.kind=cha",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
        "-Dchord.compoCHA.simulateSuperPerf=false",
        "-Dchord.compoCHA.superPerfIgnoreAppCallbk=false",
        "-Dchord.compoCHA.useLibPrefix=true",
        "-Dchord.compoCHA.degrade=false",
        "-Dchord.compoCHA.stats=false",
        "-Dchord.compoCHA.verify=false",
        "-Dchord.compoCHA.partialSummaries=true",
        "-Dchord.compoCHA.trackCG=false"
    ],
    "connection" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,chooseQuads,undirected-java-nosummaries-null",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.fixCPU=false",
        "-Dchord.CPUID=07",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=64g",
        "-Dchord.jvmargs=-ea -Xmx64g -Xss32m -XX:MaxPermSize=512m",
        "-Dchord.reuse.scope=false",
        "-Dchord.reuse.rels=false",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "allocEnvCFA" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,allocEnvCFA-java",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.max.heap=120g",
        #"-Dchord.reuse.scope=true",
        #"-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        #"-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "superopt" =>
    [
        "-Dchord.run.analyses=superopt-java",
        "-Dchord.reflect.kind=none",
	"-Dchord.ssa.kind=none",
	"-Dchord.scope.exclude=joeq.,javax.,com.,sun.,sunw.,launcher.,com.sun.,com.ibm.,org.apache.harmony.,org.w3c.,org.xml.,org.ietf.,org.omg.,slib.",
        "-Dchord.bddbddb.work.dir=\${chord.work.dir}/chord_output_superopt_ref/bddbddb",
        "-Dchord.reflect.file=\${chord.work.dir}/chord_output_superopt_ref/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/chord_output_superopt_ref/methods.txt",
    ],
    "0cfa" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-noreflect-dlog,allocEnvCFA-java",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.allocEnvCFA.0CFA=true",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "allocEnvCFAClients" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-noreflect-dlog,allocEnvCFAClients-java",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "kCFAClients" =>
    [
        "-Dchord.run.analyses=kCFAClients-java",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "provenance-instr" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,ctxts-java,argCopy-dlog,provenance-instr",
        "-Dchord.scope.exclude=java.,javax.,com.,sun.,org.",
    ],
    "mlnconvertor" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,ctxts-java,argCopy-dlog,mlnconvertor",
        "-Dchord.scope.exclude=java.,javax.,com.,sun.,org.",
    ],
    "print-polysite" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,ctxts-java,argCopy-dlog,cspa-kcfa-dlog,polysite-dlog,provenance-vis",
        "-Dchord.provenance.out_r=polySite",
        "-Dchord.ctxt.kind=cs",
    ],
    "provenance-temp" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,HIDumper-java,pro-ctxts-java,pro-argCopy-dlog,kcfa-bit-init-dlog_XZ89_,pro-cspa-kcfa-dlog_XZ89_,polysite-dlog_XZ89_,provenance-temp",
        "-Dchord.provenance.instrConfig=$chord_incubator_dir/src/chord/analyses/provenance/kcfa/kcfa-bit-init-dlog_XZ89_.config,$chord_incubator_dir/src/chord/analyses/provenance/kcfa/pro-cspa-kcfa-dlog_XZ89_.config,$chord_incubator_dir/src/chord/analyses/provenance/monosite/polysite-dlog_XZ89_.config",
        "-Dchord.ctxt.kind=cs",
    ],
    "softanalysis" =>
    [
        "-Dchord.run.analyses=soft-java",

        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=true",
        "-Dchord.reflect.file=\${chord.work.dir}/reflect.txt",
        "-Dchord.methods.file=\${chord.work.dir}/methods.txt",
    ],
    "provenance-kcfa" =>
    [
        "-Dchord.run.analyses=kcfa-refiner",
        "-Dchord.scope.exclude=com,sun",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.cfa2=false",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.provenance.boolDomain=true",
        "-Dchord.provenance.invkK=5",
        "-Dchord.provenance.allocK=5",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",

    ],
    "provenance-kobj-incre" =>
    [
        "-Dchord.run.analyses=kobj-refiner-incremaxsat",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.obj2=false",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.provenance.boolDomain=true",
        "-Dchord.provenance.invkK=10",
        "-Dchord.provenance.allocK=10",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
		"-DincSolver=y",
		#"-Dmaxsat_path=/home/xujie/Projects/open-wbo-nos/open-wbo-nos",
		"-Dmaxsat_path=/home/xujie/Projects/open-wbo-inc/open-wbo-inc",
    ],

    "provenance-kobj" =>
    [
        "-Dchord.run.analyses=kobj-refiner",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.obj2=false",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.provenance.boolDomain=true",
        "-Dchord.provenance.invkK=10",
        "-Dchord.provenance.allocK=10",
        "-Dchord.max.heap=64g",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "provenance-kobj-compogen" =>	# scratchpad
    [
        "-Dchord.run.analyses=kobj-refiner-compogen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.obj2=false",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.provenance.boolDomain=true",
        "-Dchord.provenance.invkK=10",
        "-Dchord.provenance.allocK=10",
        "-Dchord.max.heap=64g",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],


    "provenance-kobj-composat" => # scratch pad
    [
        "-Dchord.run.analyses=kobj-refiner-composat",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.obj2=false",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.provenance.boolDomain=true",
        "-Dchord.provenance.invkK=10",
        "-Dchord.provenance.allocK=10",
	"-Dchord.provenance.client=polysite",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],
    "experiment" =>  # scratch pad
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=10g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=experiment",
        "-Dchord.scope.exclude=com.,sun.",
    ],
    "mln-kobj" =>  # scratch pad
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=mln-kobj",
        "-Dchord.scope.exclude=com.,sun.",
    ],
    "mln-kobj1" =>  # scratch pad
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-refiner-mln",
        "-Dchord.scope.exclude=com.,sun.",
    ],
    "mln-polysite-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	"-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-polysite-precg-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-precg-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	"-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-polysite-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_poly_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-polysite-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_poly_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-polysite-precg-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-precg-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_poly_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-polysite-inter" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=polysite",
        "-Dchord.mln.mode=inter",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.05,0.05,0.05,0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_poly_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-pts-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=pts",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	      "-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-pts-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=pts",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_downcast_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-pts-woinit-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen-woinit",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=pts",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	      "-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-cipa-pts-learn" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=cipa-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=pts",
        "-Dchord.mln.mode=learn",
        "-Dchord.mln.nonpldi=true",
	      "-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-steensgaard-learn" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=steensgaard-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=learn",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],

    "mln-downcast-precg-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-precg-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	"-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-downcast-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
	"-Dchord.mln.nonpldiK=2",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],

    "mln-downcast-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_downcast_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-downcast-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_downcast_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-downcast-precg-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-precg-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/qmaxsat/downcast-precg.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-downcast-inter" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=downcast",
        "-Dchord.mln.mode=inter",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.05,0.05,0.05,0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_downcast_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-datarace-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=datarace-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=oracle",
		"-Dchord.mln.useThrEsc=true",
		#"-Dchord.mln.threscFile=$bench_dir/proven_queries_mapped.txt",
        "-Dchord.mln.nonpldiK=3",
        "-Dchord.mln.pointer=kobj",
	"-Dchord.datarace.exclude.init=true",
	"-Dchord.datarace.exclude.eqth=true",
	"-Dchord.datarace.exclude.nongrded=false"
    ],
    "mln-datarace-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=datarace-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_datarace_mcsls.mln",
	"-Dchord.datarace.exclude.init=true",
	"-Dchord.datarace.exclude.eqth=true",
	"-Dchord.datarace.exclude.nongrded=false"
    ],
    "mln-datarace-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=datarace-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_datarace_mcsls.mln",
	"-Dchord.datarace.exclude.init=true",
	"-Dchord.datarace.exclude.eqth=true",
	"-Dchord.datarace.exclude.nongrded=false"
    ],
    "mln-datarace-inter" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=datarace-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=inter",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05,0.05,0.05,0.05,0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_datarace_mcsls.mln",
	"-Dchord.datarace.exclude.init=true",
	"-Dchord.datarace.exclude.eqth=true",
	"-Dchord.datarace.exclude.nongrded=false"
    ],
    "mln-thresc-oracle" => 
    [
        "-Dchord.iter-thresc-java.optimize=false",
        "-Dchord.iter-thresc-java.explode=1000",
        "-Dchord.iter-thresc-java.disjuncts=5",
        "-Dchord.iter-thresc-java.timeout=300000",
        "-Dchord.iter-thresc-java.iterlimit=100",
        "-Dchord.iter-thresc-java.jobpatch=100",
        "-Dchord.iter-thresc-java.negate=true",
        "-Dchord.iter-thresc-java.prune=true",
        "-Dchord.ssa.kind=nophi",
        "-Dchord.rhs.timeout=300000",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.rhs.trace=shortest",
        "-Dchord.run.analyses=cipa-0cfa-dlog,queryE,iter-thresc-java",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.scope.exclude=com.,sun.",
    ],
    "mln-thresc-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=thresc-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_thresc.mln",
    ],
    "mln-thresc-inter" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=thresc-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=inter",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.05,0.05,0.05,0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_thresc.mln",
    ],
    "mln-infoflow-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=infoflow",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mln.nonpldi=true",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.max.heap=64g"
    ],
    "mln-infoflow-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=infoflow",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_infoflow_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-infoflow-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=infoflow",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=1.0",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_infoflow_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-infoflow-inter" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=kobj-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.client=infoflow",
        "-Dchord.mln.mode=inter",
        "-Dchord.mln.infWeight=46",
        "-Dchord.mln.ratio=0.05,0.05,0.05,0.05,0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/final_infoflow_mifu.mln",
        "-Dchord.max.heap=64g"
    ],
    "mln-nullderef-oracle" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.bddbddb.max.heap=40g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=nullderef-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=oracle",
        "-Dchord.mustnotnull.maxdepth=1",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
    ],
    "mln-nullderef-batch" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=nullderef-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/nullderef.mln",
        "-Dchord.mustnotnull.maxdepth=1",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
    ],
    "mln-nullderef-problem" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=nullderef-mln-gen",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.mln.mode=problem",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05,0.1,0.15,0.2,0.25",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/mln.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/nullderef.mln",
        "-Dchord.mustnotnull.maxdepth=1",
        "-Dchord.rhs.merge=pjoin",
        "-Dchord.ssa.kind=nomovephi",
    ],
    "ursa-datarace-train" => 
    [
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=datarace-ursa-train",
        "-Dchord.scope.exclude=com.,sun.",
	"-Dchord.ursa.nonpldiK=2",
	"-Dchord.ursa.useThrEsc=true",
	"-Dchord.ursa.pointer=kobj",
	"-Dchord.ursa.threscFile=\${chord.work.dir}/chord_output_thresc_metaback/Master/proven_queries_mapped.txt",
	"-Dchord.ursa.classifier.savepath=\${chord.work.dir}/chord_output_ursa-datarace-train/trained.txt"
    ],
    "nullderef-maxsat" =>
    [	
        "-Dchord.check.exclude=java.,javax.,sun.,com.sun.,com.ibm., org.apache.harmony.",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reflect.kind=dynamic",
        "-Dchord.reuse.scope=false",
        "-Dchord.run.analyses=cipa-0cfa-dlog,nullderef-maxsatgen-java",
        "-Dchord.max.heap=32g",
	"-Dchord.ssa.kind=nophi"
    ],
    "provenance-typestate" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,typestate-refiner",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.provenance.query=all",
        "-Dchord.provenance.heap=true",
        "-Dchord.provenance.mono=true",
        "-Dchord.provenance.queryWeight=0",
        "-Dchord.max.heap=16g",
        "-Dchord.bddbddb.max.heap=4g",
        "-Dchord.ssa.kind=nomovephi",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],
    "softrefine-kcfa" =>
    [
        "-Dchord.run.analyses=kcfa-softrefiner",
        "-Dchord.scope.exclude=com,sun",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.softrefine.queryWeight=0",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",

    ],
    "kobj" =>
    [
        "-Dchord.run.analyses=cipa-0cfa-dlog,ctxts-java,argCopy-dlog,cspa-kobj-dlog,polysite-dlog",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.kobj.k=2",
        "-Dchord.max.heap=16g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],
    "incrsolver" =>
    [
        "-Dchord.run.analyses=incrsolver",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=120g",
        "-Dchord.bddbddb.max.heap=120g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "derivsz" =>
    [
        "-Dchord.run.analyses=derivsz",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=120g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "dlogwrapper" =>
    [
        "-Dchord.run.analyses=dlogwrapper",
        #"-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org.",
        "-Dchord.ctxt.kind=cs",
        "-Dchord.max.heap=120g",
        "-Dchord.bddbddb.max.heap=120g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "incr-dtreedumper" =>
    [
        "-Dchord.run.analyses=incr-dtreedumper",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "incr-summgenerator" =>
    [
        "-Dchord.run.analyses=incr-summgenerator",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "incr-domcdumper" =>
    [
        "-Dchord.run.analyses=incr-domcdumper",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],

    "incr-libaxiomdumper" =>
    [
        "-Dchord.run.analyses=incr-libaxiomdumper",
        "-Dchord.scope.exclude=com.,sun.",
        "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.",
        "-Dchord.ctxt.kind=co",
        "-Dchord.max.heap=64g",
        "-Dchord.reflect.exclude=true",
        "-Dchord.reuse.scope=false",
        "-Dchord.reflect.kind=dynamic",
    ],
    "c-cipa-0cfa" =>
    [
        "-Dchord.run.analyses=c-cipa-0cfa-dlog",
    ],
    "c-cspa-kcfa" =>
    [
        "-Dchord.run.analyses=c-cspa-kcfa-dlog",
    ],
    "mln-cpts-batch" =>
    [
        "-Dchord.run.analyses=cpts-mln-gen",
        "-Dchord.mln.mode=batch",
        "-Dchord.mln.infWeight=58",
        "-Dchord.mln.ratio=0.05",
        "-Dchord.mln.consWeight=200",
        "-Dchord.mln.solverPath=$mln_dir/eugene.jar",
        "-Dchord.mln.programs=$mln_dir/learned/rev_or.mln,$mln_dir/learned/c_pts.mln",
    ],
    "mln-cpts-oracle" =>
    [
        "-Dchord.run.analyses=cpts-mln-gen",
        "-Dchord.mln.mode=oracle"
    ],
);

# Higher priority options, but lower than @cmdline_options below, which are highest.
my %bench_options_map = (
    "thresc_metaback" =>
    {
        "elevator" => [ ]
    },
    "pointsto_libanalysis" =>
    {
        "elevator" => [ ]
    },
    "mustalias_libanalysis" =>
    {
        "elevator" => [ ]
    },
    "compomustalias" =>
    {
        "lusearch" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "chart" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "xalan" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "toba-s" => [
            "-Dchord.reflect.kind=none"
        ],
        "kawa-c" => [
            "-Dchord.reflect.kind=none"
        ]
    },
    "mustalias-tdbu" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "toba-s" => [
            "-Dchord.reflect.kind=none"
        ],
        "kawa-c" => [
            "-Dchord.reflect.kind=none"
        ]

    },
    "mustalias-td" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "toba-s" => [
            "-Dchord.reflect.kind=none"
        ],
        "kawa-c" => [
            "-Dchord.reflect.kind=none"
        ]


    },
    "mustalias-bu" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "toba-s" => [
            "-Dchord.reflect.kind=none"
        ],
        "kawa-c" => [
            "-Dchord.reflect.kind=none"
        ]

    },

    "typestate_metaback" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ]


    },
    "provenance-kcfa" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ]


    },
"provenance-kobj" =>
   	{
    	    "lusearch" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "luindex" => [
		    "-Dchord.max.heap=64000m",
		    "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "avrora" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "hsqldb" => [
	    	"-Dchord.max.heap=16000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "antlr" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "batik" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "sunflow" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "bloat" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "chart" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "fop" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "pmd" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "xalan" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "rhino-a" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "schroeder-m" => [
		    "-Dchord.max.heap=64000m",
	    ],

   	},
"provenance-kobj-compogen" =>
   	{
    	    "lusearch" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "luindex" => [
		    "-Dchord.max.heap=64000m",
		    "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "avrora" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "hsqldb" => [
	    	"-Dchord.max.heap=16000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "antlr" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "batik" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "sunflow" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "bloat" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "chart" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "fop" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "pmd" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "xalan" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "rhino-a" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "schroeder-m" => [
		    "-Dchord.max.heap=64000m",
	    ],

   	},
"provenance-kobj-incre" =>
   	{
    	    "lusearch" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "luindex" => [
		    "-Dchord.max.heap=64000m",
		    "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "avrora" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "hsqldb" => [
	    	"-Dchord.max.heap=16000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "antlr" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "batik" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "sunflow" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "bloat" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "chart" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "fop" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "pmd" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "xalan" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "rhino-a" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "schroeder-m" => [
		    "-Dchord.max.heap=64000m",
	    ],

   	},

"provenance-kobj-composat" =>
   	{
    	    "lusearch" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "luindex" => [
		    "-Dchord.max.heap=64000m",
		    "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "avrora" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "hsqldb" => [
	    	"-Dchord.max.heap=16000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "antlr" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "batik" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "sunflow" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "bloat" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.provenance.preset=true"
	    ],
	    "chart" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "fop" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "pmd" => [
	    	"-Dchord.max.heap=64000m",
	    ],
	    "xalan" => [
	    	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "rhino-a" => [
	   	"-Dchord.max.heap=64000m",
		"-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
	    ],
	    "schroeder-m" => [
		    "-Dchord.max.heap=64000m",
	    ],

   	},

    "provenance-typestate" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ]


    },

    "kobj" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "rhino-a" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ]

    },

    "softrefine-kcfa" =>
    {
        "lusearch" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "luindex" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "avrora" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "hsqldb" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "antlr" => [
            "-Dchord.max.heap=64000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "batik" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ],
        "sunflow" => [
            "-Dchord.max.heap=16000m",
            "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher."
        ]


    },

    "experiment" =>
    { "pmd" =>
      [ "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org." ]
    , "schroeder-m" =>
      [ "-Dchord.check.exclude=java.,com.,sun.,sunw.,javax.,launcher.,org." ] },

);

######################################################################################
# Process command line arguments

my $help = 0;
my $foreground;
my $mode;
my $chosen_program;
my $chosen_analysis;
my $master_host;
my $master_port;
my $num_workers;
my $memStatsPath;
my @cmdline_options;

GetOptions(
    "mode=s" => \$mode,
"program=s" => \$chosen_program,
    "analysis=s" => \$chosen_analysis,
"host=s" => \$master_host,
    "port=i" => \$master_port,
"workers=i" => \$num_workers,
    "D=s" => \@cmdline_options,
"foreground" => \$foreground,
"memStats:s" => \$memStatsPath
);

my $error = 0;

my @modes = ("master", "worker", "parallel", "serial");
if (!grep {$_ eq $mode} @modes) {
    print "ERROR: expected mode=one of: @modes\n";
    $error = 1;
}

if (!grep {$_ eq $chosen_program} @programs) {
    #print "ERROR: expected program=one of: @programs\n";
    print "WARNING: not one of the default expected program: @programs\n";
    #$error = 1;
}

if (!grep {$_ eq $chosen_analysis} @analyses) {
    print "ERROR: expected analysis=one of: @analyses\n";
    $error = 1;
}

if ($mode eq "master" || $mode eq "worker" || $mode eq "parallel") {
    if (!$master_host) {
        $master_host = "localhost";
        print "WARN: 'host' undefined, setting it to $master_host\n";
    }
    if (!$master_port) {
        $master_port = 8888;
        print "WARN: 'port' undefined, setting it to $master_port\n";
    }
}

if ($mode eq "worker" || $mode eq "parallel") {
    if ($num_workers <= 0) {
        print "ERROR: expected workers=<NUM WORKERS>\n";
        $error = 1;
    }
}

if ($error) {
    print "Usage: $0 -mode=[@modes] -program=[@programs] -analysis=[@analyses] -D key1=val1 ... -D keyN=valN\n";
    exit 1;
}

@cmdline_options = map { "-D$_" } @cmdline_options;
print "INFO: Command line system properties: @cmdline_options\n";

######################################################################################

my $chord_jar_path = "$chord_main_dir/chord.jar";
my $local_options = $local_options_map{$chosen_analysis};
if (!$local_options) { @$local_options = (); }

my $bench_dir;

if (!grep {$_ eq $chosen_program} @programs) {
    $bench_dir = "$mainBench_dir/$chosen_program";
}else{
    $bench_dir = "$pjbench_dir/$benchmarks{$chosen_program}";
}

my $bench_options = $bench_options_map{$chosen_analysis}{$chosen_program};
if (!$bench_options) { @$bench_options = (); }
# NOTE: order of cmdline, bench, local, global options on following line is important
my @options = (@global_options, @$local_options, @$bench_options, @cmdline_options);
@options = map { s/\$\{chord.work.dir\}/$bench_dir/; $_ } @options;
unshift (@options, "-Dchord.work.dir=$bench_dir");
given ($mode) {
    when("master") {
	unshift (@options, "-Dchord.numworkers=$num_workers");
        &run_master(@options);
    }
    when("worker") {
	unshift (@options, "-Dchord.numworkers=$num_workers");
        &run_worker(@options);
    }
    when("parallel") {
	unshift (@options, "-Dchord.numworkers=$num_workers");
        &run_master(@options);
        &run_worker(@options);
    }
    when("serial") {
        &run_serial(@options);
    }
    default { die "Unknown mode: $mode\n"; }
}

######################################################################################

sub run_serial {
    my @final_options = ("-Dchord.out.dir=./chord_output_$chosen_analysis", @_);
    runcmd($foreground, @final_options);
}

sub run_master {
    my @final_options = (("-Dchord.parallel.mode=master", "-Dchord.parallel.host=$master_host", "-Dchord.parallel.port=$master_port",
            "-Dchord.out.dir=./chord_output_$chosen_analysis/Master"), @_);
    runcmd(0, @final_options);
}

sub run_worker {
    my @final_options = (("-Dchord.parallel.mode=worker", "-Dchord.parallel.host=$master_host", "-Dchord.parallel.port=$master_port"), @_);
    for (my $i = 1; $i <= $num_workers; $i++) {
        runcmd(0, "-Dchord.out.dir=./chord_output_$chosen_analysis/Worker$i", @final_options);
    }
}

sub runcmd {
    my $len = @_;
    my @cmd = getcmd(@_[1..($len-1)]);
    my $cmd_str = join(" ", map {"\"" . $_ . "\""} @cmd);
    if ($memStatsPath ne '') { $cmd_str = "/usr/bin/time -v -o " . $memStatsPath . " " . $cmd_str; }
    if (not @_[0]) { $cmd_str = "nohup " . $cmd_str . " "; }
    print "INFO: Running command: $cmd_str\n";
    system($cmd_str);
}

sub getcmd {
    return ("java", "-cp", $chord_jar_path, @_, "chord.project.Boot");
}

sub getenv {
    my $key = $_[0];
    my $val = $ENV{$key};
    if (!$val) {
        print "ERROR: Environment variable '$key' undefined.\n";
        exit 1;
    }
    return $val;
}

