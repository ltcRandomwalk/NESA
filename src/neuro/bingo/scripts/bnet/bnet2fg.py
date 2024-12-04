#!/usr/bin/env python3

# Given a bnet.out file produced by cons_all2bnet.py, and a ruleProb.txt file mapping each rule to its probability of
# firing, this script produces a factorGraph.fg file accepted by LibDAI.
# ./bnet2fg.py ruleProb.txt 0.99 < named_bnet.out > factorGraph.fg 2> bnet2fg.log

import logging
import math
#from msilib.schema import tables
import subprocess
import sys
from math import exp

ruleProbFileName = sys.argv[1]
defaultProbability = float(sys.argv[2])

useNeuralNetwork = True

logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")

#######################################################################################################################
# 0. Prelude
def cosine2Odd(cosine):
    
    return math.exp(3 * cosine - 2)
    
    #return 1.0
    #if cosine > 0.56:
     #   return 99
    #else:
      #  return 0.01
    
    K = 0.14
    ori = 0.78
    
    if cosine < ori:
        if 0.5 + 1 / (1 + exp(-K * (cosine-ori))) < 0.1:
            return 0.1
        
        return 0.5 + 1 / (1 + exp(-K * (cosine-ori)))
    return 1.0
    
    #if 0.5 + 1 / (1 + exp(-K * (cosine-ori))) < 0.1:
    #    return 0.1
    
    #return 0.5 + 1 / (1 + exp(-K * (cosine-ori)))
    
    #return K * (cosine) + (1 - K * ori)
    
    #if 1 - cosine < 0.001:
    #    return 1 / ((0.001)**2) * 1/100
    #if cosine < 0.001:
    #    return 1 / ((1 - 0.001)**2) * 1/100
    #return 1 / ((1 - cosine)**2) * 1/100
        
    if 1 - cosine < 0.01:
        return (1 - 0.01 ) / 0.01
    if cosine > 0.5:
        return (cosine) / (1 - cosine)
    if cosine < 0.01:
        return 1 / 99
    return (cosine) / (1 - cosine)

########################################################################################################################
# 1. Accept input

# Read bayesian network
bnetLines = [ line.strip() for line in sys.stdin ]

# Load rule probabilities
ruleProbs = [ line.strip().split(': ') for line in open(ruleProbFileName) ]
ruleProbs = { line[0]: float(line[1]) for line in ruleProbs }

########################################################################################################################
# 2. Compute output

# https://staff.fnwi.uva.nl/j.m.mooij/libDAI/doc/fileformats.html
# It starts with a line containing the number of factors in that graph, followed by an empty line.
numVars = int(bnetLines[0])

outLines = [ numVars, '' ]
logging.info(numVars)
logging.info('')
print(numVars)
print('')

factorLines = bnetLines[1:(numVars + 1)]
assert len(factorLines) == numVars
for varIndex in range(numVars):
    outLines = []
    # Then all factors are specified, using one block for each factor,

    line = factorLines[varIndex]
    components = [ c.strip() for c in line.split(' ') ]

    factorType = components[0] # '*' or '+'
    assert factorType == '*' or factorType == '+' or factorType == '^' or factorType == 'i'
    ruleName = components[1] if factorType == '*' else None
    numParents = int(components[2]) if factorType == '*' else int(components[1])
    if factorType == '^':
        parents = [ int(p) for p in components[2:-1] ]
    else:
        parents = [ int(p) for p in components[3:] ] if factorType == '*' else [ int(p) for p in components[2:] ]
    cosine = float(components[-1]) if factorType == '^' else None

    outLines.append('# Factor {0} of {1}. Finished printing {2}% of factors.'.format(varIndex, numVars, 100 * varIndex / numVars))
    outLines.append('# {0}'.format(line))
    # Each block describing a factor starts with a line containing the number of variables in that factor.
    outLines.append(1 + numParents)
    # The second line contains the labels of these variables, seperated by spaces
    # (labels are nonnegative integers and to avoid confusion, it is suggested to start counting at 0).
    factorVars = [ varIndex ] + parents
    outLines.append(' '.join([ str(v) for v in factorVars ]))
    # The third line contains the number of possible values of each of these variables, also seperated by spaces.
    outLines.append(' '.join([ '2' for v in factorVars ]))

    if factorType == '*':
        # The fourth line contains the number of nonzero entries in the factor table.
        tableSize = int(math.pow(2, 1 + numParents))
        nonZeroEntries = int((tableSize / 2) + 1)
        outLines.append(nonZeroEntries)

        # The rest of the lines contain these nonzero entries;
        # each line consists of a table index, followed by the value corresponding to that table index.
        # The most difficult part is getting the indexing right.
        # The convention that is used is that the left-most variables
        # cycle through their values the fastest
        # (similar to MatLab indexing of multidimensional arrays).
        for i in range(0, tableSize - 2, 2):
            outLines.append('{0} 1'.format(i))

        probability = ruleProbs[ruleName] if (ruleName != None and ruleName in ruleProbs) else \
                      1.0 if ruleName == 'Rnarrow' else \
                      defaultProbability

        outLines.append('{0} {1}'.format(tableSize - 2, 1 - probability))
        outLines.append('{0} {1}'.format(tableSize - 1, probability))
    elif factorType == '+':
        # The fourth line contains the number of nonzero entries in the factor table.
        tableSize = int(math.pow(2, 1 + numParents))
        nonZeroEntries = int((tableSize / 2))
        outLines.append(nonZeroEntries)

        outLines.append('0 1')
        for i in range(3, tableSize, 2):
            outLines.append('{0} 1'.format(i))
    elif factorType == '^':
        tableSize = int(math.pow(2, 1 + numParents))
        nonZeroEntries = int(tableSize)
        outLines.append(nonZeroEntries)

        if useNeuralNetwork:
            predict_prob = cosine
            outLines.append('0 ' + str(predict_prob))
            outLines.append('1 ' + str(1 - predict_prob))
            outLines.append('2 ' + str(1 - predict_prob))
            outLines.append('3 ' + str(predict_prob))
        else: 
            odds = cosine2Odd(cosine)
            outLines.append('0 ' + str(1 - (1.0 / (odds+1))))
            outLines.append('1 ' + str(1.0 / (odds+1)))
            outLines.append('2 ' + str(1 - (1.0 * odds / (odds + 1))))
            outLines.append('3 ' + str(1.0 * odds / (odds + 1)))
    else:  #input nodes
        outLines.append('2')
        outLines.append('0 0.2')
        outLines.append('1 0.8')
        
        
        


    # where the blocks are seperated by empty lines.
    outLines.append('')

    # Print.
    for line in outLines:
        line = str(line)
        logging.info(line)
        if not line.startswith('#'): print(line)
