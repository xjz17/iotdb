from cProfile import label
import csv
from itertools import islice
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd

fin = open("result_ingestion.csv", "r", encoding = "UTF-8")
fout1 = open("result_int.csv", "w", encoding="UTF-8", newline="")
fout2 = open("result_long.csv", "w", encoding="UTF-8", newline="")
fout3 = open("result_float.csv", "w", encoding="UTF-8", newline="")
fout4 = open("result_double.csv", "w", encoding="UTF-8", newline="")

reader = csv.reader(fin)


fout1.write("DataSet,Compressor,Encoding,Insert Time,Select Time\n")
fout2.write("DataSet,Compressor,Encoding,Insert Time,Select Time\n")
fout3.write("DataSet,Compressor,Encoding,Insert Time,Select Time\n")
fout4.write("DataSet,Compressor,Encoding,Insert Time,Select Time\n")

for ln in islice(reader, 1, None):
    if ln[1] == "INT32":
        fout1.write("{},{},{},{},{}\n".format(ln[0], ln[2], ln[3], ln[4], ln[5]))
    if ln[1] == "INT64":
        fout2.write("{},{},{},{},{}\n".format(ln[0], ln[2], ln[3], ln[4], ln[5]))
    if ln[1] == "FLOAT":
        fout3.write("{},{},{},{},{}\n".format(ln[0], ln[2], ln[3], ln[4], ln[5]))
    if ln[1] == "DOUBLE":
        fout4.write("{},{},{},{},{}\n".format(ln[0], ln[2], ln[3], ln[4], ln[5]))
fout1.close()
fout2.close()
fout3.close()
fout4.close()


plt.rcParams['pdf.fonttype'] = 42
plt.rcParams['ps.fonttype'] = 42

# python result_time_vis.py
sns.set_theme(style="ticks", palette="pastel")
plt.tick_params(labelsize=30)
# 画插入时间的图
palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig, ax_arr = plt.subplots(2,2, figsize=(20,20))

fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.25)

df = pd.read_csv("result_int.csv")
f = sns.boxplot(x="Compression", y="Insert Time",
            hue="Encoding", palette=palette,
            data=df,
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][0])

f.get_legend().remove()
f.set_title("(a) INT32").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_long.csv")
f = sns.boxplot(x="Compression", y="Insert Time",
            hue="Encoding", palette=palette,
            data=df,
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][1])
 
f.get_legend().remove()
f.set_title("(b) INT64").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_float.csv")
f = sns.boxplot(x="Compression", y="Insert Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][0])
 
f.get_legend().remove()
f.set_title("(c) FLOAT").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_double.csv")
f = sns.boxplot(x="Compression", y="Insert Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][1])
 
f.get_legend().remove()
f.set_title("(d) DOUBLE").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
lines, labels = ax_arr[0][1].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=30,ncol=4)

plt.show()
fig.savefig("insert_time.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("insert_time.png", dpi = 400,bbox_inches='tight')


# 画查询时间的图


fig, ax_arr = plt.subplots(2,2, figsize=(20,20))
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.25)

df = pd.read_csv("result_int.csv")
f = sns.boxplot(x="Compression", y="Select Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][0])
 
f.get_legend().remove()
f.set_title("(a) INT32").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_long.csv")
f = sns.boxplot(x="Compression", y="Select Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[0][1])
 
f.get_legend().remove()
f.set_title("(b) IN64").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_float.csv")
f = sns.boxplot(x="Compression", y="Select Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][0])
 
f.get_legend().remove()
f.set_title("(c) FLOAT").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

df = pd.read_csv("result_double.csv")
f = sns.boxplot(x="Compression", y="Select Time",
            hue="Encoding", palette=palette,
            data=df,            
            hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
            order=["NONE","SNAPPY","LZ4","GZIP"],
            ax = ax_arr[1][1])
 
f.get_legend().remove()
f.set_title("(d) DOUBLE").set_fontsize(30)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)

lines, labels = ax_arr[0][1].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=30,ncol=4)

plt.show()
fig.savefig("select_time.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("select_time.png", dpi = 400,bbox_inches='tight')
