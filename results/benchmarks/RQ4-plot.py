#!/usr/bin/python

import argparse
import csv
import sys
import numpy as np
import math
import matplotlib
import matplotlib.pyplot as plt
import itertools
from matplotlib import rcParams
from matplotlib.ticker import FuncFormatter

#def flip(items, ncol):
#     return itertools.chain(*[items[i::ncol] for i in range(ncol)])

def column(matrix, i):
        return [row[i] for row in matrix]

def to_percent(y, position):
    # Ignore the passed in position. This has the effect of scaling the default
    # tick locations.
    s = '{:.0f}'.format(100 * y)

    # The percent symbol needs escaping in latex
    if matplotlib.rcParams['text.usetex'] == True:
        return s + r'$\%$'
    else:
        return s + '%'

parser = argparse.ArgumentParser(description="Plot a graph from a given csv file")

parser.add_argument('-i', type = str, metavar="I", nargs=1, help='specifies the input csv file.', required=True);

parser.add_argument('-r', type = float, metavar="R", default=[0.5], nargs=1, help='specifies the aspect ratio of y over x.');

parser.add_argument('-bw', type = float, metavar="B", default=[0.2], nargs=1, help='specifies the bar width of the bargraph.')

parser.add_argument('-g', type = float, metavar='G', default = [2], nargs=1, help="specifies the space among clusters of the bargraph. Space = G*B")

parser.add_argument('-xo', type = float, metavar='X', default = [0.4], nargs = 1, help="specifies the start point of first bar on the x axis.")

parser.add_argument('-ymin', type = float, metavar='YL', default=[], nargs = 1, help='specifies the min value of y axis.')

parser.add_argument('-ymax', type = float, metavar='YH', default=[], nargs = 1, help='specifies the max value of y axis.')

parser.add_argument('-percentage', action='store_true', help='use percentage for y axis.')

parser.add_argument('-rotateXLabel', type=float,metavar='rl',default=[], nargs = 1,help='specifies to the angle by which the x labels should rotate.')

parser.add_argument('-legend', action='store_true', help='show the legend.')

parser.add_argument('-legRow', type=float,default=[1], nargs=1, help='specifies how many rows the legend occupies')

parser.add_argument('-top', type=str, nargs=1)

parser.add_argument('-o', type = str, nargs=1)

args = parser.parse_args()

data_file_path = args.i[0]

aspect_ratio = args.r[0]

font_size = 12

bar_width = args.bw[0]

cluster_space = args.g[0]*bar_width

xo = args.xo[0]

ymin = args.ymin

ymax = args.ymax

use_percentage = args.percentage

xlabel_rotate_angle = args.rotateXLabel

show_legend = args.legend

leg_rows = args.legRow[0]

data_file = open(data_file_path, 'r')

csv_reader = csv.reader(data_file);

table = []

for row in csv_reader:
    row_data = []
    for cell in row:
        row_data.append(cell)
    table.append(row_data)

legend_labels = table[0][1:len(table[0])]
print(legend_labels)

num_legend_groups = len(legend_labels)

cluster_labels = column(table,0)

cluster_labels = cluster_labels[1:len(cluster_labels)]

num_clusters = len(cluster_labels)

group_width = num_legend_groups*bar_width+cluster_space

y_label = table[0][0]

data_sets = []

for i in range(1, len(table[0])):
    data_sets.append([float(x) for x in column(table,i)[1:num_clusters+1]])

ind = np.arange(num_clusters)

fig = plt.figure()

# make axis ticks point outward from the axes
#rcParams['xtick.direction'] = 'out'
#rcParams['ytick.direction'] = 'out'

# set the color of each bar in the cluster. From white to black.
colors = []

for i in range(0, num_legend_groups):
    colors.append((num_legend_groups-1.0-i)/(num_legend_groups))


ax = fig.add_subplot(1,1,1)

# plot the bar graph
i=0

for i in range(0, len(data_sets)):
    single_data_set = data_sets[i]
    ax.bar(ind*group_width+i*bar_width+xo, single_data_set, bar_width, color=str(colors[i]), label=legend_labels[i])

# set the aspect and the range of y


x1,x2,y1,y2 = plt.axis()

if len(ymin) > 0:
    y1 = ymin[0]

if len(ymax) > 0:
    y2 = ymax[0]

ylength = y2-y1

xlength = x2-x1

ax.set_ylim([y1,y2])

aspect = float(xlength)/ylength * aspect_ratio 

ax.set_aspect(aspect)

# check if to use percentage
if use_percentage:
    formatter = FuncFormatter(to_percent)
    plt.gca().yaxis.set_major_formatter(formatter)


# set the title of y axis
ax.set_ylabel(y_label)
plt.xticks(fontsize=font_size)
plt.yticks(fontsize=font_size)
ax.yaxis.label.set_size(font_size)

#texts = open(args.top[0], 'r').read().split()
#texts = ['0.83','2.57','10.1','213','3.81','10.5','37.1','8.98']
#texts = [text + 'k' for text in texts]
#for i in range(len(texts)):
#    plt.text(xo+i*group_width-0.2, 1.12, texts[i], fontsize=font_size)


# set the x sticks
if len(xlabel_rotate_angle) > 0:
    ax.set_xticks(ind*group_width+xo)
    ax.set_xticklabels(tuple(cluster_labels), rotation = xlabel_rotate_angle[0])
else:
    ax.set_xticks(ind*group_width+num_legend_groups*bar_width/2+xo)
    ax.set_xticklabels(tuple(cluster_labels))

# set the legend
if show_legend:
    num_col = int(math.ceil(num_legend_groups/leg_rows))
    handles, labels = ax.get_legend_handles_labels()
    plt.legend(bbox_to_anchor=(0., 1.1, 1., .11), loc='upper center', prop={'size':font_size}, ncol=num_col, mode='expand', borderaxespad=0.)
num_col = int(math.ceil(num_legend_groups/leg_rows))



#plt.legend(bbox_to_anchor=(0., 1.1, 1., .11), prop={'size':11}, ncol=num_col, mode='expand', borderaxespad=0.)

plt.savefig(args.o[0], bbox_inches='tight')
plt.show()
