# Combining Formal and Informal Information in Bayesian Program Analysis via Soft Evidences (Paper Artifact)

This is the artifact document of the paper *Combining Formal and Informal Information in Bayesian Program Analysis via Soft Evidences* to appear in OOPSLA 2025.

## Introduction

Our artifact includes all source code, scripts, data and statistics in our experiments. Concretely, it supports the following things:

1. Reproduction of all results in our experiments.
2. Reproduction of Tables 2-3 and Figures 8-14 in our paper.
3. Reusability guide for applying NESA framework to other settings and extensions.

## Hardware Dependencies

In our paper, all the experiments were conducted on Ubuntu 22.04.2 with 72-core processors (Intel Xeon Gold 6240, 2.60GHz) and 256GB RAM.
To Reproduce **all results**, we recommend running the experiments with at least 10-core processors, 128GB RAM, and 100GB free storage.
To only reproduce **results of small benchmarks**, we recommend running the experiments with at least 4-core processors, 16GB RAM, and 32GB free storage.

**Note that, using low-performance hardware configurations may affect the results of the inference time in our experiments (Table 2).**

A machine with a CUDA-enabled GPU is also recommended for running experiments efficiently. 

## Getting Started Guide

We provide the artifact as a Docker image. To launch the Docker image, run the following commands:

```bash
docker load < paper382.tar.gz
docker run --gpus all -it paper382
```

The `--gpus all` flag passed all available GPU devices to the container. If you want to use a specific GPU, replace `all` with the GPU ID, e.g., `--gpus device=1`.

Inside the container, you can verify GPU availability by running:
```bash
nvidia-smi
```

The default device during the experiment is set to `cuda:1`. If you want to change it, please modify `~/.bashrc` and set the `DEFAULT_DEVICE` environment variable to the one you expect, and then run `source ~/.bashrc`.

**Note that, for optimal performance, we recommend using a GPU to run experiments.** Ensure the `DEFAULT_DEVICE` environment variable is set correctly and use the `--gpus` flag to link the GPU to the container. If only a CPU is available, the experiments can still run, but the performance will be significantly slow in the parts that rely on neural networks. If you want to run the experiments by CPU, run `docker run -it paper382` and set `DEFAULT_DEVICE` to `cpu`.

Then, to compile the relevant code, run the following command:

```bash
~/NESA/build.sh
```

For large benchmarks, it can take significant memory usage and time cost to run the experiments.
Some results of the baseline approach can take up to one week to reproduce.
For different benchmarks, the experiments can run in parallel. 

**Small benchmarks**: For pointer analysis, `moldyn` and `montecarlo` are small benchmarks. For taint analysis, all the benchmarks are small benchmarks. For the experiment with dynamic feedback, `bc-1.06` and `cflow-1.5` are small benchmarks. These benchmarks have low memory usage and short running time, most of which can be finished within one hour.

## Step by Step Instructions

All scripts are stored in `~/NESA/scripts`. 
The benchmarks we used in our experiment are stored in `~/NESA/benchmarks`.
The original results of our experiment are stored in `~/NESA/original_results`.
All reproduced results will be stored in `~/NESA/reproduced_results`.
The contents of `~/NESA/original_results` can be reproduced in `~/NESA/reproduced_results`.

Since some of the compilations in libDAI (used for Bayesian inference in Bingo) are hardware-dependent, its behavior may not be the same as that in our experiments.
This can lead to minor differences in the reproduced results.

### Reproducing the results in Section 7.2.1 & 7.2.2 (RQ1-2)

#### Pointer Analysis

The following command will apply NESA to `<benchmark>`:

```bash
~/NESA/scripts/run_pts.sh <benchmark>
```

`<benchmark>` can be one of the followings: `montecarlo`, `moldyn`, `weblech`, `toba-s`, `hedc`, `jspider`, `javasrc-p`, `ftp`.
Note that, `montecarlo` and `moldyn` are **small benchmarks**, which can be finished in a few minutes.

Connection to the Internet is necessary for the pointer analysis of benchmarks `weblech` and `jspider`. 

