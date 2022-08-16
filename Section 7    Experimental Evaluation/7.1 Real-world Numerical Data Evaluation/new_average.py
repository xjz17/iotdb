from nntplib import NNTP_PORT
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
# plt.style.use('ggplot')
import matplotlib
# matplotlib.rcParams['font.sans-serif'] = ['SimHei']
# matplotlib.rcParams['axes.unicode_minus']=False
# sns.set_theme(style="ticks", palette="pastel")
# df_mapping = pd.DataFrame({
#     'Encoding ': ["TS_2DIFF","GORILLA","RAKE","RLE","RLBE","SPRINTZ","PLAIN"],
# })["GORILLA","PLAIN","RAKE","RLBE","RLE","SPRINTZ","TS_2DIFF"],

plt.switch_backend('agg')
plt.rcParams['pdf.use14corefonts'] = True
font = {'size': 18, 'family': 'Helvetica'}
plt.rc('font', **font)

# sort_mapping = df_mapping.reset_index().set_index('Encoding ')
encoding_sort = [1,6,2,4,3,5,0]
# df['size_num'] = df['size'].map(sort_mapping['index'])

fmri = pd.read_csv("radar/compressedSpeedResult.csv",header=0)
# print(fmri.sample(3))
# print(fmri.columns.values)groupby(fmri.iloc[0])
order=["Encoding Time","Decoding Time","Compress Time","Uncompress Time","Compression Ratio"]
order1=["Encoding Time","Decoding Time","Compress Time","Uncompress Time","Compression Ratio"]
nnr = pd.read_csv("radar/new_new_result.csv",header=0)
# nnr = nnr.drop(nnr[nnr["Encoding"]=="RAKE"].index)
gzip_nnr = nnr[nnr["Compression"]=="GZIP"]
gzip_nnr_means = gzip_nnr.groupby(['Encoding'])["Compression Ratio"].mean()
#gzip_nnr_means =1/gzip_nnr_means
gzip_nnr_nor=(gzip_nnr_means-gzip_nnr_means.min())/(gzip_nnr_means.max()-gzip_nnr_means.min())

# gzip_nnr_nor=gzip_nnr_means/gzip_nnr_means.max()

gzip_nnr_nor.to_csv("gzip_avg_cr.csv",index=True)
# print(gzip_nnr_means)s
gzip=fmri[fmri["Compress"]=="GZIP"]
gzip_means = gzip.groupby(['Encoding'])["Encoding Time","Compress Time","Uncompress Time","Decoding Time","Compression Ratio"].mean()
# print(gzip_means)

gzip_means["Compression Ratio"] = gzip_nnr_nor.to_numpy()
# gzip_means["Compression Ratio"] = 1/gzip_means["Compression Ratio"]
gzip_nor=(gzip_means-gzip_means.min())/(gzip_means.max()-gzip_means.min())
# gzip_nor=gzip_means/gzip_means.max()
gzip_nor["Encoding Time"] = 1-gzip_nor["Encoding Time"]
gzip_nor["Compress Time"] = 1-gzip_nor["Compress Time"]
gzip_nor["Uncompress Time"] = 1-gzip_nor["Uncompress Time"]
gzip_nor["Decoding Time"] = 1-gzip_nor["Decoding Time"]
gzip_nor["Compression Ratio"] = 1-gzip_nor["Compression Ratio"]

gzip_nor = gzip_nor[order]
# print(gzip_nor)
gzip_nor=gzip_nor.sort_values('Encoding')
gzip_nor["Encoding_sort"] = encoding_sort
gzip_nor=gzip_nor.sort_values('Encoding_sort')
gzip_nor.to_csv("gzip_avg.csv",index=True)


lz4_nnr = nnr[nnr["Compression"]=="LZ4"]
lz4_nnr_means = lz4_nnr.groupby(['Encoding'])["Compression Ratio"].mean()
#lz4_nnr_means = 1/lz4_nnr_means
lz4_nnr_nor=(lz4_nnr_means-lz4_nnr_means.min())/(lz4_nnr_means.max()-lz4_nnr_means.min())
# lz4_nnr_nor=lz4_nnr_means/lz4_nnr_means.max()
lz4_nnr_nor.to_csv("lz4_avg_cr.csv",index=True)
lz4=fmri[fmri["Compress"]=="LZ4"]
lz4_means = lz4.groupby(['Encoding'])["Encoding Time","Compress Time","Uncompress Time","Decoding Time","Compression Ratio"].mean()

lz4_means["Compression Ratio"] = lz4_nnr_nor.to_numpy()
# lz4_means["Compression Ratio"] = 1/lz4_means["Compression Ratio"]
# lz4_nor=lz4_means/lz4_means.max()
lz4_nor=(lz4_means-lz4_means.min())/(lz4_means.max()-lz4_means.min())
lz4_nor["Encoding Time"] = 1-lz4_nor["Encoding Time"]
lz4_nor["Compress Time"] = 1-lz4_nor["Compress Time"]
lz4_nor["Uncompress Time"] = 1-lz4_nor["Uncompress Time"]
lz4_nor["Decoding Time"] = 1-lz4_nor["Decoding Time"]
lz4_nor["Compression Ratio"] = 1-lz4_nor["Compression Ratio"]

