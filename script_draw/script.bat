cd ..
cd Visualization\Fig 9    Compression ratio over all numerical datasets
python result_ratio_vis.py
cd ..\Fig 10-11   Insert time and select time over all numerical datasets
python result_time_vis.py
cd ..\Fig 12    Compression ratio and features on each datasets
python draw.py
cd ..\Fig 13    Trade-off between time and compression ratio
python radar_draw.py
cd ..\Fig 14-18    Varying data features
python draw_feature.py
cd ..\Fig 19    Performance of text encoding on real datasets
python result_ratio_text_vis.py
cd ..\Fig 20-23    Varying text features
python text_draw_feature.py
cmd