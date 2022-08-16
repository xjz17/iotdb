import csv
from distutils.fancy_getopt import wrap_text
from itertools import islice

reader = csv.reader(open("result_compression_ratio_for_each_dataset.csv"))
writer = csv.writer(open("ready_to_draw.csv", "w", encoding="UTF-8", newline=''))


writer.writerow(['DataSet','feature','value'])

f = ["Value mean","Standard variance","Value spread","Delta mean","Delta variance","Delta spread","Repeat","Increase"]

for ln in islice(reader, 1, None):
    for i in range(8):
        writer.writerow([ln[0],f[i],ln[i+2]])