lz4_nor = lz4_nor[order]
lz4_nor=lz4_nor.sort_values('Encoding')
lz4_nor["Encoding_sort"] = encoding_sort
lz4_nor=lz4_nor.sort_values('Encoding_sort')
lz4_nor.to_csv("lz4_avg.csv",index=True)

snappy_nnr = nnr[nnr["Compression"]=="SNAPPY"]
snappy_nnr_means = snappy_nnr.groupby(['Encoding'])["Compression Ratio"].mean()
#snappy_nnr_means = 1/snappy_nnr_means
snappy_nnr_nor=(snappy_nnr_means-snappy_nnr_means.min())/(snappy_nnr_means.max()-snappy_nnr_means.min())
# snappy_nnr_nor=snappy_nnr_means/snappy_nnr_means.max()
snappy_nnr_nor.to_csv("snappy_avg_cr.csv",index=True)
snappy=fmri[fmri["Compress"]=="SNAPPY"]
snappy_means = snappy.groupby(['Encoding'])["Encoding Time","Compress Time","Uncompress Time","Decoding Time","Compression Ratio"].mean()

# print(type(snappy_nnr_nor))
snappy_means["Compression Ratio"] = snappy_nnr_nor.to_numpy()
# print(snappy_means["Compression Ratio"])
# snappy_means["Compression Ratio"] = 1/snappy_means["Compression Ratio"]
# snappy_nor=snappy_means/snappy_means.max()
snappy_nor=(snappy_means-snappy_means.min())/(snappy_means.max()-snappy_means.min())
snappy_nor["Encoding Time"] = 1-snappy_nor["Encoding Time"]
snappy_nor["Compress Time"] = 1-snappy_nor["Compress Time"]
snappy_nor["Uncompress Time"] = 1-snappy_nor["Uncompress Time"]
snappy_nor["Decoding Time"] = 1-snappy_nor["Decoding Time"]
snappy_nor["Compression Ratio"] = 1-snappy_nor["Compression Ratio"]

snappy_nor = snappy_nor[order]
snappy_nor=snappy_nor.sort_values('Encoding')
snappy_nor["Encoding_sort"] = encoding_sort
snappy_nor=snappy_nor.sort_values('Encoding_sort')
snappy_nor.to_csv("snappy_avg.csv",index=True)
# print(means)
# means["Encoding Time "] = 1/means["Encoding Time "]
# means["Compress Time "] = 1/means["Compress Time "]
# means["Uncompress Time "] = 1/means["Uncompress Time "]
# means["Decoding Time "] = 1/means["Decoding Time "]
# means["Compression Ratio"] = 1/means["Compression Ratio"]

# # print(means["Decoding Time "])
# means_nor=(means-means.min())/(means.max()-means.min())
# # print(means_nor)
# means_nor.to_csv("avg.csv",index=True)

uncompressed_nnr = nnr[nnr["Compression"]=="NONE"]
uncompressed_nnr_means = uncompressed_nnr.groupby(['Encoding'])["Compression Ratio"].mean()
#uncompressed_nnr_means = 1/uncompressed_nnr_means
uncompressed_nnr_nor=(uncompressed_nnr_means-uncompressed_nnr_means.min())/(uncompressed_nnr_means.max()-uncompressed_nnr_means.min())
# uncompressed_nnr_nor=uncompressed_nnr_means/uncompressed_nnr_means.max()
uncompressed_nnr_nor.to_csv("uncompressed_avg_cr.csv",index=True)
fmri = pd.read_csv("radar/new_UncompressedSpeedResult.csv",header=0)
uncompressed=fmri[fmri["Compress"]=="UNCOMPRESSED"]
uncompressed_means = uncompressed.groupby(['Encoding'])["Encoding Time","Compress Time","Uncompress Time","Decoding Time","Compression Ratio"].mean()

uncompressed_means["Compression Ratio"] = uncompressed_nnr_nor.to_numpy()
# uncompressed_means["Compression Ratio"] = 1/uncompressed_means["Compression Ratio"]
uncompressed_nor=(uncompressed_means-uncompressed_means.min())/(uncompressed_means.max()-uncompressed_means.min())
# uncompressed_nor=uncompressed_means/uncompressed_means.max()
uncompressed_nor["Encoding Time"] = 1-uncompressed_nor["Encoding Time"]
uncompressed_nor["Compress Time"] = 1-uncompressed_nor["Compress Time"]
uncompressed_nor["Uncompress Time"] = 1-uncompressed_nor["Uncompress Time"]
uncompressed_nor["Decoding Time"] = 1-uncompressed_nor["Decoding Time"]
uncompressed_nor["Compression Ratio"] = 1-uncompressed_nor["Compression Ratio"]

uncompressed_nor = uncompressed_nor[order1]
uncompressed_nor=uncompressed_nor.sort_values('Encoding')
uncompressed_nor["Encoding_sort"] = encoding_sort
uncompressed_nor=uncompressed_nor.sort_values('Encoding_sort')
uncompressed_nor.to_csv("uncompressed_avg.csv",index=True)