#!/bin/bash
pathname=$1
limit=$2
analysis=$3
masterHost=$4
masterPort=$5
chordSize=$6
bddSize=$7

index=1

if [ -z "$CHORD_MAIN" ]; then
	echo "Set env var CHORD_MAIN to location of main/ directory of Chord."
	exit 1
fi

if [ -z "$CHORD_INCUBATOR" ]; then
	echo "Set env var CHORD_INCUBATOR to location of incubator/ directory."
	exit 1
fi

CHORD_JAR=$CHORD_MAIN/chord.jar

while [ $index -le $limit ]
do
	nohup java -Xmx1024m -cp $CHORD_JAR \
		-Dchord.ext.java.analysis.path=$CHORD_INCUBATOR/classes \
		-Dchord.ext.dlog.analysis.path=$CHORD_INCUBATOR/src \
		-Dchord.props.file=$pathname/chordWorker.properties \
		-Dchord.work.dir=$pathname \
		-Dchord.out.dir=./chord_output_$analysis/Worker$index \
		-Dchord.parallel.mode=worker \
		-Dchord.parallel.host=$masterHost \
		-Dchord.parallel.port=$masterPort \
		-Dchord.run.analyses=$analysis \
		-Dchord.max.heap=$chordSize \
		-Dchord.bddbddb.max.heap=$bddSize \
		-Dchord.rhs.merge=pjoin \
		chord.project.Boot &
(( index++ ))
done

