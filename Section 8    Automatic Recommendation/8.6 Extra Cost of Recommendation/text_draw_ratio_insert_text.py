import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
plt.style.use('ggplot')
import matplotlib
matplotlib.rcParams['font.sans-serif'] = ['SimHei']
matplotlib.rcParams['axes.unicode_minus']=False
sns.set_theme(style="ticks", palette="pastel")

fig, ax_arr = plt.subplots(1,3, figsize=(21,7))
my_palette=["#1178b4", "#33a02c"]
# my_palette=["#1178b4", "#33a02c","#e31a1c", "#ff7f00"]
fig.subplots_adjust(hspace=0.22)
fig.subplots_adjust(wspace=0.45)

fmri = pd.read_csv("text_draw_ratio_insert_text.py.csv")
f = sns.lineplot(x="Size",y="Time Cost",hue="Time type",
                       markers=['o','o'],err_style=None,style="Time type",dashes=False,
                       palette=my_palette,data=fmri[fmri["Dataset"]=="Incident Event Log"],ax=ax_arr[0],size='Time type',sizes=[5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(a) Incident Event Log")
f_title.set_fontsize(30)
f.set_xlabel("Number of strings")
f.set_ylabel("Time Cost(s)")
# f.set_ylim(0,0.80)
# f.set_ylim(0,0.5)


f = sns.lineplot(x="Size",y="Time Cost",hue="Time type",
                       markers=['o','o'],err_style=None,style="Time type",dashes=False,
                       palette=my_palette,data=fmri[fmri["Dataset"]=="Web Log"],ax=ax_arr[1],size='Time type',sizes=[5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f_title = f.set_title("(b) Web Log")
f.set_xlabel("Number of strings")
f.set_ylabel("Time Cost(s)")
f_title.set_fontsize(30)
# f.set_ylim(0,0.5)


f = sns.lineplot(x="Size",y="Time Cost",hue="Time type",
                       markers=['o','o'],err_style=None,style="Time type",dashes=False,
                       palette=my_palette,data=fmri[fmri["Dataset"]=="CW-AIOps"],ax=ax_arr[2],size='Time type',sizes=[5,5])
f.get_legend().remove()
f.tick_params(labelsize = 30) 
f.xaxis.label.set_size(30)
f.yaxis.label.set_size(30)
f.set_title("(c) CW-AIOps").set_fontsize(30)
f.set_xlabel("Number of strings")
f.set_ylabel("Time Cost(s)")
# f.set_ylim(0,0.80)


lines, labels = ax_arr[0].get_legend_handles_labels()
# laebls = ["Data Feature Collection Time","Insert Time"]
fig.legend(lines, labels, loc = 'upper center', bbox_to_anchor=(0.5,1.1),fontsize=30,ncol=2)
plt.show()
fig.savefig("data_extraction_3.eps",format='eps',dpi = 400,bbox_inches='tight')
fig.savefig("data_extraction_3.png", dpi = 400,bbox_inches='tight')

