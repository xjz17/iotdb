import csv
import os, re

Encoding = ["RAKE","RLE","TS_2DIFF","GORILLA","PLAIN","SPRINTZ","RLBE"]

writer = csv.writer(open("dataset_result_test1.csv",'w',encoding='UTF-8',newline=""))
writer.writerow(['DataSet','Encoding','Mean','Standard_variance','Spread','Delta_mean','Delta_variance','Delta_spread','Repeat','Increase','Compression_Ratio'])

filelist = os.listdir(".")
for file in filelist:
    if re.match(".+\\.csv",file,flags=0) and file != "dataset_result_test.csv":
        print(file)
    for encoding in Encoding:
        Average = 0
        Standard_Variance = 0
        Spread = 0
        Average_diff = 0
        Variance_diff = 0
        Spread_diff = 0
        Repeat = 0
        Sortedness = 0
        Compression_ratio = 0

        count = 0

        reader = csv.reader(open(file))
        for ln in reader:
            if ln[10] == encoding:
                Average += float(ln[2])
                Standard_Variance += float(ln[3])
                Spread += float(ln[4])
                Average_diff += float(ln[5])
                Variance_diff += float(ln[6])
                Spread_diff += float(ln[7])
                Repeat += float(ln[8])
                Sortedness += float(ln[9])
                Compression_ratio += float(ln[11])
                count += 1
        Average /= count
        Standard_Variance /= count
        Spread /= count
        Average_diff /= count
        Variance_diff /= count
        Spread_diff /= count
        Sortedness /= count
        Repeat  /= count
        Compression_ratio /= count
        writer.writerow([file,encoding,Average,Standard_Variance,Spread,Average_diff,Variance_diff,
                            Spread_diff,Repeat, Sortedness,Compression_ratio])
                    #print(Repeat_words)