package org.apache.iotdb.tsfile.encoding;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Whitelist of CSV basenames under {@code ElfTestData_camel} used by encoding benchmarks. Keep in
 * sync with encoding-block README / fig10_vary_all_pack_size.py dataset_mapping.
 */
public final class BenchmarkDatasetFilter {

    private static final Set<String> ALLOWED_CSV = new HashSet<>();

    static {
        String[] names =
                new String[] {
                    "TH-Climate.csv",
                    "TY-Transport.csv",
                    "USGS-Earthquakes.csv",
                    "Stocks-UK.csv",
                    "PM10-dust.csv",
                    "Food-price.csv",
                    "EPM-Education.csv",
                    "Cyber-Vehicle.csv",
                    "CS-Sensors.csv",
                    "Blockchain-tr.csv"
                };
        Collections.addAll(ALLOWED_CSV, names);
    }

    private BenchmarkDatasetFilter() {}

    /** @return true if this basename should be processed (exact match, e.g. {@code Foo.csv}). */
    public static boolean includeDatasetFile(String fileName) {
        return fileName != null && ALLOWED_CSV.contains(fileName);
    }
}
