cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python taintdriver.py -blr $1

benchmark_folder=$ARTIFACT_ROOT_DIR/benchmarks/android_bench/$1/chord_output_mln-taint-problem
result_folder=$ARTIFACT_ROOT_DIR/reproduced_results/neuro/taint/$1

mkdir -p $result_folder

cp $benchmark_folder/rank-baseline.txt $result_folder/rank-baseline.txt
cp $benchmark_folder/rank-our-approach-ICCmodel.txt $result_folder/rank-our-approach-ICCmodel.txt
cp $benchmark_folder/rank-oracle.txt $result_folder/rank-oracle.txt