The result rankings will be stored in `~/NESA/reproduced_results/neuro/pts/<benchmark>`, which are copied from the corresponding directory in `~/NESA/benchmarks/pjbench/`.
`rank-baseline.txt`, `rank-our-approach-small.txt`, and `rank-oracle.txt` represent the results of the baseline approach, our approach, and the oracle, respectively.
In these ranking files, alarms are ranked by their marginal probabilities from high to low.
The `Ground` column shows whether the alarm is true or false.
At the bottom of the ranking file, the statistics used in our experiment are listed, including the inversion count, the average rank and the midean rank of true alarms.
The inference time is listed here as well.

#### Taint Analysis

The following command will apply NESA to `<benchmark>`:

```bash
~/NESA/scripts/run_taint.sh <benchmark>
```

`<benchmark>` can be one of the followings: `app-324`, `noisy-sounds`, `app-ca7`, `app-kQm`, `tilt-mazes`, `andors-trail`, `ginger-master`, `app-018`.
All of these benchmarks are **small benchmarks**, which can be finished in a few minutes.

The result rankings will be stored in `~/NESA/reproduced_results/neuro/taint/<benchmark>`, which are copied from the corresponding directory in `~/NESA/benchmarks/android_bench/`.
`rank-baseline.txt`, `rank-our-approach-ICCmodel.txt`, and `rank-oracle.txt` represent the results of the baseline approach, our approach, and the oracle, respectively.
The meanings of the rankings and the statistics are the same of those in the pointer analysis.

#### Reproducing Tables and Figures

##### Reproducing Figure 8

The following command will reproduce Figure 8 based on the alarm rankings calculated above:

```bash
~/NESA/scripts/plot_figure8.sh
```

The reproduced figures will be stored in `~/NESA/reproduced_results/RQ1/figure8`. 
If the results of a benchmark are missed, the orginal results will be used.
For example, if you don't run `~/NESA/scripts/run_pts.sh hedc` to get the alarm rankings of benchmark `hedc`, then the corresponding results in `~/NESA/original_results` will be applied to plot the figure.
The script will print this information to `stdout`.
**Please check if this meets your expectations.**

##### Reproducing Figure 9-10

The following command will reproduce Figure 9 and 10 based on the alarm rankings calculated above:

```bash
~/NESA/scripts/plot_figure_9.sh
~/NESA/scripts/plot_figure_10.sh
```

The reproduced figures will be stored in `~/NESA/reproduced_results/RQ1/figure9` and `~/NESA/reproduced_results/RQ1/figure10`.
Similar to Figure 8, if the results of a benchmark are missed, the original results will be used to plot the figure.
The information will be printed to `stdout` in this case.
**Please check if this meets your expectations.**

##### Reproducing Table 2 a-b

The following command will reproduce Table 2a and 2b based on the alarm rankings calculated above:

```bash
~/NESA/scripts/gen_table2ab.sh
```

The reproduced csv files will be stored in `~/NESA/reproduced_results/RQ2`.
Similar to Figure 8, if the results of a benchmark are missed, the original results will be used to plot the figure.
The information will be printed to `stdout` in this case.
**Please check if this meets your expectations.**

### Reproducing the Results in Section 7.2.3 (RQ3)

#### Running Bingo

The following command will run Bingo on `<benchmark>`:

```bash
~/NESA/scripts/run_bingo.sh <benchmark>
```

`<benchmark>` can be one of the followings: `montecarlo`, `moldyn`, `weblech`, `toba-s`, `hedc`, `jspider`, `javasrc-p`, `ftp`.
For Bingo, all of these benchmarks are large benchmarks. Note that, it can take **extremely long time** to reproduce the results of Bingo.
We suggest running this experiment in the background, and the results will be updated in real time.
In our experiment and the script, we set a time limit of one week.
Since Bingo is an iterative process, the temporary results will be updated continuously.
Therefore, it is possible to get incomplete results at any time when the script runs.

If you want to use the incomplete results to reproduce the figures and tables, run the following command before reproducing:

