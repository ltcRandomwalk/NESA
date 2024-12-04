#!/usr/bin/env python3

import logging
import math
import subprocess
import sys
import time
import re

testDir = sys.argv[1]
augmentDir = sys.argv[2]

dictFileName = testDir + '/bnet/' + augmentDir + '/bnet-dict.out'
fgFileName = testDir + '/bnet/' + augmentDir + '/factor-graph.fg'
baseQueriesFileName = testDir + '/base_queries.txt'
oracleQueriesFileName = testDir + '/oracle_queries.txt'
softEvidencesFileName = testDir + '/soft_evi.txt'

wrapperExecutable = './libdai/wrapper'


logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")
#######################################################################################################################
# 0. Prelude

def AppendToFile(str, filename):
    with open(filename, 'a') as outFile:
        print(str, file = outFile)

########################################################################################################################
# 1. Setup

# 1a. Populate bayesian network node dictionary
bnetDict = {}
for line in open(dictFileName):
    line = line.strip()
    if len(line) == 0: continue
    components = [ c.strip() for c in line.split(': ') if len(c.strip()) > 0 ]
    assert len(components) == 2
    bnetDict[components[1]] = components[0]

# 1b. Initialize set of labelled tuples (to confirm that tuples are not being relabelled), and populate the set of
# alarms in the ground truth.
labelledTuples = {}
oracleQueries = set([ line.strip() for line in open(oracleQueriesFileName) if len(line.strip()) > 0 ])
baseQueries = set([ line.strip() for line in open(baseQueriesFileName) if len(line.strip()) > 0 ])
softEvidences = set([ line.strip().split()[0] for line in open(softEvidencesFileName) if len(line.strip()) > 0 ])
softEvidencesList = [ line.strip().split()[0] for line in open(softEvidencesFileName) if len(line.strip()) > 0 ]
for q in oracleQueries: assert q in baseQueries, f'Oracle query {q} not found in baseQueries'
for q in baseQueries: assert q in bnetDict, f'Base query {q} not found in bnetDict'

logging.info('Populated {0} oracle queries.'.format(len(oracleQueries)))
logging.info('Populated {0} base queries.'.format(len(baseQueries)))
logging.info('Populated {0} soft evidences.'.format(len(softEvidences)))

# SRK: 15 Feb 2018: For artifact evaluation
unoptimizedRun = 1 if ("noaugment_unopt" in fgFileName) else 0

NSoftEvidence = 10
logFile = testDir + '/logging.txt'
outputFile = testDir + '/softlabel.txt'

########################################################################################################################
# 2. Start LibDAI/wrapper.cpp, and interact with the user

with subprocess.Popen([wrapperExecutable, fgFileName], \
                      stdin=subprocess.PIPE, \
                      stdout=subprocess.PIPE, \
                      universal_newlines=True) as wrapperProc:
    tolerance = 1e-5
    minIters = 500
    maxIters = 1000
    histLength = 100

    def execWrapperCmd(fwdCmd):
        logging.info('Driver to wrapper: ' + fwdCmd)
        print(fwdCmd, file=wrapperProc.stdin)
        wrapperProc.stdin.flush()
        response = wrapperProc.stdout.readline().strip()
        logging.info('Wrapper to driver: ' + response)
        return response

    def observe(t, value):
        assert t not in labelledTuples, 'Attempting to relabel alarm {0}'.format(t)
        if not value == (t in oracleQueries):
            logging.warning('Labelling alarm {0} with value {1}, which does not match ground truth.'.format(t, value))

        fwdCmd = 'O {0} {1}'.format(bnetDict[t], 'true' if value else 'false')
        execWrapperCmd(fwdCmd)
        labelledTuples[t] = value
        
        
    def unobserve(t):
        assert t in labelledTuples, 'Attempting to unlabel alarm {0}'.format(t)
        
        fwdCmd = 'UC {0}'.format(bnetDict[t])
        execWrapperCmd(fwdCmd)
        del(labelledTuples[t])

    def getRankedAlarms():
        alarmList = []
        for t in baseQueries:
            index = bnetDict[t]
            response = float(execWrapperCmd('Q {0}'.format(index)))
            alarmList.append((t, response))
        def getLabelInt(t): return 0 if t not in labelledTuples else 1 if labelledTuples[t] else -1
        def sortKey(rec):
            confidence = rec[1] if not math.isnan(rec[1]) else 0
            return (-getLabelInt(rec[0]), -confidence, not math.isnan(rec[1]), rec[0])
        return sorted(alarmList, key=sortKey)
 

    def getMaxLabels(t, flag):
        softLabels = []
        print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
        index = bnetDict[t]
        response = float(execWrapperCmd('Q {0}'.format(index)))
        print('Prior prob: {0}'.format(response))
        pri = response
        poste = response

        for evi in softEvidencesList[:NSoftEvidence]:
            observe(evi, True)
            execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength))
            result1 = float(execWrapperCmd('Q {0}'.format(index)))
            unobserve(evi)
            observe(evi, False)
            execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength))
            result2 = float(execWrapperCmd('Q {0}'.format(index)))
            unobserve(evi)
            AppendToFile('{0} {1}'.format(result1, result2), logFile)
            
            
            if (result1 >= result2 and flag) or (result1 <= result2 and not flag) :
                observe(evi, True)
                if result1 == result2:
                    softLabels.append(-1)
                else:
                    softLabels.append(1)

                AppendToFile('{0}: true. Posterior prob: {1}\n'.format(evi, result1), logFile)
                poste = result1
                
            else:
                observe(evi, False)
                softLabels.append(0)
                
                AppendToFile('{0}: false. Posterior prob: {1}\n'.format(evi, result2), logFile)
                poste = result2
                
        return softLabels, pri, poste
    
    
    AppendToFile(str(softEvidencesList[:NSoftEvidence]), outputFile)
        
    for t in baseQueries:
        labels,pri, poste = getMaxLabels(t, t in oracleQueries)
        for evi in softEvidencesList[:NSoftEvidence]:
            unobserve(evi)
        #print(softEvidencesList[:100],file = outfile)
        AppendToFile(t, outputFile)
        if t in oracleQueries: AppendToFile('TrueGround', outputFile)
        else: AppendToFile("FalseGround", outputFile)
        AppendToFile(str(labels), outputFile)
        AppendToFile('{0} {1}\n'.format(pri, poste), outputFile)