from cProfile import label
import csv
from itertools import islice
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import matplotlib
plt.rc('font', family='sans-serif')
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['pdf.fonttype'] = 42
matplotlib.rcParams['ps.fonttype'] = 42
# python result_ratio_vis.py
sns.set_theme(style="ticks", palette="pastel")
# drow the fig of compression ratio
# plt.tick_params(labelsize=30)
df = pd.read_csv("text_new_result_all.csv")
fig, ax_arr = plt.subplots(1,3, figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00"]

# fig.subplots_adjust(bottom=0.1)
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.25)

f = sns.boxplot(x="Compression", y="Compression Ratio",
            hue="Encoding", palette=my_palette,
            data=df,
            hue_order=["HUFFMAN","DICTIONARY","RLE","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0])
# f.set(ylim=(0,2.0))
f.tick_params(labelsize = 28) 
f_title = f.set_title("(a) Compression Ratio")
f_title.set_fontsize(30)

f.get_legend().remove()
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("text_time_result_all.csv")
f = sns.boxplot(x="Compression", y="Insert Time",
            hue="Encoding", palette=my_palette,
            data=df,
            hue_order=["HUFFMAN","DICTIONARY","RLE","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1])

f.get_legend().remove()
f.set_title("(b) Insert Time").set_fontsize(30)
f.tick_params(labelsize = 28) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

# df = pd.read_csv("result_long.csv")
f = sns.boxplot(x="Compression", y="Select Time",
            hue="Encoding", palette=my_palette,
            data=df,
            hue_order=["HUFFMAN","DICTIONARY","RLE","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[2])
 
f.get_legend().remove()
f.set_title("(c) Select Time").set_fontsize(30)
f.tick_params(labelsize = 28) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center',bbox_to_anchor=(0.5,1.1),fontsize=30,ncol=4)


plt.show()
fig.savefig("text_compression.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("text_compression.png", dpi = 400,bbox_inches='tight')