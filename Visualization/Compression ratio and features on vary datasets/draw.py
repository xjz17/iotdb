from cProfile import label
import csv
from itertools import islice
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import matplotlib
# from brokenaxes import brokenaxes
plt.rc('font', family='sans-serif')
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
# python result_ratio_vis.py
sns.set_theme(style="ticks", palette="pastel")

# 画压缩率的图

plt.tick_params(labelsize=20)
df = pd.read_csv("dataset_result_test.csv")
fig, ax_arr = plt.subplots(1,1, figsize=(10,4))
fig.subplots_adjust(top=0.82)
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.2)
my_palette = sns.color_palette("Set2",n_colors=7)
f = sns.barplot(x="Dataset", y="Compression Ratio",
            order = ["MSRC-12","UCI-Gas","WC-Vehicle","THU-Climate","CW-AIOps","CSSC-Ship","TY-Carriage","WH-Chemistry","CRRC-Train","CBMI-Engine"],
            hue="Encoding",
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            ax = ax_arr)
f.set_xticklabels(f.get_xticklabels(), rotation=45)
f.get_legend().remove()
f.tick_params(labelsize = 12)
f.xaxis.label.set_size(12)
f.yaxis.label.set_size(12)
# f.set_title("(a) Compression ratio").set_fontsize(20)
lines, labels = ax_arr.get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=12,ncol=4)

fig.savefig("vary_dataset.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("vary_dataset.png", dpi = 400,bbox_inches='tight')

plt.close()

df = pd.read_csv("ready_to_draw.csv")
my_palette = sns.color_palette("Set2",n_colors=8)
# fig.subplots_adjust(hspace=0.2)
# fig.subplots_adjust(wspace=0.2)
fig, ax_arr = plt.subplots(1,1, figsize=(10,4))
fig.subplots_adjust(top=0.82)
f = sns.barplot(x="Dataset", y="value",
            hue="feature",
            order = ["MSRC-12","UCI-Gas","WC-Vehicle","THU-Climate","CW-AIOps","CSSC-Ship","TY-Carriage","WH-Chemistry","CRRC-Train","CBMI-Engine"],
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c","#ff7f00"],
            hue_order=["Value mean","Delta mean","Value variance","Delta variance","Value spread","Delta spread","Repeat","Increase"],
            data=df,
            ax = ax_arr)
f.get_legend().remove()
f.set(yscale='log')
f.set_xticklabels(f.get_xticklabels(), rotation=45)
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 12)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
f.xaxis.label.set_size(12)
f.yaxis.label.set_size(12)
# f.set_title("(b) Features Values").set_fontsize(20)
lines, labels = ax_arr.get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=12,ncol=4)
'''
f = sns.barplot(x="Dataset", y="Average",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[0][0])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Standard_Variance",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[0][1])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="MAD",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[0][2])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Average_diff",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[0][3])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Variance_diff",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[1][0])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Entropy",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[1][1])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Repeat_words",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[1][2])
f.set(yscale='log')
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)

f = sns.barplot(x="Dataset", y="Singularity",
            #hue="Encoding",
            palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],
            data=df,
            #hue_order=["GORILLA","TS_2DIFF","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            ax = ax_arr[1][3])
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 20)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
# f.legend(fontsize=20)
f.xaxis.label.set_size(20)
f.yaxis.label.set_size(20)
'''

# plt.tick_params(labelsize=20)
# labels = ax_arr[1][1].get_xticklabels() + ax_arr[1][1].get_yticklabels()
# [label.set_fontname('Times New Roman') for label in labels]

# plt.subplots_adjust(bottom=0.20)
# plt.subplots_adjust(left=0.20)
#plt.xticks(rotation=45)
#plt.show()

fig.savefig("vary_dataset_features.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("vary_dataset_features.png", dpi = 400,bbox_inches='tight')
