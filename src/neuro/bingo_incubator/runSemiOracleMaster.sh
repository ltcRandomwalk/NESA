#!/bin/bash
pathname=$1
analysis=$2
masterHost=$3
masterPort=$4
chordSize=$5
bddSize=$6

if [ -z "$CHORD_HOME" ]; then
	echo "Set env var CHORD_HOME to location of main/ directory of Chord."
	exit 1
fi

if [ -z "$CHORD_INCUBATOR" ]; then
	echo "Set env var CHORD_INCUBATOR to location of incubator/ directory."
	exit 1
fi

CHORD_JAR=$CHORD_HOME/chord.jar
mkdir $pathname/chord_output_$analysis/SemiOracleMaster
cp $pathname/chord_output_$analysis/Master/groups $pathname/chord_output_$analysis/SemiOracleMaster/  

nohup java -Xmx1024m -cp $CHORD_HOME/chord.jar \
	-Dchord.ext.java.analysis.path=$CHORD_INCUBATOR/classes \
	-Dchord.ext.dlog.analysis.path=$CHORD_INCUBATOR/src \
	-Dchord.props.file=$pathname/chordSemiOracleMaster.properties \
	-Dchord.work.dir=$pathname \
	-Dchord.out.dir=./chord_output_$analysis/SemiOracleMaster \
    -Dchord.parallel.mode=master \
    -Dchord.parallel.host=$masterHost \
    -Dchord.parallel.port=$masterPort \
	-Dchord.run.analyses=$analysis \
	-Dchord.max.heap=$chordSize \
	-Dchord.bddbddb.max.heap=$bddSize \
	-Dchord.rhs.merge=pjoin \
	chord.project.Boot &
