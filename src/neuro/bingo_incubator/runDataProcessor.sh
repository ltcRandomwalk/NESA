#!/bin/bash
pathname=$1
analysis=metaback
 if [ -z "$CHORD_MAIN" ]; then
 echo "Set env var CHORD_MAIN to location of main/ directory of Chord."
        exit 1
   fi 
   if [ -z "$CHORD_INCUBATOR" ]; then
           echo "Set env var CHORD_INCUBATOR to location of incubator/ directory."
        exit 1 
   fi
CHORD_JAR=$CHORD_MAIN/chord.jar

java -Xmx1024m -cp $CHORD_MAIN/chord.jar \
         -Dchord.ext.java.analysis.path=$CHORD_INCUBATOR/classes \
         -Dchord.ext.dlog.analysis.path=$CHORD_INCUBATOR/src \
	 -Dchord.props.file=$pathname/data.config \
	 -Dchord.work.dir=$pathname \
	 -Dchord.out.dir=./chord_output_$analysis/data \
	 -Dchord.run.analyses=iter-thresc-data \
	 chord.project.Boot
