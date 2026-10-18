package esa.mo.nmf.apps.pwned;

/**
 * Finding 2 fixture: content is irrelevant -- only the built package's
 * metadata (info.name, set via the pom's generate-nmf-package
 * configuration, not this class) matters for this finding. Never expected
 * to actually run. See findings/02-path-traversal-install/README.md.
 */
public class NoopApp {
    public static void main(String[] args) {
    }
}
