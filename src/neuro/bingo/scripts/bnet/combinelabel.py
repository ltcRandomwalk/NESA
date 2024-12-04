import subprocess
import logging
import math

testDir = '/home/tcli/neuro/pjbench/cache4j/chord_output_mln-pts-problem'
augmentDir = 'noaugment_base'

softEvidencesFileName = testDir + '/soft_evi.txt'
aliasLabelFileName = testDir + '/aliasLabels.txt'
dictFileName = testDir + '/bnet/' + augmentDir + '/bnet-dict.out'
fgFileName = testDir + '/bnet/' + augmentDir + '/factor-graph.fg'
baseQueriesFileName = testDir + '/base_queries.txt'
oracleQueriesFileName = testDir + '/oracle_queries.txt'
softEvidencesFileName = testDir + '/soft_evi.txt'


wrapperExecutable = './libdai/wrapper'

logging.basicConfig(level=logging.INFO, \
                    format="[%(asctime)s] %(levelname)s [%(name)s.%(funcName)s:%(lineno)d] %(message)s", \
                    datefmt="%H:%M:%S")

bnetDict = {}
for line in open(dictFileName):
    line = line.strip()
    if len(line) == 0: continue
    components = [ c.strip() for c in line.split(': ') if len(c.strip()) > 0 ]
    assert len(components) == 2
    bnetDict[components[1]] = components[0]

aliasLabels = [ line[:-1] for line in open(aliasLabelFileName) if len(line) > 0 ]
softEvidences = [ line.strip().split() for line in open(softEvidencesFileName) if len(line) > 0]

alias = {}
for label in aliasLabels:
    alias[label[:-1]] = {}
    alias[label[:-1]]['label'] = label[-1]
    
for evi in softEvidences:
    print(evi)
    assert evi[0] in alias
    alias[evi[0]]['score'] = evi[1]
    
aliasList = [[evi, alias[evi]['label'], alias[evi]['score']] for evi in alias]
for i in range(len(aliasList)):
    for j in range(i+1, len(aliasList)):
        if float(aliasList[i][2]) < float(aliasList[j][2]):
            tmp = aliasList[j].copy()
            aliasList[j] = aliasList[i].copy()
            aliasList[i] = tmp.copy()
            

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


    tolerance = 1e-6
    minIters = 500
    maxIters = 10000
    histLength = 100
    print(execWrapperCmd('BP {0} {1} {2} {3}'.format(tolerance, minIters, maxIters, histLength)))
    
    for i in range(len(aliasList)):
        evi = aliasList[i][0]
        index = bnetDict[evi]
        response = (float(execWrapperCmd('Q {0}'.format(index))))
        aliasList[i].append(response)
            


outputFileName = testDir + '/combine.txt'
with open(outputFileName, 'w') as f:
    for a in aliasList:
        print('{0} {1} {2} {3}'.format(a[0], a[1], a[2], a[3]), file = f)