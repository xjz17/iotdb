from cProfile import label
import csv
from itertools import islice
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import matplotlib
# from brokenaxes import brokenaxes

print("1")

plt.rcParams['pdf.fonttype'] = 42
plt.rcParams['ps.fonttype'] = 42

# python result_ratio_vis.py
sns.set_theme(style="ticks", palette="pastel")
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
my_palette1 = ["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19","#9dafff"] 
# 画压缩率的图

plt.tick_params(labelsize=15)
df = pd.read_csv("result_compression_ratio_for_each_dataset.csv")
fig, ax_arr = plt.subplots(1,1, figsize=(10,5))
# fig.subplots_adjust(top=0.82)
# fig.subplots_adjust(hspace=0.2)
# fig.subplots_adjust(wspace=0.2)
# my_palette = sns.color_palette("Set2",n_colors=7)
f = sns.barplot(x="DataSet", y="Compression Ratio",
            order = ["MSRC-12","UCI-Gas","WC-Vehicle","THU-Climate","CW-AIOps","CSSC-Ship","TY-Carriage","WH-Chemistry","CRRC-Train","CBMI-Engine"],
            hue="Encoding",
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            palette=my_palette,
            data=df,
            ax = ax_arr)
f.set_xticklabels(labels = ["MSRC-12","UCI-Gas","WC-Vehicle","TH-Climate","CW-AIOps","CS-Ship","TY-Carriage","WH-Chemistry","CR-Train","CB-Engine"],  rotation=45)
f.get_legend().remove()
f.tick_params(labelsize = 15)
f.xaxis.label.set_size(15)
f.yaxis.label.set_size(15)
# f.set_title("(a) Compression ratio").set_fontsize(20)
lines, labels = ax_arr.get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.1),fontsize=15,ncol=4)

fig.savefig("vary_dataset.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("vary_dataset.png", dpi = 400,bbox_inches='tight')

plt.close()

df = pd.read_csv("ready_to_draw.csv")
# my_palette = sns.color_palette("Set2",n_colors=8)
# fig.subplots_adjust(hspace=0.2)
# fig.subplots_adjust(wspace=0.2)
fig, ax_arr = plt.subplots(1,1, figsize=(10,5))
# fig.subplots_adjust(top=0.82)
f = sns.barplot(x="DataSet", y="value",
            hue="feature",
            order = ["MSRC-12","UCI-Gas","WC-Vehicle","THU-Climate","CW-AIOps","CSSC-Ship","TY-Carriage","WH-Chemistry","CRRC-Train","CBMI-Engine"],
            palette=my_palette1,
            hue_order=["Value mean","Delta mean","Value variance","Delta variance","Value spread","Delta spread","Repeat","Increase"],
            data=df,
            ax = ax_arr)
f.get_legend().remove()
f.set(yscale='log')
f.set_xticklabels(labels = ["MSRC-12","UCI-Gas","WC-Vehicle","TH-Climate","CW-AIOps","CS-Ship","TY-Carriage","WH-Chemistry","CR-Train","CB-Engine"],  rotation=45)
# sns.despine(offset=20, trim=True)
f.tick_params(labelsize = 15)
# f.xaxis.ticks.set_size(20)
#f.get_legend().remove()
f.xaxis.label.set_size(15)
f.yaxis.label.set_size(15)
# f.set_title("(b) Features Values").set_fontsize(20)
lines, labels = ax_arr.get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.1),fontsize=15,ncol=4)

fig.savefig("vary_dataset_features.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("vary_dataset_features.png", dpi = 400,bbox_inches='tight')
