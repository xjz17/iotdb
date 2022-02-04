import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
plt.style.use('ggplot')
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus']=False
sns.set_theme(style="ticks", palette="pastel")

fig, ax_arr = plt.subplots(2,2,figsize=(20,20))
my_palette = ["#ff4040", "orange","pink", "green","#00ff79", "blue","purple"]
fig.subplots_adjust(hspace=0.22)
fig.subplots_adjust(wspace=0.20)

fmri = pd.read_csv("result_diffvar_int.csv")
f = sns.lineplot(x="Delta variance",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],edgecolor='none',style="Encoding",dashes=False,palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],data=fmri,ax=ax_arr[0][0],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(a) INT32")
f_title.set_fontsize(30)

fmri = pd.read_csv("result_diffvar_long.csv")
f = sns.lineplot(x="Delta variance",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],style="Encoding",dashes=False,palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],data=fmri,ax=ax_arr[0][1],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_title("(b) INT64").set_fontsize(30)

fmri = pd.read_csv("result_diffvar_float.csv")
f = sns.lineplot(x="Delta variance",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                      markers=['o','o','o','o','o','o','o'],style="Encoding",dashes=False, palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],data=fmri,ax=ax_arr[1][0],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(c) FLOAT")
f_title.set_fontsize(30)

fmri = pd.read_csv("result_diffvar_double.csv")
f = sns.lineplot(x="Delta variance",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],style="Encoding",dashes=False,palette=["#a6cee3", "#b2df8a","#fb9a99", "#fdbf6f","#cab2d6", "#1f78b4","#33a02c"],data=fmri,ax=ax_arr[1][1],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_title("(d) DOUBLE").set_fontsize(30)

lines, labels = ax_arr[0][1].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', fontsize=30,ncol=4)
plt.show()
fig.savefig("diff_var_compression_ratio.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("diff_var_compression_ratio.png", dpi = 400,bbox_inches='tight')

