set terminal svg dynamic
set title 'Histogram Row Sizes' font ",36"
set key font ",5"
set ylabel 'Log10'
set logscale y
set autoscale
set style data histograms
set style fill solid
set style histogram clustered gap 1
# This xtics list needs to match what is up in the code.
set xtics axis out rotate by 45 right ("" -1, "<1" 0, "<5" 1, "<10" 2, \
 "<15" 3, "<20"  4, "<25" 5, "<100" 6, \
 "<1k" 7, "<5k" 8, "<10k" 9, "<20k" 10, \
 "<50k" 11, "<100k" 12, \
 "<1M" 13, ">=1M" 14, "" 15)
plot \
"table.rowSize.histograms.7171879522414641440.gnuplotdata" title "table regions=589, duration=PT38.262S, N=35751, min=1136.0, max=2.38644832E8" with lines