```bash
~NESA/scripts/copy_bingo.sh <benchmark>
```

The result will be stored as `~/NESA/reproduced_results/neuro/pts/<benchmark>/naivebingo.txt`.
Alarms are ranked by their inspected order from the top to the bottom.
The `Ground` column shows whether the alarm is true or false.
The `Time` column shows the inference time of the alarm's feedback iteration.

#### Reproducing Figure 11

The following command will reproduce Figure 9 and 10 based on the alarm rankings calculated above:

```bash
~/NESA/scripts/plot_figure11.sh
```

The reproduced figures will be stored in `~/NESA/reproduced_results/RQ3/figure11` .
Similar to Figure 8, if the results of a benchmark are missed, the original results will be used to plot the figure.
The information will be printed to `stdout` in this case.
**Please check if this meets your expectations.**

Note that, if the incomplete Bingo ranking is used, the red line (Bingo) will become a part of that of the original figure in our paper.

#### Reproducing Table 2c

The following command will reproduce Table 2c based on the alarm rankings calculated above:

```bash
~/NESA/scripts/gen_table_2c.sh
```

The reproduced csv files will be stored in `~/NESA/reproduced_results/RQ3`.
Similar to Figure 8, if the results of a benchmark are missed, the original results will be used to plot the figure.
The information will be printed to `stdout` in this case.
**Please check if this meets your expectations.**

The following issues should be noticed while reproducing Table 2c:

- If an incomplete Bingo ranking is used to reproduce Table 2c, the corresponding data in the table will become "timeout" if the overall iteration number is less than 200.
- If an incomplete Bingo ranking is used to reproduce Table 2c, the corresponding data in the table can be less than that in the original table.

### Reproducing the Results in Section 7.2.4 (RQ4)

#### Reproducing the Rankings by Different Models

The following command will apply NESA to `<benchmark>`, using `<model>` to predict the confidence that two variables alias to each other.

```bash
~/NESA/scripts/run_pts_with_model.sh <benchmark> <model>
```

`<benchmark>` can be one of the followings: `montecarlo`, `moldyn`, `weblech`, `toba-s`, `hedc`, `jspider`, `javasrc-p`, `ftp`.
Note that, `montecarlo` and `moldyn` are **small benchmarks**, which can be finished in a few minutes.

`<model>` can be one of the followings:
- `small`
- `ftp+moldyn`
- `javasrc-p+montecarlo`
- `toba-s+hedc`
- `weblech+jspider`

The model is named with the benchmarks used to train it. 
While the model `small` is trained with small benchmarks which we applied in Section 7.2.1 (RQ1) in the paper.
If a model is trained with a benchmark, then it is not applied to evaluate the same benchmark.
For example, the model `ftp+moldyn` is not applied to evaluate the benchmark `ftp` in our experiment.

**Note, make sure that the results in Section 7.2.1 (RQ1) have been reproduced before running the above script.** Otherwise, the frontend analysis result will be missed.

The result rankings will be stored in `~/NESA/reproduced_results/neuro/pts/<benchmark>/rank-our-approach-<model>.txt`, which are copied from the corresponding directory in `~/NESA/benchmarks/pjbench`.
The meanings of the rankings and the statistics are the same of those in the pointer analysis.

#### Reproducing Figures 12-14

The following command will reproduce Figure 12 to 14 based on the alarm rankings calculated above:

```bash
~/NESA/scripts/plot_figure12.sh
~/NESA/scripts/plot_figure13.sh
~/NESA/scripts/plot_figure14.sh
```

The reproduced figures will be stored in `~/NESA/reproduced_results/RQ4/figure12`, `~/NESA/reproduced_results/RQ4/figure13`, and `~/NESA/reproduced_results/RQ4/figure14`.
Similar to Figure 8, if the results of a benchmark are missed, the original results will be used to plot the figure.
The information will be printed to `stdout` in this case.
**Please check if this meets your expectations.**


### Reproducing the Results in Section 8 (Extension on Dynamic Information)

#### Run Bingo with Dynamic Feedback

