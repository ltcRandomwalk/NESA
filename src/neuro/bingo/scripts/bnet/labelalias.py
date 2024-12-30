#!/usr/bin/env python3

# Accepts human-readable commands from stdin, and passes them to LibDAI/wrapper.cpp, thus acting as a convenient driver.
# Arguments:
# 1. Dictionary file for the bayesian network, named-dict.out, produced by cons_all2bnet.py. This is to translate
#    commands, such as "O racePairs_cs(428,913) true" to the format accepted by LibDAI/wrapper.cpp, such as
#    "O 38129 true".
# 2. Factor graph, factor-graph.fg
# 3. Base queries file, base_queries.txt. This need not be the full list of base queries produced by Chord, but could
#    instead be any subset of it, such as the alarms reported by the upper oracle.
# 4. Oracle queries file, oracle_queries.txt. Needed while producing combined.out.

# Intended to be run from the main Bingo directory
# ./scripts/bnet/softdriver.py testDir

import logging
import math
import subprocess
import sys
import time
import re

testDir = sys.argv[1]

# augmentDir = sys.argv[2]
augmentDir = 'noaugment_base'

dictFileName = testDir + '/bnet/' + augmentDir + '/bnet-dict.out'
fgFileName = testDir + '/bnet/' + augmentDir + '/factor-graph.fg'
baseQueriesFileName = testDir + '/base_queries.txt'
oracleQueriesFileName = testDir + '/oracle_queries.txt'
softEvidencesFileName = testDir + '/soft_evi.txt'
aliasLabelFileName = testDir + '/aliasLabels.txt'

wrapperExecutable = './libdai/wrapper'

logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")

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
softEvidencesList = [ line.strip().split() for line in open(softEvidencesFileName) if len(line.strip()) > 0 ]
#softEvidencesList = list(softEvidences)
aliasLabelsList = [ line[:-1] for line in open(aliasLabelFileName) if len(line) > 0 ]
#for q in oracleQueries: assert q in baseQueries, f'Oracle query {q} not found in baseQueries'
for q in baseQueries: assert q in bnetDict, f'Base query {q} not found in bnetDict'

logging.info('Populated {0} oracle queries.'.format(len(oracleQueries)))
logging.info('Populated {0} base queries.'.format(len(baseQueries)))
logging.info('Populated {0} alias labels.'.format(len(aliasLabelsList)))

# SRK: 15 Feb 2018: For artifact evaluation
unoptimizedRun = 1 if ("noaugment_unopt" in fgFileName) else 0

########################################################################################################################
# 2. Start LibDAI/wrapper.cpp, and interact with the user

