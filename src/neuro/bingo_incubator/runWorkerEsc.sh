#!/bin/bash
pathname=$1
limit=$2
pre=queryE,
analysis=iter-thresc-java
masterHost=$3
masterPort=$4
chordSize=$5
bddSize=$6

index=1

if [ -z "$CHORD_HOME" ]; then
	echo "Set env var CHORD_HOME to location of main/ directory of Chord."
	exit 1
fi

if [ -z "$CHORD_INCUBATOR" ]; then
	echo "Set env var CHORD_INCUBATOR to location of incubator/ directory."
	exit 1
fi

CHORD_JAR=$CHORD_HOME/chord.jar

while [ $index -le $limit ]
do
	nohup java -Xmx1024m -cp $CHORD_JAR \
		-Dchord.ext.java.analysis.path=$CHORD_INCUBATOR/classes \
		-Dchord.ext.dlog.analysis.path=$CHORD_INCUBATOR/src \
		-Dchord.props.file=$pathname/chordMetaBack.properties \
		-Dchord.work.dir=$pathname \
		-Dchord.out.dir=./chord_output_$analysis/Worker$index \
		-Dchord.parallel.mode=worker \
		-Dchord.parallel.host=$masterHost \
		-Dchord.parallel.port=$masterPort \
		-Dchord.run.analyses=$pre$analysis \
		-Dchord.max.heap=$chordSize \
		-Dchord.bddbddb.max.heap=$bddSize \
		chord.project.Boot &
(( index++ ))
done

