import sys

def read_vvs(file):
    ret = set()
    with open(file) as f:
        lines = f.readlines()
        for l in lines:
            if l.startswith("VV"):
                ret.add(l.strip())
    return ret

def main():
    base_file = sys.argv[1]
    oracle_file = sys.argv[2]
    label_file = sys.argv[3]
    base_vvs = read_vvs(base_file)
    oracle_vvs = read_vvs(oracle_file)
    label_file_str = open(label_file, "w")
    for vv in base_vvs:
        if vv in oracle_vvs:
            label_file_str.write(vv+"+\n")
        else:
            label_file_str.write(vv+"-\n")
    label_file_str.close()

if __name__ == "__main__":
    main()