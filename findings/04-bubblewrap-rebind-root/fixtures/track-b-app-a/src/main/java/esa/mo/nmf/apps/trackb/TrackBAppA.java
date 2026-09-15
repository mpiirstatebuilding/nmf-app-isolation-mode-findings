package esa.mo.nmf.apps.trackb;

import esa.mo.nmf.AppStorage;
import esa.mo.nmf.MCRegistration;
import esa.mo.nmf.SimpleMonitorAndControlAdapter;
import esa.mo.nmf.nanosatmoconnector.NanoSatMOConnectorImpl;
import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;

/**
 * Track B fixture, "App A" -- writes a marker file into its own storage
 * once connected, for App B (a separate module) to try to read/write/
 * delete. See findings/04-bubblewrap-rebind-root/README.md.
 */
public class TrackBAppA {

    private final NanoSatMOConnectorImpl connector = new NanoSatMOConnectorImpl();

    public TrackBAppA() throws Exception {
        connector.init(new SimpleMonitorAndControlAdapter() {
            @Override
            public void initialRegistrations(MCRegistration registrationObject) {
            }

            @Override
            public Serializable onGetValueSimple(String name) {
                return null;
            }

            @Override
            public boolean onSetValueSimple(String name, Serializable value) {
                return false;
            }

            @Override
            public boolean actionArrivedSimple(String name, Serializable[] values, Long executionId) {
                return false;
            }
        });

        // AppStorage.getAppCacheDir() needs HelperMisc.PROP_MO_APP_NAME, which
        // connector.init() sets -- must run after init(), not in the adapter's
        // own constructor (evaluated before init() is called).
        File marker = new File(AppStorage.getAppCacheDir(), "marker.txt");
        try (FileWriter fw = new FileWriter(marker)) {
            fw.write("secret-from-A\n");
        }
        System.out.println("TrackBAppA: wrote marker at " + marker.getAbsolutePath());
    }

    public static void main(final String[] args) throws Exception {
        new TrackBAppA();
    }
}