with subprocess.Popen([wrapperExecutable, fgFileName], \
                      stdin=subprocess.PIPE, \
                      stdout=subprocess.PIPE, \
                      universal_newlines=True) as wrapperProc:

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

        if t not in bnetDict: 
            print("Attempt to observe an evidence not in bnet dict.")
            return
        fwdCmd = 'O {0} {1}'.format(bnetDict[t], 'true' if value else 'false')
        execWrapperCmd(fwdCmd)
        labelledTuples[t] = value

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

    def getInversionCount(alarmList):
        numInversions = 0
        numFalse = 0
        for t, confidence in alarmList:
            if t in oracleQueries: numInversions = numInversions + numFalse
            else: numFalse = numFalse + 1
        return numInversions
    
    def getAverageRank(alarmList):
        sumRank = 0
        index = 0
        for t, confidence in alarmList:
            index = index + 1
            if t in oracleQueries: sumRank = sumRank + index
        return sumRank / len(oracleQueries)
  
    def getMiddleRank(alarmList):
        oracleList = [ alarmList.index(alarm) + 1 for alarm in alarmList if alarm[0] in oracleQueries]
        if len(oracleQueries) % 2:
            return oracleList[(len(oracleQueries)-1) // 2]
        else:
            return ( oracleList[len(oracleQueries) // 2] + oracleList[len(oracleQueries) // 2 - 1] ) / 2
        
    
    def getLowestRank(alarmList):
        lowest = 1
        index = 0
        for t, confidence in alarmList:
            index = index + 1
            if t in oracleQueries: lowest = index
        return lowest

    def printRankedAlarms(outFile):
        alarmList = getRankedAlarms()
        print('Rank\tConfidence\tGround\tLabel\tComments\tTuple', file=outFile)
        index = 0
        for t, confidence in alarmList:
            index = index + 1
            ground = 'TrueGround' if t in oracleQueries else 'FalseGround'
            label = 'Unlabelled' if t not in labelledTuples else \
                    'PosLabel' if labelledTuples[t] else \
                    'NegLabel'
            print('{0}\t{1}\t{2}\t{3}\tSPOkGoodGood\t{4}'.format(index, confidence, ground, label, t), file=outFile)
            
        inversionCount = getInversionCount(alarmList)
        averageRank = getAverageRank(alarmList)
        lowestRank = getLowestRank(alarmList)
        middleRank = getMiddleRank(alarmList)
        print('############################################################',file=outFile)
        print('Inversion count: {0}'.format(inversionCount),file=outFile)
        print('Average rank of true alarms: {0}'.format(averageRank),file=outFile)
        print('Lowest rank of true alarms: {0}'.format(lowestRank),file=outFile)
        print('Middle rank of true alarms: {0}'.format(middleRank),file = outFile)
        
    def printMRankedAlarms(outFile):
        alarmList = getRankedAlarms()
        print('Rank\tConfidence\tLabel\tTuple', file=outFile)
        index = 0
        for t, confidence in alarmList:
            index = index + 1
            label = 'Unlabelled' if t not in labelledTuples else \
                    'PosLabel' if labelledTuples[t] else \
                    'NegLabel'
            print(f'{index}\t{confidence}\t{label}\t{t}', file=outFile)

    def runAlarmCarousel(tolerance, minIters, maxIters, histLength, statsFile, combinedPrefix, combinedSuffix):
        assert 0 < tolerance and tolerance < 1
        assert 0 < histLength and histLength < minIters and minIters < maxIters

        numTrue = 0
        numFalse = 0

        print('Tuple\tConfidence\tGround\tNumTrue\tNumFalse\tFraction\tInversionCount\tYetToConvergeFraction\tTime(s)', file=statsFile)
        lastTime = time.time()
        #while len(labelledTuples) < len(baseQueries):
        # SRK: 15th Feb 2018: for artifact evaluation.
        startTime = time.time()
        while not (oracleQueries.issubset(labelledTuples.keys())):
            yetToConvergeFraction = float(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
            rankedAlarmList = getRankedAlarms()
            unlabelledAlarms = [ (t, confidence) for t, confidence in rankedAlarmList if t not in labelledTuples ]
            t0, conf0 = unlabelledAlarms[0]

            ground = 'TrueGround' if t0 in oracleQueries else 'FalseGround'
            if t0 in oracleQueries: numTrue = numTrue + 1
            else: numFalse = numFalse + 1
            fraction = numTrue / (numTrue + numFalse)
            inversionCount = getInversionCount(rankedAlarmList)
            thisTime = int(time.time() - lastTime)
            lastTime = time.time()
            print('{0}\t{1}\t{2}\t{3}\t{4}\t{5}\t{6}\t{7}\t{8}'.format(t0, conf0, ground, numTrue, numFalse, fraction, \
                                                                       inversionCount, yetToConvergeFraction, thisTime), \
                  file=statsFile)
            statsFile.flush()

            with open('{0}{1}.{2}'.format(combinedPrefix, numTrue + numFalse - 1, combinedSuffix), 'w') as outFile:
                printRankedAlarms(outFile)

            logging.info('Setting tuple {0} to value {1}'.format(t0, t0 in oracleQueries))
            observe(t0, t0 in oracleQueries)
            # SRK: 15th Feb 2018: for artifact evaluation.
            if (unoptimizedRun == 1):
                if (len(labelledTuples.keys()) == 4) or ((time.time() - startTime) > 14400):
                    break

    def runManualAlarmCarousel(tolerance, minIters, maxIters, histLength, statsFile, combinedPrefix, combinedSuffix):
        assert 0 < tolerance and tolerance < 1
        assert 0 < histLength and histLength < minIters and minIters < maxIters

        numTrue = 0
        numFalse = 0

        print('Tuple\tConfidence\tGround\tNumTrue\tNumFalse\tFraction\tYetToConvergeFraction\tTime(s)', file=statsFile)
        while len(labelledTuples) < len(baseQueries):
            startTime = time.time()
            yetToConvergeFraction = float(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
            rankedAlarmList = getRankedAlarms()
            unlabelledAlarms = [ (t, confidence) for t, confidence in rankedAlarmList if t not in labelledTuples ]
            t0, conf0 = unlabelledAlarms[0]
            endTime = time.time()
            thisTime = int(endTime - startTime)

            print(f'Highest ranked alarm: {t0} (confidence = {conf0}). Real bug (Y) / False alarm (N) / Abort (A)?')
            ground = next(sys.stdin).strip()
            if ground == 'A':
                logging.info('Aborting MAC interaction loop.')
                break
            ground = (ground == 'Y')
            if ground:
                ground = 'TrueGround'
                numTrue = numTrue + 1
            else:
                ground = 'FalseGround'
                numFalse = numFalse + 1
            fraction = numTrue / (numTrue + numFalse)
            print(f'{t0}\t{conf0}\t{ground}\t{numTrue}\t{numFalse}\t{fraction}\t{yetToConvergeFraction}\t{thisTime}', \
                  file=statsFile)
            statsFile.flush()

            with open(f'{combinedPrefix}{numTrue + numFalse - 1}.{combinedSuffix}', 'w') as outFile:
                printMRankedAlarms(outFile)

            logging.info('Setting tuple {0} to value {1}'.format(t0, t0 in oracleQueries))
            observe(t0, t0 in oracleQueries)

    logging.info('Begin calculating...')
    
    tolerance = 1e-4
    minIters = 500
    maxIters = 100000
    histLength = 100

    assert 0 < tolerance and tolerance < 1
    assert 0 < histLength and histLength < minIters and minIters < maxIters

    time1 = time.time()
    #print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
    time2 = time.time()
    
    #outFileName1 = testDir + '/labelout1.txt'
    #with open(outFileName1, 'w') as outFile: printRankedAlarms(outFile)
    #print('P {0}'.format(outFileName1))
    #with open(outFileName1, 'a') as outFile: print('Inference time: {0}'.format(time2 - time1), file = outFile)
    
    
    #for evi in softEvidencesList:
    #    evidenceName = evi[0]
    #    score = evi[1]
     #   prob = float(execWrapperCmd('Q {0}'.format(bnetDict[evidenceName])))
     #   if float(score) > 0.56 or prob > 0.0001:
     #       observe(evidenceName, True)
     #   else: observe(evidenceName, False)
    for aliaslabel in aliasLabelsList:
        evidence = aliaslabel[:-1]
        value = True if aliaslabel[-1] == '+' else False
        #if value: continue
        print('O {0} {1}'.format(evidence, value))
        observe(evidence, value)
        
    time1 = time.time()
    print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
    time2 = time.time()
    outFileName2 = testDir + '/rank-oracle.txt'
    with open(outFileName2, 'w') as outFile: printRankedAlarms(outFile)
    print('P {0}'.format(outFileName2))
    with open(outFileName2, 'a') as outFile: print('Inference time: {0}'.format(time2 - time1), file = outFile)
    
    

logging.info('Bye!')
