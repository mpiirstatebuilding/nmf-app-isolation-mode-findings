package esa.mo.nmf.apps;

import esa.mo.nmf.MCRegistration.RegistrationMode;
import esa.mo.nmf.MCRegistration;
import esa.mo.nmf.SimpleMonitorAndControlAdapter;
import esa.mo.nmf.nanosatmoconnector.NanoSatMOConnectorImpl;
import java.io.Serializable;
import org.ccsds.moims.mo.mal.structures.*;
import org.ccsds.moims.mo.mc.structures.*;

/**
 * Finding 1 fixture ("package B") -- deliberately different content from the
 * SDK's DemoHelloWorldSimple (different var text, different class name/main
 * class) while declaring the SAME info.name (hello-world) at a different
 * info.version, to test whether install() distinguishes them. See
 * findings/01-linux-userspace-account-takeover/README.md.
 */
public class DemoHelloWorldSimpleV2 {

    private final NanoSatMOConnectorImpl connector = new NanoSatMOConnectorImpl();
    private static final String PARAMETER_NAME = "a-parameter";
    private static final String PARAMETER_DESCRIPTION = "Package B's parameter -- not package A's!";
    private String var = "Hello from package B!";

    public DemoHelloWorldSimpleV2() {
        connector.init(new MCAdapterSimpleV2());
    }

    public static void main(final String[] args) throws Exception {
        DemoHelloWorldSimpleV2 demo = new DemoHelloWorldSimpleV2();
    }

    public class MCAdapterSimpleV2 extends SimpleMonitorAndControlAdapter {
        public MCAdapterSimpleV2() {
        }

        @Override
        public void initialRegistrations(MCRegistration registrationObject) {
            registrationObject.setMode(RegistrationMode.DONT_UPDATE_IF_EXISTS);

            final ParameterDefinitionList defs = new ParameterDefinitionList();
            defs.add(new ParameterDefinition(
                    new Identifier(PARAMETER_NAME),
                    PARAMETER_DESCRIPTION, AttributeType.STRING, "",
                    false, new Duration(3), null, null, false));
            registrationObject.registerParameters(defs);
        }

        @Override
        public Serializable onGetValueSimple(String name) {
            return PARAMETER_NAME.equals(name) ? var : null;
        }

        @Override
        public boolean onSetValueSimple(String name, Serializable value) {
            if (PARAMETER_NAME.equals(name)) {
                var = value.toString();
                return true;
            }
            return false;
        }

        @Override
        public boolean actionArrivedSimple(String name, Serializable[] values, Long executionId) {
            return false;
        }
    }
}
