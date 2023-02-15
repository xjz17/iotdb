import csv
import pandas
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import matplotlib
plt.style.use('ggplot')
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus']=False
sns.set_theme(style="ticks", palette="vlag")

fig, ax_arr = plt.subplots(1,1,figsize=(5,3))
my_palette = sns.color_palette("Set1",n_colors=7)
fig.subplots_adjust(hspace=0.22)
fig.subplots_adjust(wspace=0.20)

data = pd.read_csv("correlation2_text.csv")
data.columns = ['Exponent','Types','Length','Repeat','RLE','PLAIN','HUFFMAN','DICTIONARY','MTF','BW','AC']

# print(data)

features = [data.Exponent,data.Types,data.Length,data.Repeat]
encodings = [data.HUFFMAN,data.MTF,data.BW,data.DICTIONARY,data.RLE,data.AC]

n = len(encodings)
m = len(features)
matrix = [[0]*m for i in range(n)]
for i in range(len(encodings)):
    for j in range(len(features)):
        print(i,j)
        matrix[i][j] = encodings[i].corr(features[j])
sns.set()
label_x = ['Exponent','Types','Length','Repeat']
label_y = ['HUFFMAN','MTF','BW','DICTIONARY','RLE','AC']
heatmap = sns.heatmap(matrix, xticklabels = label_x, yticklabels = label_y, cmap='vlag_r', vmin = -0.5, vmax = 0.5, ax = ax_arr)

heatmap.set_xticklabels(heatmap.get_xticklabels(), rotation=60)
heatmap.set_yticklabels(heatmap.get_yticklabels(), rotation=0)
plt.setp(label_x)
print(matrix)
lines, labels = ax_arr.get_legend_handles_labels()
#fig.legend(lines)
#plt.show()
fig.savefig("features-encoding.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features-encoding.png", dpi = 400,bbox_inches='tight')

# sns.lineplotcd 