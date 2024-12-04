#!/usr/bin/env python3
import logging
import math
#from msilib.schema import tables
import subprocess
import sys
import time
from math import exp


defaultProbability = float(sys.argv[3])

logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")
testDir = sys.argv[1]
augmentDir = sys.argv[2]

namedBnetFileName = testDir + '/bnet/' + augmentDir + '/named-bnet.out'
dictFileName = testDir + '/bnet/' + augmentDir + '/bnet-dict.out'
fgFileName = testDir + '/bnet/' + augmentDir + '/factor-graph.fg'
baseQueriesFileName = testDir + '/base_queries.txt'
oracleQueriesFileName = testDir + '/oracle_queries.txt'
softEvidencesFileName = testDir + '/soft_evi.txt'
ruleProbFileName = testDir + '/rule-prob.txt'

wrapperExecutable = './libdai/wrapper'

def cosine2Odd(K, ori, cosine):
    if 0.5 + 1 / (1 + exp(-K * (cosine-ori))) < 0.1:
        return 0.1
    
    return 0.5 + 1 / (1 + exp(-K * (cosine-ori)))


K = 0.14
ori = 0.8

while True:
    while ori < 0.9:
        ########################################################################################################################
        # 1. Accept input

        # Read bayesian network
        bnetLines = [ line.strip() for line in open(namedBnetFileName) ]

        # Load rule probabilities
        ruleProbs = [ line.strip().split(': ') for line in open(ruleProbFileName) ]
        ruleProbs = { line[0]: float(line[1]) for line in ruleProbs }
        fgFile = open(fgFileName, 'w')
        

        ########################################################################################################################
        # 2. Compute output

        # https://staff.fnwi.uva.nl/j.m.mooij/libDAI/doc/fileformats.html
        # It starts with a line containing the number of factors in that graph, followed by an empty line.
        numVars = int(bnetLines[0])

        outLines = [ numVars, '' ]
        logging.info(numVars)
        logging.info('')
        print(numVars, file=fgFile)
        print('', file=fgFile)

        factorLines = bnetLines[1:(numVars + 1)]
        assert len(factorLines) == numVars
        for varIndex in range(numVars):
            outLines = []
            # Then all factors are specified, using one block for each factor,

            line = factorLines[varIndex]
            components = [ c.strip() for c in line.split(' ') ]

            factorType = components[0] # '*' or '+'
            assert factorType == '*' or factorType == '+' or factorType == '^'
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
            else:
                tableSize = int(math.pow(2, 1 + numParents))
                nonZeroEntries = int(tableSize)
                outLines.append(nonZeroEntries)

                odds = cosine2Odd(K, ori, cosine)
                outLines.append('0 ' + str(1 - (0.9 / (odds+1))))
                outLines.append('1 ' + str(0.9 / (odds+1)))
                outLines.append('2 ' + str(1 - (0.9 * odds / (odds + 1))))
                outLines.append('3 ' + str(0.9 * odds / (odds + 1)))


            # where the blocks are seperated by empty lines.
            outLines.append('')

            # Print.
            for line in outLines:
                line = str(line)
                logging.info(line)
                if not line.startswith('#'): print(line, file=fgFile)
                
        fgFile.close()
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
        softEvidencesList = list(softEvidences)
        for q in oracleQueries: assert q in baseQueries, f'Oracle query {q} not found in baseQueries'
        for q in baseQueries: assert q in bnetDict, f'Base query {q} not found in bnetDict'

        logging.info('Populated {0} oracle queries.'.format(len(oracleQueries)))
        logging.info('Populated {0} base queries.'.format(len(baseQueries)))
        logging.info('Populated {0} soft evidences.'.format(len(softEvidences)))

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
                print('############################################################',file=outFile)
                print('Inversion count: {0}'.format(inversionCount),file=outFile)
                print('Average rank of true alarms: {0}'.format(averageRank),file=outFile)
                print('Lowest rank of true alarms: {0}'.format(lowestRank),file=outFile)

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
            maxIters = 1000
            histLength = 100

            assert 0 < tolerance and tolerance < 1
            assert 0 < histLength and histLength < minIters and minIters < maxIters

            time1 = time.time()
            print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
            time2 = time.time()
            
            outFileName1 = testDir + '/sigmoid_{0}_{1}_prior.txt'.format(K,ori)
            with open(outFileName1, 'w') as outFile: printRankedAlarms(outFile)
            print('P {0}'.format(outFileName1))
            with open(outFileName1, 'a') as outFile: print('Inference time: {0}'.format(time2 - time1), file = outFile)
            
            stride = 1500
            n = 0
            time1 = time.time()
            while n < len(softEvidencesList):
                print(n)
                start = n
                end = n + stride
                if end >= len(softEvidencesList):
                    end = len(softEvidencesList)
                for evidence in softEvidencesList[start:end]:
                    evidence = '_' + evidence
                    observe(evidence, True)
                    print('O {0} true'.format(evidence))
                print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
                n += stride
                #outputFileName = testDir + '/out{0}.txt'.format(n)
                #with open(outputFileName, 'w') as outFile: printRankedAlarms(outFile)
            
            
            #print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
            time2 = time.time()
            outFileName2 = testDir + '/sigmoid_{0}_{1}_post.txt'.format(K,ori)
            with open(outFileName2, 'w') as outFile: printRankedAlarms(outFile)
            print('P {0}'.format(outFileName2))
            with open(outFileName2, 'a') as outFile: print('Inference time: {0}'.format(time2 - time1), file = outFile)
            
            

        logging.info('Bye!')
        
        ori += 0.01
    K = min(K+0.05, K*1.1)
    ori = 0.8

