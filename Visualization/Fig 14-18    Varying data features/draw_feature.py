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

fig, ax_arr = plt.subplots(1,3,figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]

fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.37)

fmri = pd.read_csv("result/ratio_feature/result_mean_int.csv")
f = sns.lineplot(x="Value mean",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],style='Encoding',dashes=False,palette=my_palette
                       ,data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(a) Value Mean")
f.set_xlabel("Value Mean")
f_title.set_fontsize(30)

fmri = pd.read_csv("result/time_data_insert/mean_int.csv")
f = sns.lineplot(x="Data Mean",y="Insert Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style='Encoding',dashes=False,
                       palette=my_palette,
                       data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])

f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(b) Value Mean")
f_title.set_fontsize(30)
f.set_ylabel("Insert Time(s)")
f.set_xlabel("Value Mean")
f.set_ylim(0,0.16)


fmri = pd.read_csv("result/time_data_select/mean_int.csv")
f = sns.lineplot(x="Data Mean",y="Select Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style='Encoding',dashes=False,
                       palette=my_palette,
                       data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f.set_xlabel("Value Mean")
f_title = f.set_title("(c) Value Mean")
f_title.set_fontsize(30)
f.set_ylim(0,0.012)

lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)

fig.savefig("features_mean.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features_mean.png", dpi = 400,bbox_inches='tight')

# #-----------------------------------diffmean-------------------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.37)
fmri = pd.read_csv("result/ratio_feature/result_diffmean_int.csv")
f = sns.lineplot(x="Delta mean",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_xlabel("Delta Mean")
f_title = f.set_title("(a) Delta Mean")
f_title.set_fontsize(30)
# f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_insert/diffmean_int.csv")
f = sns.lineplot(x="Delta Mean",y="Insert Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f.set_xlabel("Delta Mean")
f_title = f.set_title("(b) Delta Mean")
f_title.set_fontsize(30)
f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_select/diffmean_int.csv")
f = sns.lineplot(x="Delta Mean",y="Select Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f.set_xlabel("Delta Mean")
f_title = f.set_title("(c) Delta Mean")
f_title.set_fontsize(30)
f.set_ylim(0,0.012)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
fig.savefig("features_diffmean.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features_diffmean.png", dpi = 400,bbox_inches='tight')

# # -----------------------------------------diffvar------------------------------------

fig, ax_arr = plt.subplots(1,3,figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.37)
fmri = pd.read_csv("result/ratio_feature/result_diffvar_int.csv")
f = sns.lineplot(x="Delta variance",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_xlabel("Delta Variance")
f_title = f.set_title("(a) Delta Variance")
f_title.set_fontsize(30)
# f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_insert/diffvar_int.csv")
f = sns.lineplot(x="Delta Variance",y="Insert Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f_title = f.set_title("(b) Delta Variance")
f_title.set_fontsize(30)
f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_select/diffvar_int.csv")
f = sns.lineplot(x="Delta Variance",y="Select Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f_title = f.set_title("(c) Delta Variance")
f_title.set_fontsize(30)
f.set_ylim(0,0.012)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
fig.savefig("features_diffvar.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features_diffvar.png", dpi = 400,bbox_inches='tight')

# # ------------------------------repeat------------------------------------------------


fig, ax_arr = plt.subplots(1,3,figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.37)
fmri = pd.read_csv("result/ratio_feature/result_repeat_int.csv")
f = sns.lineplot(x="Repeat",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                      markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                      dashes=False,palette=my_palette,
                      data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.xaxis.label.set_size(30)
f.tick_params(labelsize = 30) 
f.yaxis.label.set_size(30)
f_title = f.set_title("(a) Repeat")
f_title.set_fontsize(30)
# f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_insert/repeat_int.csv")
f = sns.lineplot(x="Repeat",y="Insert Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                      markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                      dashes=False,palette=my_palette,
                      data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.xaxis.label.set_size(30)
f.tick_params(labelsize = 30) 
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f_title = f.set_title("(b) Repeat")
f_title.set_fontsize(30)
f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_select/repeat_int.csv")
f = sns.lineplot(x="Repeat",y="Select Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                      markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                      dashes=False,palette=my_palette,
                      data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.xaxis.label.set_size(30)
f.tick_params(labelsize = 30) 
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f_title = f.set_title("(c) Repeat")
f_title.set_fontsize(30)
f.set_ylim(0,0.012)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
fig.savefig("features_repeat.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features_repeat.png", dpi = 400,bbox_inches='tight')
#  -----------------------------------------increase-------------------------------------------
fig, ax_arr = plt.subplots(1,3,figsize=(24,7))
my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00","#6a3d9a","#fb9a99", "#814a19"]
fig.subplots_adjust(hspace=0.2)
fig.subplots_adjust(wspace=0.37)
fmri = pd.read_csv("result/ratio_feature/result_increase_int.csv")
f = sns.lineplot(x="Increase",y="Compression Ratio",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[0],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(a) Increase")
f_title.set_fontsize(30)
# f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_insert/increase_int.csv")
f = sns.lineplot(x="Increase",y="Insert Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[1],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Insert Time(s)")
f_title = f.set_title("(b) Increase")
f_title.set_fontsize(30)
f.set_ylim(0,0.16)
fmri = pd.read_csv("result/time_data_select/increase_int.csv")
f = sns.lineplot(x="Increase",y="Select Time",hue="Encoding",hue_order=["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
                       markers=['o','o','o','o','o','o','o'],err_style=None,style="Encoding",
                       dashes=False,palette=my_palette,
                       data=fmri,ax=ax_arr[2],size='Encoding',sizes=[5,5,5,5,5,5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_ylabel("Select Time(s)")
f_title = f.set_title("(c) Increase")
f_title.set_fontsize(30)
f.set_ylim(0,0.012)
lines, labels = ax_arr[0].get_legend_handles_labels()
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.2),fontsize=30,ncol=4)
fig.savefig("features_increase.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("features_increase.png", dpi = 400,bbox_inches='tight')