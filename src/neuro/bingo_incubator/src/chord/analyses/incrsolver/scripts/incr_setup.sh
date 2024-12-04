#!/bin/bash

mkdir chord_output_incrsolver
cd chord_output_incrsolver
mkdir bddbddb
cd bddbddb
cp ../../chord_output_derivsz/bddbddb/*_s.bdd .
cp ../../chord_output_derivsz/bddbddb/*_f.bdd .
cp ../../chord_output_derivsz/bddbddb/*_l.bdd .
cp ../../chord_output_derivsz/bddbddb/matched.bdd .
cp ~/chord/incubator/temp.dlog conditional_summ_load.dlog.generated
cd ../..
