import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
plt.style.use('ggplot')
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus']=False
matplotlib.rcParams['pdf.fonttype'] = 42
matplotlib.rcParams['ps.fonttype'] = 42
sns.set_theme(style="ticks", palette="pastel")



# ----------------------------EXPONENT--------------------------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(21,7))

my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.355)

fmri = pd.read_csv("text_feature/exponent_text_ratio_result.csv")
f = sns.lineplot(x="Exponent",y="Compression Ratio",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Compression Ratio")
f_title = f.set_title("(a) Exponent")
f_title.set_fontsize(30)

fmri = pd.read_csv("time_text_features/exponent_text_time_result.csv")
f = sns.lineplot(x="Exponent",y="Insert Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f_title = f.set_title("(b) Exponent")
f_title.set_fontsize(30)
# f.set_ylim(0,1)


f = sns.lineplot(x="Exponent",y="Select Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(c) Exponent")
f.set_ylabel("Select Time(s)")
f_title.set_fontsize(30)
# f.set_ylim(0,4)

lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)

fig.savefig("text_features_exponent.png",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("fig20.png", dpi = 400,bbox_inches='tight')
# plt.show()

# # -------------------------------Types------------------------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(21,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.355)
fmri = pd.read_csv("text_feature/types_text_ratio_result.csv")
f = sns.lineplot(x="Types",y="Compression Ratio",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_title("(a) Domain").set_fontsize(30)
f.set_ylabel("Compression Ratio")
f.set_xlabel("Domain")
fmri = pd.read_csv("time_text_features/types_text_time_result.csv")
f = sns.lineplot(x="Types",y="Insert Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_title("(b) Domain").set_fontsize(30)
f.set_ylabel("Insert Time(s)")
f.set_xlabel("Domain")
# f.set_ylim(0,1)
f = sns.lineplot(x="Types",y="Select Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f.set_title("(c) Domain").set_fontsize(30)
f.set_xlabel("Domain")
# f.set_ylim(0,4)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
# plt.show()
fig.savefig("text_features_domain.png",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("fig21.png", dpi = 400,bbox_inches='tight')

# # -----------------------------Length------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(21,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.355)
fmri = pd.read_csv("text_feature/length_text_ratio_result.csv")
f = sns.lineplot(x="Length",y="Compression Ratio",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Compression Ratio")
f_title = f.set_title("(a) Length")
f_title.set_fontsize(30)
# # f.set_ylim(0,1)
fmri = pd.read_csv("time_text_features/length_text_time_result.csv")
f = sns.lineplot(x="Length",y="Insert Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f_title = f.set_title("(b) Length")
f_title.set_fontsize(30)
# f.set_ylim(0,1)
f = sns.lineplot(x="Length",y="Select Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f_title = f.set_title("(c) Length")
f_title.set_fontsize(30)
# f.set_ylim(0,4)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center',bbox_to_anchor=(0.5,1.2), fontsize=30,ncol=4)
# plt.show()
fig.savefig("text_features_length.png",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("fig22.png", dpi = 400,bbox_inches='tight')


# # ----------------------------REPEAT----------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(21,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.355)
fmri = pd.read_csv("text_feature/repeat_text_ratio_result.csv")
f = sns.lineplot(x="Repeat",y="Compression Ratio",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Compression Ratio")
f.set_title("(a) Repeat").set_fontsize(30)
# f.set_ylim(0,0.16)
fmri = pd.read_csv("time_text_features/repeat_text_time_result.csv")
f = sns.lineplot(x="Repeat",y="Insert Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f.set_title("(b) Repeat").set_fontsize(30)
# f.set_ylim(0,1)
f = sns.lineplot(x="Repeat",y="Select Time",hue="Encoding",hue_order=["HUFFMAN","MTF","BW","DICTIONARY","RLE","AC","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",dashes=False,
                       palette=my_palette,data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
#sns.despine(offset=10, trim=True)
f.get_legend().remove()
# f.legend(loc = 'best',fontsize=7)
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
# f.set_ylabel("Select Time/s")
f.set_ylabel("Select Time(s)")
f.set_title("(c) Repeat").set_fontsize(30)
# f.set_ylim(0,4)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
# plt.show()
fig.savefig("text_features_repeat.png",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("fig23.png", dpi = 400,bbox_inches='tight')