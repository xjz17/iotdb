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
n = 6
m = 12
matrix = [[0]*m for i in range(n)]

data = pd.read_csv("correlation2.csv")
data.columns = ['INT32','INT64','FLOAT','DOUBLE','Mean','Standard_variance','Spread','Delta_mean','Delta_variance','Delta_spread','Repeat','Increase',
                 'RAKE','RLE','TS_2DIFF','GORILLA','PLAIN','SPRINTZ','RLBE']

features = [data.INT32, data.INT64, data.FLOAT, data.DOUBLE, data.Mean, data.Standard_variance, data.Spread, data.Delta_mean, data.Delta_variance, data.Delta_spread, data.Repeat, data.Increase]
encodings = [data.TS_2DIFF,data.GORILLA, data.RAKE, data.RLE, data.RLBE, data.SPRINTZ]

for i in range(6):
    for j in range(12):
        matrix[i][j] = encodings[i].corr(features[j])
sns.set()
label_x = ['INT32','INT64','FLOAT','DOUBLE','Value mean','Value variance','Value spread','Delta mean','Delta variance','Delta spread','Repeat','Increase',]
label_y = ['TS_2DIFF','GORILLA','RAKE','RLE','RLBE','SPRINTZ']
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

sns.lineplot