package esa.mo.nmf.bench;

import esa.mo.nmf.NMFConsumer;
import esa.mo.nmf.groundmoadapter.GroundMOAdapterImpl;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.ccsds.moims.mo.com.structures.Provider;
import org.ccsds.moims.mo.com.structures.ProviderList;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.MALInteractionException;
import org.ccsds.moims.mo.mal.MOErrorException;
import org.ccsds.moims.mo.mal.structures.BooleanList;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.LongList;
import org.ccsds.moims.mo.mal.structures.URI;
import org.ccsds.moims.mo.mal.helpertools.helpers.HelperMisc;
import org.ccsds.moims.mo.mal.transport.MALMessageHeader;
import org.ccsds.moims.mo.sm.appslauncher.body.ListAppResponse;
import org.ccsds.moims.mo.sm.appslauncher.consumer.AppsLauncherStub;
import org.ccsds.moims.mo.sm.packagemanagement.body.FindPackageResponse;
import org.ccsds.moims.mo.sm.packagemanagement.consumer.PackageManagementAdapter;
import org.ccsds.moims.mo.sm.packagemanagement.consumer.PackageManagementStub;

/**
 * Headless MAL consumer that calls AppsLauncher.runApp() against a running
 * NanoSat MO Supervisor — a drop-in for TEST_TRIGGER_CMD in
 * bench_isolation.sh, so isolation-mode benchmarking doesn't need a manual
 * CTT click per iteration.
 *
 * Makes the same wire calls CTT's "Run App" button makes, plus, optionally,
 * the PackageManagement install CTT's "Install Package" button makes first:
 * (0) findPackage+install(packageFileName) if given — confirmed live an App
 * sitting in packages/ is not auto-installed at boot, so listApp() won't see
 * it until this runs; (1) Directory lookup; (2) listApp(appName); (3)
 * runApp(ids). The Supervisor then dispatches to whichever
 * AppsLauncherManager* is active for the current appsIsolation build, same
 * as if CTT had triggered it.
 *
 * PackageManagement and AppsLauncher key the same App by two DIFFERENT
 * names (confirmed live), hence two separate arguments: packageFileName is
 * the literal .nmfpack filename in packages/ (e.g. "hello-world-5.0.nmfpack");
 * appName is the app name from the package's own metadata
 * (package-metadata.properties: info.name, e.g. "hello-world") — usually not
 * the same string.
 *
 * USAGE:
 *   java -jar runapp-trigger-jar-with-dependencies.jar <directoryURI> <appName> [packageFileName]
 *
 * Example (wire into bench_isolation.sh as TEST_TRIGGER_CMD):
 *   TEST_TRIGGER_CMD='java -jar /path/to/runapp-trigger-jar-with-dependencies.jar
 *       maltcp://127.0.0.1:1024/nanosat-mo-supervisor-Directory hello-world hello-world-5.0.nmfpack'
 *
 * directoryURI must point to the Supervisor's Directory service (the URI
 * printed in the Supervisor log as "Directory service URI").
 *
 * A transport.properties (and optionally provider.properties) file — the
 * same ones CTT/the CLI tool use — should be discoverable on the classpath
 * or working directory; if absent, HelperMisc.loadPropertiesFile() falls
 * back to the default maltcp/TCPIP transport, normally sufficient.
 */
