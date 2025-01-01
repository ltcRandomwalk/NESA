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

A machine with a CUDA-enabled GPU is also recommended for running experiments efficiently. If only a CPU is available, the experiments can still run, but the performance will be significantly slow in the parts that rely on neural networks.

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

To confirm the environment is configured successfully, we provide a testing script which applies our tool on a tiny program `philo`. Run the following command to test:

```bash
~/NESA/test.sh
```

The script will print `Test Passed!` to `stdout` if it runs successfully. Otherwise, the error will be printed. You can also manully check the output files `~/NESA/benchmarks/pjbench/java_grande/philo/chord_output_mln-pts-problem/rank-our-approach-small.txt` and `~/NESA/benchmarks/pjbench/java_grande/philo/chord_output_mln-pts-problem/rank-baseline.txt`. They should be look like the following:

```
Rank	Confidence	Ground	Label	Comments	Tuple
1	0.877303	TrueGround	Unlabelled	SPOkGoodGood	ptsVH(11,3)
2	0.877303	TrueGround	Unlabelled	SPOkGoodGood	ptsVH(3,1)
...
```

The script will download Graphcodebert from [huggingface](https://huggingface.co/microsoft/graphcodebert-base) the first time you run it.
An error will occur if it fails to connect to huggingface. The following error message will be printed:

```
OSError: We couldn't connect to 'https://huggingface.co' to load this file,...
Check your internet connection or see how to run the library in offline mode.
```

In this case, you should manually download Graphcodebert from [huggingface](https://huggingface.co/microsoft/graphcodebert-base) following the steps on <https://huggingface.co/docs/transformers/installation#offline-mode>, and then mount it to the Docker image.
Assume that you have downloaded Graphcodebert in `/path/to/graphcodebert-base` on your machine, use the following command to run the docker image:

```bash
docker run -v /path/to/graphcodebert-base:/home/user/graphcodebert-base --gpus all -it paper382
```

After that, you should modify `~/.bashrc` to set the environment variable `GRAPHCODEBERT_DIR` to `/home/user/graphcodebert-base`, and run `source ~/.bashrc`.


For large benchmarks, it can take significant memory usage and time cost to run the experiments.
Some results of the baseline approach can take up to one week to reproduce. This mainly contains the baseline approach Bingo run in Section 7.2.3 (RQ3).
**For this approach, it is possible to use incomplete results to reproduce the figures, which does not affect the main claim in our paper.**
We will provide detailed guide in Step by Step Instructions.

We list the small benchmarks below for each analysis. We suggest running experiments only on those small benchmarks if there is a time or resource constraint.
We also list the time cost for each part of our experiments on our machine to help reviewers schedule their time to run all the experiments.
For different benchmarks, the experiments can run in parallel. 

**Small benchmarks**: For pointer analysis, `moldyn` and `montecarlo` are small benchmarks. For taint analysis, all the benchmarks are small benchmarks. For the experiment with dynamic feedback, `libtasn1-4.3` and `patch-2.7.1` are small benchmarks. These benchmarks have low memory usage and short running time, most of which can be finished within one hour.

### Running Time for Each Benchmark

#### Pointer Analysis

| Benchmark | RQ1-2 | RQ3 (Bingo) | RQ4 |
| ------ | ------ | ------ | ------ |
| moldyn | < 1 hour | 1 hour | < 1 hour |
| montecarlo | < 1 hour | 12 hours | < 1 hour |
| weblech | 6 hours | 4 days |  6 hours |
| jspider | 6 hours | 5 days |  6 hours |
| hedc | 2 hours | timeout | 3 hours |
| toba-s | 10 hours | timeout | 3 hours |
| javasrc-p | 3 days | timeout | 3 days |
| ftp | 3 days | timeout | 3 days |

#### Taint Analysis

For all benchmarks, the experiment can finish within one hour. 

#### Experiments on Dynamic Information

For all benchmarks, the experiment can finish within 6 hours. The time cost of the two small benchmarks `libtasn1-4.3` and `patch-2.7.1` is less than one hour. 

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

`libtasn1-4.3` and `patch-2.7.1` are two **small benchmarks**, which can be finished in about ten minutes.

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

### Training Neural Networks (Optional)

The above experiments apply the fine-tuned neural networks which are the same as what we used.
We also introduce how to train a new neural network and use it to analyze a program via soft evidence.
**Note, the experiments in this section is optional, and the results do not relate to any part in our paper.**

#### Getting Training Data

The following command will generate training data from `<benchmark>`:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -ls <benchmark>
```

The labelled alias tuples are stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/aliasLabels.txt`, with one line an alias tuple and its label.
The extracted variable names will be stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/processedAlias.txt`.
In this file, the alias tuple and its corresponding variable names are listed per three lines.

#### Training the Neural Network

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
You can modify the `learning_rate` hyperparameter at the top of `alias_label.py` to help do better training.

#### Applying the Neural Network to Run Analysis

To apply the model trained above, you should first put it to `~/NESA/data/alias_model` and rename it to `<model>.pth`. (The model name can be anything you like.)

Then, the following command will apply `<model>.pth` to generate soft evidences for `<benchmark>`:

```bash
cd ~/NESA/src/neuro/neuroanalysis/Driver
python driver.py -lspb <benchmark> <model>
```

The explanation of the parameters:
- `-l`: Apply `Chord` to get the pointer analysis result of `<benchmark>`.
- `-s`: Apply `VarNameExtractor` to extract variable names for the alias tuples.
- `-p`: Apply `<model>` to get the soft evidences.
- `-b`: Apply Bayesian program analysis to get the alarm ranking.

The above steps can be done separately. For example, if you have already run `-ls` to get the analysis results, then you can run `-p` only to get the soft evidences.

The result ranking will be stored in `~/NESA/benchmarks/pjbench/<benchmark>/chord_output_mln-pts-problem/`, where `rank-baseline.txt` and `rank-our-approach-<model>.txt` are the alarm ranking of the baseline and our method, respectively.


## Reusability Guide

We offer reusablility guide for three use cases of our framework:
1. Applying our framework to analyze a new program.
2. Implementing a new analysis instance using our framework.
3. Implementing a new feature using our framework.

For each use case, we explain which parts of our artifact can be reused and how to reuse them. 

### New Program

We will use an example to show how to apply our framework to new benchmarks for pointer analysis, which is our main experiment.
For taint analysis and the dynamic information experiment, the implement details of the analysis frontend can be found in [Apposcopy](https://github.com/utopia-group/apposcopy) and [DynaBoost](https://zenodo.org/records/4731470).

We assume the new benchmark is called "new_benchmark". Next, we introduce what configurations should be made and how to run Bayesian program analysis with soft evidence on it.

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

In this section, we first introduce what inputs should be prepared for the Bayesian analysis, and then how to use our framework to get alarm rankings.

#### Input Files

In order to implement a new analysis instance, you need to apply an analysis frontend to get a Datalog derivation graph. Besides, you should also provide the confidence that each soft evidence holds. Next, we list the name and content of these files concretely. We assume all the input files are stored in `/path/to/new_analysis`. In the directory `~/NESA/benchmarks/pjbench/cache4j/chord_output_mln-pts-problem`, we provide examples of these files.

First, you need a Datalog derivation graph generated by the analysis frontend, named with `named_cons_all.txt`. The format of this file is as follow:

```
R0: NOT thisMV(1,3565), NOT VH(3565,607), HM(607,1)
R0: NOT thisMV(1,3565), NOT VH(3565,1284), HM(1284,1)
R0: NOT thisMV(91,417), NOT VH(417,557), HM(557,91)
...
```

In this file, each line stands for a ground Datalog rule. It is started with a rule name which is defined by users, and followed by a ground rule. The tuples started with "NOT" stand for conditions, and the last tuple stands for the conclusion.
For example, the ground rule in the first line stands for `thisMV(1,3565), VH(3565,607) ==> HM(607,1)`.

Second, you need a file that stores the rule probability of each rule, named with `rule-prob.txt`. The rule probability can be set manually or by training. The format of this file is as follow:

```
R4: 0.984119
R44: 0.983386
R75: 0.98511
...
```

In this file, each line contains a rule name followed by its probability. The rule probability should stay in 0 to 1. Note that, the rule name should be consistent with that in `named_cons_all.txt`.

Third, you need a file stores the confidence that each soft evidence holds, named with `soft_evi.txt`. The format of this file is as follow:

```
VV(13051,13060) 0.985
VV(13051,13066) 0.56
VV(13051,13077) 0.2
...
```

In this file, each line contains a tuple which served as the soft evidence, followed by the confidence it holds.
For example, the soft evidence `VV(13051,13060)` holds for a likelihood of 0.985. The likelihood should stay in 0 to 1. Besides, the soft evidence should appear in `named_cons_all.txt`.

Last, you need to identify the alarms and true alarms. `based_queries.txt` lists the alarms generated  by the analysis, while `oracle_queries.txt` lists the true alarms. The format of these two files is as follow, with each line an alarm:

```
ptsVH(13045,1284)
ptsVH(13046,1284)
ptsVH(13049,1284)
...
```





#### Running Bayesian Analysis

The following command will build a Bayesian network and run Bayesian analysis based on the Datalog derivation graph. Please ensure that the above input files are stored in /path/to/new_analysis.

```bash
cd ~/NESA/src/neuro/bingo
./scripts/bnet/build-bnet.sh /path/to/new_analysis noaugment_base /path/to/new_analysis/rule-prob.txt /path/to/new_analysis/soft_evi.txt
./scripts/bnet/softdriver2.py /path/to/new_analysis noaugment_base <model>
```

The <model> argument can be defined by users, which is just a suffix of the result file name. The alarm ranking after the soft evidence feedback will be stored in `/path/to/new_analysis/rank-our-approach-<model>.txt`.


### New Features

To help users implement new features using our framework, we briefly introduce the codes that related to the Bayesian analysis with soft evidence.
We build our framework atop [Bingo](https://github.com/difflog-project/bingo), which is an interactive Bayesian analysis engine with user feedback.
The main logic for building a Bayesian network and doing Bayesian inference is the same as Bingo. 
Therefore, we mainly introduce the code that integrates soft evidence.

The python file `~/NESA/src/neuro/bingo/bnet/cons_all2bnet.py` transforms a Datalog derivation graph to a Bayesian network.
It takes the Datalog derivation graph, the rule probabilities and the soft evidences as inputs.
As described in Section 5 in our paper, we encode the soft evidence as a virtual "noisy sensor" in the Bayesian network.
In particular, for a program fact $t$ that the soft evidence is observed on, we add another event $t'$ which serves as a noisy sensor of $t$.
At this step, for each tuple `T(...)` that serves as a soft evidence, we add a node `_T(...)` in the output Bayesian network, and mark it as a noisy sensor using special symbols. (`^` in our code).
The confidence that the soft evidence holds is also logged.

The python file `~/NESA/src/neuro/bingo/bnet/bnet2fg.py` transforms a Bayesion network into a factor graph.
This is because the Bayesian inference algorithm actually runs on a factor graph instead of a Bayesian network.
For more details about the format of the factor graph, you can refer to [libDAI](http://www.libdai.org).
At this step, we attach conditional probabilities to the Bayesian network.
For a soft evidence $t$ and its related noisy sensor $t'$, the code reads the confidence $p$ that it holds, and sets the conditional probabilities as $P(t'\,|\,t)=p, P(t'\,|\,\lnot t)=1-p$.

The python file `~/NESA/src/neuro/bingo/bnet/softdriver2.py` accepts the factor graph as input and runs Bayesian inference algorithm on it.
It observes all the soft evidences, and runs loopy belief propogation algorithm to get the marginal probabilites of all alarms.
The alarm ranking is then printed to the problem directory.
