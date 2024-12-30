import sys 
import os
from matplotlib import pyplot as plt

benchmark = sys.argv[1]
maxiters = 200

artifact_root_dir = os.getenv("ARTIFACT_ROOT_DIR")
reproduced_dir = os.path.join(artifact_root_dir, "reproduced_results")
original_dir = os.path.join(artifact_root_dir, "original_results")

os.chdir(os.path.join(reproduced_dir, "RQ3"))
prefile = 'txts/'+benchmark + '-pre.txt'
postfile = 'txts/'+benchmark + '.txt'
bingofile = 'txts/'+benchmark + '-bingo.txt'
pre_y = open(prefile, 'r').read().split()[:maxiters]
post_y = open(postfile, 'r').read().split()[:maxiters]
bingo_y = open(bingofile, 'r').read().split()


dev_x = [ i + 1 for i in range(maxiters) ]
bingo_x = [ i + 1 for i in range(len(bingo_y)) ]
bingo_maxiters = len(bingo_y)

dev_x.insert(0,0)
bingo_x.insert(0,0)
pre_y.insert(0,0)
post_y.insert(0,0)
bingo_y.insert(0,0)
plt.plot(dev_x, pre_y, color='orange')

plt.plot(dev_x, post_y, color='blue')
plt.plot(bingo_x, bingo_y, color='red')

def draw_spot():
    x0, y0 = bingo_x[-1], int(bingo_y[-1])
    plt.scatter(x0, y0, s=30, color='red')
    #plt.plot([x0, x0], [y0, 0], 'b--', lw=1)
    #plt.plot([0,x0], [y0,y0], 'b--', lw=1)
    show_plot = '(' + str(x0) + ',' + str(y0) + ')'
    plt.annotate(show_plot, xy=(x0, y0), xytext=(x0-10,y0+10), fontsize=18)

if maxiters != bingo_maxiters:
   draw_spot() 
font_size = 20

plt.ylim([0,101])
plt.yticks((0, 25,50,75,100,125,150,175,200),fontsize=font_size)
plt.xticks((0, 25,50,75,100,125,150,175,200),fontsize=font_size)
plt.xlabel('#alarms', fontsize=font_size)
plt.ylabel('#true alarms', fontsize=font_size)

plt.legend(['baseline', 'our approach', 'Bingo'], fontsize=font_size-2)
plt.savefig('figure11/'+benchmark+'-bingo.pdf', bbox_inches='tight')
plt.show()