import sys

base_name = sys.argv[1]
oracle_name = sys.argv[2]
label_name = sys.argv[3]

oracle_set = set( [ line.strip() for line in open(oracle_name, 'r').readlines() ] )

inFile = open(base_name, 'r')
outFile = open(label_name, 'w')

line = inFile.readline()
while line:
    outFile.write(line.strip() + ('+' if line.strip() in oracle_set else '-') + '\n')
    #inFile.readline()
    #inFile.readline()
    #inFile.readline()
    #inFile.readline()
    line = inFile.readline()
        
inFile.close()
outFile.close()