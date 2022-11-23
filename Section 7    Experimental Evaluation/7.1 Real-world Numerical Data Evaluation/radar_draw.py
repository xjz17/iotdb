from turtle import color

import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
# 用于正常显示中文
# plt.rcParams['font.sans-serif'] = 'SimHei'
#用于正常显示符号
# plt.rcParams['axes.unicode_minus'] = False

plt.rcParams['pdf.fonttype'] = 42
plt.rcParams['ps.fonttype'] = 42

# plt.style.use('ggplot')
# palette=["#a6cee3", "#b2df8a","#fdbf6f","#cab2d6", "#1f78b4","#33a02c"]
palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
#["#1178b4", "#33a02c","#ff7f00","#6a3d9a","#fb9a99", "#fdbf6f"]
fig=plt.figure(figsize=(20, 20))
ax = fig.add_subplot(221, polar=True)
fmri = pd.read_csv("gzip_avg.csv")
# fmri = fmri.sort_values('Compress ')
# feature = ['Algorithm','Compress Time','Decompress Time','Decoding Time','Compression Ratio','Encoding Time']
feature = ['ET','DT','CT','UT','CR','ET']
# print(fmri)
angles=np.linspace(0, 2*np.pi,5, endpoint=False)
angles=np.concatenate((angles,[angles[0]])) 
num_encoding = 7
for i in range(0,num_encoding):
    # print(i)
    values=np.array(fmri.iloc[i,1:num_encoding-1]).tolist()
    # print(values)
    values=np.concatenate([values,[values[0]]])
    # print(values)
 
    ax.plot(angles, values, 'o-', linewidth=4,label=fmri.iloc[i,0],color=palette[i])
    ax.fill(angles, values, alpha=0)
 
ax.set_thetagrids(angles * 180/np.pi, feature)
ax.set_ylim(-0.2,1)
ax.tick_params(labelsize = 30)
ax.xaxis.label.set_size(30)
ax.yaxis.label.set_size(30)
ax.set_title('(a)GZIP').set_fontsize(30)
plt.legend(loc='best')
ax.get_legend().remove()
ax.set_facecolor('w')
# set grid color
ax.grid(color="silver")
ax.set_xticklabels(feature,color='black')
ax.grid(True)


fmri = pd.read_csv("lz4_avg.csv")
ax = fig.add_subplot(222, polar=True)

angles=np.linspace(0, 2*np.pi,5, endpoint=False)
angles=np.concatenate((angles,[angles[0]])) 

for i in range(0,num_encoding):
    values=np.array(fmri.iloc[i,1:num_encoding-1]).tolist()
    values=np.concatenate([values,[values[0]]])
 
    ax.plot(angles, values, 'o-', linewidth=4,label=fmri.iloc[i,0],color=palette[i])
    ax.fill(angles, values, alpha=0)
 
ax.set_thetagrids(angles * 180/np.pi, feature)
ax.set_ylim(-0.2,1)
ax.tick_params(labelsize = 30)
ax.xaxis.label.set_size(30)
ax.yaxis.label.set_size(30)
ax.set_title('(b)LZ4').set_fontsize(30)
plt.legend(loc='best')
ax.get_legend().remove()
ax.set_facecolor('w')
# set grid color
ax.grid(color="silver")
ax.set_xticklabels(feature,color='black')
ax.grid(True)


fmri = pd.read_csv("snappy_avg.csv")
ax = fig.add_subplot(223, polar=True)

angles=np.linspace(0, 2*np.pi,5, endpoint=False)
angles=np.concatenate((angles,[angles[0]])) 

for i in range(0,num_encoding):
    values=np.array(fmri.iloc[i,1:num_encoding-1]).tolist()
    values=np.concatenate([values,[values[0]]])
 
    ax.plot(angles, values, 'o-', linewidth=4,label=fmri.iloc[i,0],color=palette[i])
    ax.fill(angles, values, alpha=0)
 
ax.set_thetagrids(angles * 180/np.pi, feature)
ax.set_ylim(-0.2,1)
ax.tick_params(labelsize = 30)
ax.xaxis.label.set_size(30)
ax.yaxis.label.set_size(30)
ax.set_title('(c)SNAPPY').set_fontsize(30)
plt.legend(loc='best')
ax.get_legend().remove()

ax.set_facecolor('w')
# set grid color
ax.grid(color="silver")
ax.set_xticklabels(feature,color='black')
ax.grid(True)

fmri = pd.read_csv("uncompressed_avg.csv")
ax = fig.add_subplot(224, polar=True)

angles=np.linspace(0, 2*np.pi,5, endpoint=False)
angles=np.concatenate((angles,[angles[0]])) 

for i in range(0,num_encoding):
    values=np.array(fmri.iloc[i,1:num_encoding-1]).tolist()
    values=np.concatenate([values,[values[0]]])
 
    ax.plot(angles, values, 'o-', linewidth=4,label=fmri.iloc[i,0],color=palette[i])
    ax.fill(angles, values, alpha=0)
 
ax.set_thetagrids(angles * 180/np.pi, feature)
ax.set_ylim(-0.2,1)
ax.tick_params(labelsize = 30)
ax.xaxis.label.set_size(30)
ax.yaxis.label.set_size(30)
ax.set_title('(d)NONE').set_fontsize(30)
plt.legend(loc='best')
ax.get_legend().remove()

ax.set_facecolor('w')
# set grid color
ax.grid(color="silver")
ax.set_xticklabels(feature,color='black')
ax.grid(True)

lines, labels = ax.get_legend_handles_labels()
# order = ["TS_2DIFF","GORILLA","RLE","RLBE","SPRINTZ","PLAIN"]
# labels = labels[order]
fig.legend(lines, labels, loc = 'upper center', fontsize=30,ncol=4)

fig.savefig("radar.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("radar.png", dpi = 400,bbox_inches='tight')
# radar of trade-off between time cost and compression ratio


# fmri = pd.read_csv("snappy_avg.csv")
# fig1=plt.figure(figsize=(30, 10))
# ax = fig1.add_subplot(111, polar=True)

# angles=np.linspace(0, 2*np.pi,5, endpoint=False)
# angles=np.concatenate((angles,[angles[0]])) 

# for i in range(0,6):
#     values=np.array(fmri.iloc[i,1:]).tolist()
#     values=np.concatenate([values,[values[0]]])
 
#     ax.plot(angles, values, 'o-', linewidth=2,label=fmri.iloc[i,0])
#     ax.fill(angles, values, alpha=0.1)
 
# ax.set_thetagrids(angles * 180/np.pi, feature)
# ax.set_ylim(0,1)
# ax.tick_params(labelsize = 30)
# ax.xaxis.label.set_size(30)
# ax.yaxis.label.set_size(30)
# ax.set_title('(c)SNAPPY').set_fontsize(30)
# plt.legend(loc='best')
# ax.get_legend().remove()
# ax.grid(True)

# fig1.savefig("radar2.eps",format='eps',dpi = 400,bbox_inches='tight')
# fig1.savefig("radar2.png", dpi = 400,bbox_inches='tight')