The following command will apply Bingo to `<benchmark>` with three methods: our method (NESA), DynaBoost, and the baseline:

```
~/NESA/scripts/run_dynamic.sh <benchmark>
```

`<benchmark>` can be one of the followings: `bc-1.06`, `cflow-1.5`, `grep-2.19`, `gzip-1.2.4a`, `latex2rtf-2.1.1`, `libtasn1-4.3`, `optipng-0.5.3`, `patch-2.7.1`, `readelf-2.24`, `sed-4.3`, `shntool-3.0.5`, `sort-7.2`, and `tar-1.28`. 

`bc-1.06` and `cflow-1.5` are two **small benchmarks**, which can be finished in about ten minutes.

The results are stored in `~/NESA/reproduced_results/dynamic/<benchmark>`. 
There are lots of output files, but the following two files are the most important and are used to reproduce the table:
1. `<benchmark><method>0.out`. This is the initial ranking file without any user feedback. The average rank of true alarms can be calculated here, which is the data in the "Init" column in Table 3 of our paper.
2. `<benchmark><method>-stats.txt`. This file records the detected alarm at every iteration of Bingo. After Bingo finishes, the count of lines in this file stands for the number of iterations required by a user to identify all true alarms, which is the data in the "Iters" column in Table 3 of our paper.

**NOTE: Bingo will continue running in the background although the script exits.** The exit of the script does not mean that the experiment is done.
One way to confirm the experiment is finished is that the result status file does not update.
Besides, if the experiment is still running when you try to reproduce the table, you will be noticed.

#### Reproducing Table 3

The following command will reproduce Table 3 of our paper using the results calculated above:

```bash
python ~/NESA/scripts/gen_table_3.py
```

The table will be stored as `~/NESA/reproduced_results/dynamic/table3.csv`. In the following two cases, The original data instead of the reproduced data will be used to reproduce the table:
1. The reproduced results for some benchmark do not exist. The script will print "WARNING: XXX file does not exist! Using original data."
2. The result exists but is incomplete. This can occurs if Bingo is still running or terminates before detecting all true alarms on this benchmark. In this case, the script will print "WARNING: Bingo has not finished for <benchmark>! The result stat is not complete. Using original data."

If the above two messages are printed to `stdout`, **please check if this meets your expectations.**

## Reusability Guide

We offer a detailed introduction to the reusable parts of our artifact, including getting training data, training the neural network, and using the trained neural network to analyze a program with soft evidence.
Then, we provide guide on how to apply our framework on a new benchmark and on a new analysis instance.

### Getting Training Data

As we mentioned in Section 6.2 of our paper, we need to train a neural network to produce soft evidences.
The input of the neural network should be a pair of variable names.
Besides, to do supervised learning, we need to get the label of whether the two variables alias to each other.
In our experiment, we apply a 0-CFA pointer analysis to get such pairs of variables, and apply a 2-obj pointer analysis to get the oracle label.

The following command will apply these 2 analyses to `<benchmark>`:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -l <benchmark>
```

The command first run the 2 analyses on `<benchmark>` using `Chord` framework. The analysis results are stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem`.
It then applys `~/NESA/src/neuro/neuroanalysis/AliasLabeler/label_alias.py` to automatically label the alias tuples generated by the 0-CFA analysis.
The labelled results are stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/aliasLabels.txt`, with one line an alias tuple and its label.

The following command can extract the variable names for each alias tuple:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -s <benchmark>
```