public class RunAppTrigger {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar runapp-trigger-jar-with-dependencies.jar "
                    + "<directoryURI> <appName> [packageFileName]");
            System.exit(2);
        }

        final String directoryURI = args[0];
        final String appName = args[1];
        final String packageFileName = (args.length >= 3) ? args[2] : null;

        int exitCode = 1;
        try {
            exitCode = run(directoryURI, appName, packageFileName);
        } catch (Exception e) {
            System.err.println("RunAppTrigger failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.exit(exitCode);
    }

    /**
     * @return 0 on a confirmed successful runApp submit, 1 otherwise.
     */
    private static int run(String directoryURI, String appName, String packageFileName)
            throws MALException, MALInteractionException, MalformedURLException {

        // 1. Query the Supervisor's Directory service.
        //    loadPropertiesFile() must run before any consumer is created: it's
        //    the only thing registering the default maltcp transport mapping
        //    absent a transport.properties file. retrieveProvidersFromDirectory()
        //    calls a different, consumer.properties-only loader that never sets
        //    this — without this call it throws "Unknown transport factory for
        //    protocol: maltcp" (verified live). Mirrors the NMF CLI tool's
        //    BaseCommand.
        HelperMisc.loadPropertiesFile();
        ProviderList providers = NMFConsumer.retrieveProvidersFromDirectory(new URI(directoryURI));

        if (providers == null || providers.isEmpty()) {
            System.err.println("No providers found at directory URI: " + directoryURI);
            return 1;
        }

        // When directoryURI points directly at the Supervisor's own Directory
        // service there is typically exactly one provider entry (the Supervisor).
        // If somehow more are listed, find the one named "nanosat-mo-supervisor".
        Provider supervisorProvider = null;
        if (providers.size() == 1) {
            supervisorProvider = providers.get(0);
        } else {
            for (Provider p : providers) {
                if ("nanosat-mo-supervisor".equals(p.getProviderName().getValue())) {
                    supervisorProvider = p;
                    break;
                }
            }
            if (supervisorProvider == null) {
                System.err.println("Could not find 'nanosat-mo-supervisor' among "
                        + providers.size() + " providers at " + directoryURI);
                for (Provider p : providers) {
                    System.err.println("  - " + p.getProviderName());
                }
                return 1;
            }
        }

        // 2. Connect via Ground MO Adapter.
        //    Pattern mirrors the NMF CLI tool (BaseCommand.initRemoteConsumer +
        //    AppsLauncherCommands.getAppsLauncher) and the MCP adapter server.
        GroundMOAdapterImpl adapter = new GroundMOAdapterImpl(supervisorProvider);
        adapter.init();

        if (adapter.getSMServices() == null) {
            System.err.println("Supervisor at " + directoryURI + " does not expose SM services.");
            return 1;
        }

        // 0. Install the package first, if requested. Must happen before
        //    listApp() below: confirmed live that a package sitting in
        //    packages/ is not auto-installed at boot.
        if (packageFileName != null) {
            if (adapter.getSMServices().getPackageManagementService() == null) {
                System.err.println("Supervisor at " + directoryURI
                        + " does not expose the PackageManagement service.");
                return 1;
            }
            if (!ensurePackageInstalled(
                    adapter.getSMServices().getPackageManagementService().getPackageManagementStub(),
                    packageFileName)) {
                return 1;
            }
        }

        if (adapter.getSMServices().getAppsLauncherService() == null) {
            System.err.println("Supervisor at " + directoryURI
                    + " does not expose the AppsLauncher service.");
            return 1;
        }

        AppsLauncherStub stub =
                adapter.getSMServices().getAppsLauncherService().getAppsLauncherStub();

        // 3. Resolve appName -> COM object instance ID(s) via listApp() REQUEST.
        //    Pass null for category (wildcard) — same as the CLI tool's RunApp command.
        //    appName vs. packageFileName: see class javadoc.
        IdentifierList appsToSearch = new IdentifierList();
        appsToSearch.add(new Identifier(appName));
        ListAppResponse response = stub.listApp(appsToSearch, null);
        LongList appIds = response.getAppIds();

        if (appIds == null || appIds.isEmpty()) {
            System.err.println("App '" + appName + "' not found via listApp(). "
                    + "Check the app name matches exactly what the Supervisor registered "
                    + "(package-metadata.properties: info.name) — this is usually NOT the "
                    + "same string as the .nmfpack filename.");
            return 1;
        }

        // 4. Submit runApp (SUBMIT interaction — fire-and-forget, no response body).
        //    runApp() takes a LongList so that multiple apps can be started in one
        //    call; here we always pass the single ID returned for our named app.
        stub.runApp(appIds);

        System.out.println("runApp submitted OK for '" + appName
                + "' (objInstIds=" + appIds + ")");
        return 0;
    }

    /**
     * Ensures packageFileName (the literal .nmfpack filename in packages/, NOT
     * the app name) is installed, via findPackage() + install(). A no-op if
     * it's already installed.
     *
     * install(names, adapter) returns as soon as the ACK arrives, NOT after
     * the final response — confirmed live: proceeding straight to listApp()
     * after install() returned raced the actual server-side install (which
     * only completes when installResponseReceived fires) and failed with
     * "listApp() App not found" nearly every time. A CountDownLatch here
     * blocks until installResponseReceived/installResponseErrorReceived
     * actually fires, so the package is genuinely installed before this
     * method returns.
     *
     * @return true if the package is confirmed installed (or already was),
     * false on any error or timeout (already logged to stderr).
     */
    private static boolean ensurePackageInstalled(PackageManagementStub packageManagement,
            String packageFileName) throws MALInteractionException, MALException {

        IdentifierList names = new IdentifierList();
        names.add(new Identifier(packageFileName));

        FindPackageResponse findResponse;
        try {
            findResponse = packageManagement.findPackage(names);
        } catch (MALInteractionException e) {
            System.err.println("Package '" + packageFileName + "' not found via findPackage(). "
                    + "Make sure it was copied into the generated filesystem's packages/ "
                    + "directory before the Supervisor was started. (" + e.getMessage() + ")");
            return false;
        }

        boolean alreadyInstalled = findResponse.getInstalled() != null
                && !findResponse.getInstalled().isEmpty()
                && Boolean.TRUE.equals(findResponse.getInstalled().get(0));

        if (alreadyInstalled) {
            System.out.println("Package '" + packageFileName + "' already installed.");
            return true;
        }

        System.out.println("Installing package '" + packageFileName + "'...");
        final boolean[] installFailed = {false};
        final CountDownLatch responseLatch = new CountDownLatch(1);

        packageManagement.install(names, new PackageManagementAdapter() {
            @Override
            public void installAckReceived(MALMessageHeader msgHeader,
                    BooleanList integrity, Map qosProperties) {
                System.out.println("install() acknowledged for '" + packageFileName + "'.");
            }

            @Override
            public void installResponseReceived(MALMessageHeader msgHeader, Map qosProperties) {
                System.out.println("Package '" + packageFileName + "' installed.");
                responseLatch.countDown();
            }

            @Override
            public void installAckErrorReceived(MALMessageHeader msgHeader,
                    MOErrorException error, Map qosProperties) {
                System.err.println("install() ack error for '" + packageFileName + "': " + error);
                installFailed[0] = true;
                responseLatch.countDown();
            }

            @Override
            public void installResponseErrorReceived(MALMessageHeader msgHeader,
                    MOErrorException error, Map qosProperties) {
                System.err.println("install() response error for '" + packageFileName + "': " + error);
                installFailed[0] = true;
                responseLatch.countDown();
            }
        });

        try {
            if (!responseLatch.await(10, TimeUnit.SECONDS)) {
                System.err.println("install() for '" + packageFileName + "' timed out after 10s "
                        + "waiting for the final response.");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for install() to complete.");
            return false;
        }

        return !installFailed[0];
    }
}
