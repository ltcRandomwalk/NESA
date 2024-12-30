cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -lspbr $1 small

declare -A benchmark_dict

benchmark_dict=(
    ["moldyn"]="java_grande/moldyn"
    ["montecarlo"]="java_grande/montecarlo"
    ["ftp"]="ftp"
    ["jspider"]="jspider"
    ["weblech"]="weblech-0.0.3"
    ["hedc"]="hedc"
    ["javasrc-p"]="ashesJSuite/benchmarks/javasrc-p"
    ["toba-s"]="ashesJSuite/benchmarks/toba-s"
)

benchmark_folder=$ARTIFACT_ROOT_DIR/benchmarks/pjbench/${benchmark_dict[$1]}/chord_output_mln-pts-problem
result_folder=$ARTIFACT_ROOT_DIR/reproduced_results/neuro/pts/$1

mkdir -p $result_folder
cp $benchmark_folder/rank-baseline.txt $result_folder/rank-baseline.txt
cp $benchmark_folder/rank-our-approach-small.txt $result_folder/rank-our-approach-small.txt
cp $benchmark_folder/rank-oracle.txt $result_folder/rank-oracle.txt