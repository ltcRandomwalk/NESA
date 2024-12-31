cd $ARTIFACT_ROOT_DIR/src/neuro/neuroanalysis/Driver
python driver.py -lspb philo

FILE1=$ARTIFACT_ROOT_DIR/benchmarks/pjbench/java_grande/philo/chord_output_mln-pts-problem/rank-baseline.txt
FILE2=$ARTIFACT_ROOT_DIR/benchmarks/pjbench/java_grande/philo/chord_output_mln-pts-problem/rank-our-approach-small.txt

if [[ -f "$FILE1" && -f "$FILE2" ]]; then
    echo "Test Passed!"
else
    echo "Test Failed!"
fi