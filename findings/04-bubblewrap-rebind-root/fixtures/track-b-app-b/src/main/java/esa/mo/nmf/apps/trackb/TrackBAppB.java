package esa.mo.nmf.apps.trackb;

import esa.mo.nmf.AppStorage;
import esa.mo.nmf.MCRegistration;
import esa.mo.nmf.SimpleMonitorAndControlAdapter;
import esa.mo.nmf.nanosatmoconnector.NanoSatMOConnectorImpl;
import java.io.File;
import java.io.FileWriter;
import java.io.Serializable;
import java.nio.file.Files;

/**
 * Track B fixture, "App B" -- tries to read/write/delete App A's marker
 * file by its well-known storage path (same OS user under
 * linux-userspace/none, sandboxed under bubblewrap/docker-containers).
 * Three independently scored outcomes, written to this App's own cache
 * dir as results.txt and echoed to stdout. See
 * findings/04-bubblewrap-rebind-root/README.md.
 */
public class TrackBAppB {

    private final NanoSatMOConnectorImpl connector = new NanoSatMOConnectorImpl();

    public TrackBAppB() throws Exception {
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

        File marker = new File(System.getProperty("user.home")
                + "/.nmf-apps/track-b-app-a/cache/marker.txt");

        StringBuilder result = new StringBuilder();

        try {
            String content = new String(Files.readAllBytes(marker.toPath()));
            result.append("READ: SUCCESS content=[").append(content.trim()).append("]\n");
        } catch (Exception e) {
            result.append("READ: FAILED ").append(e).append("\n");
        }

        try {
            try (FileWriter fw = new FileWriter(marker, true)) {
                fw.write("tampered-by-B\n");
            }
            result.append("WRITE: SUCCESS\n");
        } catch (Exception e) {
            result.append("WRITE: FAILED ").append(e).append("\n");
        }

        try {
            boolean deleted = marker.delete();
            result.append("DELETE: ").append(deleted ? "SUCCESS" : "FAILED (delete() returned false)").append("\n");
        } catch (Exception e) {
            result.append("DELETE: FAILED ").append(e).append("\n");
        }

        File outFile = new File(AppStorage.getAppCacheDir(), "results.txt");
        try (FileWriter fw = new FileWriter(outFile)) {
            fw.write(result.toString());
        }
        System.out.println("TrackBAppB results:\n" + result);
    }

    public static void main(final String[] args) throws Exception {
        new TrackBAppB();
    }
}
