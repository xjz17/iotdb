from cProfile import label
import csv
from itertools import islice
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import matplotlib

plt.rcParams['pdf.fonttype'] = 42
plt.rcParams['ps.fonttype'] = 42

# python result_ratio_vis.py
sns.set_theme(style="ticks", palette="pastel")

palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
# palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
# drow the fig of compression ratio
# plt.tick_params(labelsize=30)
df = pd.read_csv("result_compression_ratio.csv")
fig, ax_arr = plt.subplots(2,2, figsize=(20,20))

fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.2)

f = sns.boxplot(x="Compression", y="Compression Ratio",
            hue="Encoding", palette=palette,
            data=df[df["DataType"]=="INT32"],
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][0])
f.set(ylim=(0,1))
# sns.despine(offset=30, trim=True)
f.tick_params(labelsize = 30) 
f_title = f.set_title("(a) INT32")
f_title.set_fontsize(30)
# f.xaxis.ticks.set_size(30) 

f.get_legend().remove()
# f.legend(fontsize=30)
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

f = sns.boxplot(x="Compression", y="Compression Ratio",
            hue="Encoding", palette=palette,
            data=df[df["DataType"]=="INT64"],
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][1])
f.set(ylim=(0,1))  
# sns.despine(offset=30, trim=True)

f.set_title("(b) INT64").set_fontsize(30)
f.get_legend().remove()
# f.legend(fontsize=7)
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.tick_params(labelsize = 30)
# labels = ax_arr[0][1].get_xticklabels() + ax_arr[0][1].get_yticklabels()
# [label.set_fontname('Times New Roman') for label in labels]


f = sns.boxplot(x="Compression", y="Compression Ratio",
            hue="Encoding", palette=palette,
            data=df[df["DataType"]=="FLOAT"],
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][0])
# sns.despine(offset=30, trim=True)


f_title = f.set_title("(c) FLOAT")
f_title.set_fontsize(30)
f.get_legend().remove()
f.tick_params(labelsize = 30) 
# f.legend(fontsize=7)
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

f = sns.boxplot(x="Compression", y="Compression Ratio",
            hue="Encoding", palette=palette,
            data=df[df["DataType"]=="DOUBLE"],
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][1])
# sns.despine(offset=30, trim=True)

f.set_title("(d) DOUBLE").set_fontsize(30)

f.get_legend().remove()
f.tick_params(labelsize = 30) 
# f.legend(fontsize=7)
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

lines, labels = ax_arr[0][1].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=30,ncol=4)


# plt.tick_params(labelsize=30)
# labels = ax_arr[1][1].get_xticklabels() + ax_arr[1][1].get_yticklabels()
# [label.set_fontname('Times New Roman') for label in labels]

# plt.subplots_adjust(bottom=0.30)
# plt.subplots_adjust(left=0.30)
# plt.show()
# fig.savefig("compression_ratio.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("fig9.png", dpi = 400,bbox_inches='tight')