It will call `~/NESA/src/neuro/neuroanalysis/VarNameExtractor.jar` to automatically extract variable names for each alias tuples generated by the above analysis.
The source code is stored in `~/NESA/src/neuro/neuroanalysis/VarNameExtractor`.
The results will be stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/processedAlias.txt`.
In this file, the alias tuple and its corresponding variable names are listed per three lines.

### Training the Neural Network

Before training the neural network, the training data `aliasLabels.txt` and `processedAlias.txt` should be put to `~/NESA/src/neuro/neuroanalysis/SimilarityCalculator` with the same file name.
There are two example files in the directory.
If you want to train the neural network with data of more than one benchmark, you should first generate `aliasLabels.txt` and `processedAlias.txt` for each benchmark.
Then, you should combined them in a single file and put it in the `SimilarityCalculator` directory.
For example, if `aliasLabels.txt` for benchmark A and B are like:
```
a+
b-
```

```
c-
d+
```

Then the combined `aliasLabels.txt` should be:
```
a+
b-
c-
d+
```

After the training data is prepared, you can run the following command to train a neural network:

```bash
cd ~/NESA/src/neuro/neuroanalysis/SimilarityCalculator
python alias_model.py
```

This Python script automatically generates training set and test set using the provided data.
Every two epoches, the loss values will be printed to `stdout`, and a set of trained parameters is stored in `~/NESA/src/neuro/neuroanalysis/SimilarityCalculator/model`.
It is named with `my_model<n>.pth`, where n is the number of epoches of the training process.
Therefore, the training script will generate a sequence of models. 
You can pick one based on the loss values.

The structure of the neural network is represented in the class `GraphCodeBERTMeanPooling` in `alias_model.py`. 
You can modify the structure or the hyperparameters in this class.
Besides, you can modify the `learning_rate` hyperparameter at the top of `alias_label.py`.

### Applying the Neural Network to Get Soft Evidences

To apply the model trained above, you should first put it to `~/NESA/data/alias_model` and rename it to `<model>.pth`. (The model name can be anything you like.)

Then, the following command will apply `<model>.pth` to generate soft evidences for `<benchmark>`:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -lsp <benchmark> <model>
```

The explanation of the parameters:
- `-l`: Apply `Chord` to get the pointer analysis result of `<benchmark>`.
- `-s`: Apply `VarNameExtractor` to extract variable names for the alias tuples.
- `-p`: Apply `<model>` to get the soft evidences.

The above three steps can be done separately. For example, if you have already run `-ls` to get the analysis results, then you can run `-p` only to get the soft evidences.

The generated soft evidences will be stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/soft_evi.txt`, with each line an alias tuple and the confidence of whether it is true.

### Running Bayesian Program Analysis

After getting the soft evidences, you can run the following command to apply Bayesian program analysis with soft evidences on `<benchmark>`:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -b <benchmark> <model>
```

The Bayesian network is generated, and the Bayesian inference will run on it. It will produce two rankings of all alarms: one is the baseline method with no feedback, the other is our method with soft evidence feedback.
The result ranking will be stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/`, where `rank-baseline.txt` and `rank-our-approach-<model>.txt` are the alarm ranking of the baseline and our method, respectively.

### New Benchmarks

We will use an example to show how to apply our approach to new benchmarks for pointer analysis.
We assume the new benchmark is called "new_benchmark".

First, create a directory `~/NESA/benchmarks/pjbench/new_benchmark`, with a document `chord.properties` in the benchmark folder.
It should contain at least the following parameters:

```
chord.main.class=<The main class of this benchmark>
chord.class.path=<The paths of all bytecode files of this benchmark>
chord.src.path=<The paths of all source files of this benchmark>
```

Detailed information on other parameters can be found on [Chord website](https://bitbucket.org/psl-lab/jchord/src/master/).

Second, add the benchmark information to `~/NESA/src/neuro/neuroanalysis/Driver/driver.py`.
Concretely, you should add a key-value set `"new_benchmark": "new_benchmark"` to the dict `bench_path_map` in `driver.py`.
Besides, you should also add a key-value tuple `"new_benchmark" => "new_benchmark"` to the dict `%benchmarks` in `~/NESA/src/neuro/bingo_incubator/runner_serial.pl`.

After the above configs, you can run the following command to apply our approach to it:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -lspb new_benchmark <model>
```

The explanation of the above parameters:
- `<model>` is the neural network applied to get the soft evidences, which is stored as `~/NESA/data/alias_models/<model>.pth`.
- `-l`: Apply `Chord` to get the pointer analysis result of `<benchmark>`.
- `-s`: Apply `VarNameExtractor` to extract variable names for the alias tuples.
- `-p`: Apply `<model>` to get the soft evidences.
- `-b`: Apply Bayesian program analysis to get the alarm ranking.

