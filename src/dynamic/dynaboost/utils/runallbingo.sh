./runbingo.sh bc-1.06 ${ARTIFACT_ROOT_DIR}/data/dynamic/bc-1.06/ interval ${PREFIX}bc >/dev/null 2>/dev/null &
./runbingo.sh cflow-1.5 ${ARTIFACT_ROOT_DIR}/data/dynamic/cflow-1.5/ interval ${PREFIX}cflow >/dev/null 2>/dev/null &
./runbingo.sh grep-2.19 ${ARTIFACT_ROOT_DIR}/data/dynamic/grep-2.19/ interval ${PREFIX}grep > /dev/null 2> /dev/null &
./runbingo.sh gzip-1.2.4a ${ARTIFACT_ROOT_DIR}/data/dynamic/gzip-1.2.4a/ interval ${PREFIX}gzip > /dev/null 2> /dev/null &
./runbingo.sh libtasn1-4.3 ${ARTIFACT_ROOT_DIR}/data/dynamic/libtasn1-4.3/ interval ${PREFIX}libtasn1 > /dev/null 2> /dev/null &
./runbingo.sh patch-2.7.1 ${ARTIFACT_ROOT_DIR}/data/dynamic/patch-2.7.1/ interval ${PREFIX}patch > /dev/null 2> /dev/null &
./runbingo.sh readelf-2.24 ${ARTIFACT_ROOT_DIR}/data/dynamic/readelf-2.24/ interval ${PREFIX}readelf >/dev/null 2>/dev/null &
./runbingo.sh sed-4.3 ${ARTIFACT_ROOT_DIR}/data/dynamic/sed-4.3/ interval ${PREFIX}sed >/dev/null 2>/dev/null &
./runbingo.sh sort-7.2 ${ARTIFACT_ROOT_DIR}/data/dynamic/sort-7.2/ interval ${PREFIX}sort > /dev/null 2> /dev/null &
./runbingo.sh tar-1.28 ${ARTIFACT_ROOT_DIR}/data/dynamic/tar-1.28/ interval ${PREFIX}tar > /dev/null 2> /dev/null &
./runbingo.sh optipng-0.5.3 ${ARTIFACT_ROOT_DIR}/data/dynamic/optipng-0.5.3/ taint ${PREFIX}optipng >/dev/null 2>/dev/null &
./runbingo.sh latex2rtf-2.1.1 ${ARTIFACT_ROOT_DIR}/data/dynamic/latex2rtf-2.1.1/ taint ${PREFIX}latex2rtf >/dev/null 2>/dev/null &
./runbingo.sh shntool-3.0.5 ${ARTIFACT_ROOT_DIR}/data/dynamic/shntool-3.0.5/ taint ${PREFIX}shntool >/dev/null 2>/dev/null &
