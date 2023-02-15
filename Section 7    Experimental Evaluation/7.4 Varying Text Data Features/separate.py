import csv, os
from itertools import islice

if not os.path.exists("text_feature"):
    os.mkdir("text_feature")
if not os.path.exists("time_text_features"):
    os.mkdir("time_text_features")


reader = csv.reader(open("text_ratio_2.csv", "r", encoding="UTF-8"))
fout1 = open("text_feature/exponent_text_ratio_result.csv", "w", encoding="UTF-8", newline="")
fout2 = open("text_feature/length_text_ratio_result.csv", "w", encoding="UTF-8", newline="")
fout3 = open("text_feature/repeat_text_ratio_result.csv", "w", encoding="UTF-8", newline="")
fout4 = open("text_feature/types_text_ratio_result.csv", "w", encoding="UTF-8", newline="")

fout1.write("Datatype,Compression,Encoding,Exponent,Types,Length,Repeat,Compression Ratio\n")
fout2.write("Datatype,Compression,Encoding,Exponent,Types,Length,Repeat,Compression Ratio\n")
fout3.write("Datatype,Compression,Encoding,Exponent,Types,Length,Repeat,Compression Ratio\n")
fout4.write("Datatype,Compression,Encoding,Exponent,Types,Length,Repeat,Compression Ratio\n")

w1 = csv.writer(fout1)
w2 = csv.writer(fout2)
w3 = csv.writer(fout3)
w4 = csv.writer(fout4)

for ln in islice(reader, 1, None):
    if ln[0] == "Exponent":
        w1.writerow(ln)
    if ln[0] == "Length":
        w2.writerow(ln)
    if ln[0] == "Repeat":
        w3.writerow(ln)
    if ln[0] == "Class":
        w4.writerow(ln)

fout1.close()
fout2.close()
fout3.close()
fout4.close()



reader = csv.reader(open("text_time_2.csv", "r", encoding="UTF-8"))
fout1 = open("time_text_features/exponent_text_time_result.csv", "w", encoding="UTF-8", newline="")
fout2 = open("time_text_features/length_text_time_result.csv", "w", encoding="UTF-8", newline="")
fout3 = open("time_text_features/repeat_text_time_result.csv", "w", encoding="UTF-8", newline="")
fout4 = open("time_text_features/types_text_time_result.csv", "w", encoding="UTF-8", newline="")

fout1.write("DataFile,Compression,Encoding,Exponent,Types,Length,Repeat,Select Time,Insert Time\n")
fout2.write("DataFile,Compression,Encoding,Exponent,Types,Length,Repeat,Select Time,Insert Time\n")
fout3.write("DataFile,Compression,Encoding,Exponent,Types,Length,Repeat,Select Time,Insert Time\n")
fout4.write("DataFile,Compression,Encoding,Exponent,Types,Length,Repeat,Select Time,Insert Time\n")

w1 = csv.writer(fout1)
w2 = csv.writer(fout2)
w3 = csv.writer(fout3)
w4 = csv.writer(fout4)

for ln in islice(reader, 1, None):
    if ln[0] == "Exponent":
        w1.writerow(ln)
    if ln[0] == "Length":
        w2.writerow(ln)
    if ln[0] == "Repeat":
        w3.writerow(ln)
    if ln[0] == "Class":
        w4.writerow(ln)

fout1.close()
fout2.close()
fout3.close()
fout4.close()




