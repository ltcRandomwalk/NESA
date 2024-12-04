#!/bin/bash
pathname=$1
analysis=iter-thresc-java
pre=queryE,
masterHost=$2
masterPort=$3
chordSize=$4
bddSize=$5
additionalOption=$6
if [ -z "$CHORD_HOME" ]; then
	echo "Set env var CHORD_HOME to location of main/ directory of Chord."
	exit 1
fi

if [ -z "$CHORD_INCUBATOR" ]; then
	echo "Set env var CHORD_INCUBATOR to location of incubator/ directory."
	exit 1
fi

CHORD_JAR=$CHORD_HOME/chord.jar

nohup java -Xmx1024m -cp $CHORD_HOME/chord.jar \
	-Dchord.ext.java.analysis.path=$CHORD_INCUBATOR/classes \
	-Dchord.ext.dlog.analysis.path=$CHORD_INCUBATOR/src \
	-Dchord.props.file=$pathname/chordMetaBack.properties \
	-Dchord.work.dir=$pathname \
	-Dchord.out.dir=./chord_output_$analysis/Master \
	-Dchord.parallel.mode=master \
	-Dchord.parallel.host=$masterHost \
	-Dchord.parallel.port=$masterPort \
	-Dchord.run.analyses=$pre$analysis \
	-Dchord.max.heap=$chordSize \
	-Dchord.bddbddb.max.heap=$bddSize \
	$additionalOption \
	chord.project.Boot &