The above steps can be done separately. For example, you can run `-b` only to run Bayesian program analysis based on the result of previous steps.

The result ranking will be stored in `~/NESA/benchmarks/pjbench/new_benchmark/chord_output_mln-pts-problem/`, where `rank-baseline.txt` and `rank-our-approach-<model>.txt` are the alarm ranking of the baseline and our method, respectively.

### New Analysis Instance

In this section, we offer a high-level guideline on how to apply our framework to a new analysis instance.
In our paper, we have extended our approach to a taint analysis and an interval analysis with dynamic feedback.
For these two analyses, the implement details of the analysis frontend can be found in [Apposcopy](https://github.com/utopia-group/apposcopy) and [DynaBoost](https://zenodo.org/records/4731470).
In the main pointer analysis experiment, we use [Chord](https://bitbucket.org/psl-lab/jchord/src/master/) as the analysis frontend and [Bingo](https://github.com/difflog-project/bingo) as the Bayesian analysis backend.
Here, we introduce what should be done to extend our approach to a new analysis instance on Chord. We also list the important files in each part.
**If we mentioned the outputs for the pointer analysis later, you can refer to `~/NESA/benchmarks/pjbench/cache4j/chord_output_mln-pts-problem` as an example.**

#### Running a New Analysis

The following command will run an analysis on a program using Chord:

```bash
~/NESA/src/neuro/bingo_incubator/runner_serial.pl \
    -analysis=... \
    -program=... \
    -mode=serial \
    [...]
```

`-analysis=` is the type of analysis. Supported analyses are listed in the `analyses` set in `~/NESA/src/neuro/bingo_incubator/runner_serial.pl`. In our experiment, we use `mln-pts-problem`, which is the points-to analysis.
`-program=` is the program to analyses. It is the benchmark name such as `hedc`.
`[...]` represents other parameters. For details, please refer to [Chord website](https://bitbucket.org/psl-lab/jchord/src/master/).
The analysis result will be stored in the benchmark folder. One of the most important files is `named_cons_all.txt`, which represents the Datalog derivation graph of the analysis.
It is used to generate Bayesian analysis.
The alarms generated by the analysis are stored in `base_queries.txt`.

#### Extracting Informal Information

The key technique in our approach is to extract useful informal information that related to a program fact. 
First, you should decide which program fact will be used as soft evidence and the informal information you want to use.
For example, in the pointer analysis, we use the program fact `VV(a,b)` which stands for that variable `a` and `b` alias to each other.
We need to extract the variable names for each program fact `VV(a,b)`.

To achieve this, you should modify the source code of Chord, so that it can print all the program facts and the related informal information.
For the pointer analysis, we add a new method `printAlias` in `~/NESA/src/neuro/bingo_incubator/src/chord/analyses/mln/kobj/MLNKobjDriver.java`, so that it can print all alias tuples and the variable names to the file `aliasNames.txt`.

#### Training a Neural Network

The next step is training a neural network to predict how likely a program fact holds.
The neural network should accept the informal information as input, and output the confidence of whether the program fact holds.
In our implement, all of the codes related the neural network are stored in `~/NESA/src/neuro/neuroanalysis/SimilarityCalculator`.
In particular, the structure of the neural network is represented in the class `GraphCodeBERTMeanPooling` in `alias_model.py`. 

After getting the neural network, the confidence of the program facts can be predicted as soft evidence. 
The soft evidences should be stored in `soft_evi.txt` to further support the Bayesian analysis. See the file in the benchmark `cache4j` as an example.

#### Running Bayesian Analysis

The last step is running Bayesian Analysis based on the soft evidence. If you have prepared the result of the analysis frontend and the soft evidences, you can run `~/NESA/src/neuro/neuroanalysis/Driver/driver.py -b <benchmark>` to get the alarm rankings.
The source codes of the Bayesian analysis are stored in `~/NESA/src/neuro/bingo`.
