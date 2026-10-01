package local.passwordmanager.vault;

import app.keemobile.kotpass.cryptography.format.BaseKdfProvider;
import app.keemobile.kotpass.database.header.KdfParameters;

/**
 * Reuses the pinned library's KDF without duplicating cryptography. Kotpass marks
 * this provider Kotlin-internal, although it is JVM-public. Updating Kotpass
 * requires compiling and running the repository compatibility tests.
 */
final class LibraryKdfBridge {
    static byte[] transform(KdfParameters parameters, byte[] key) {
        return BaseKdfProvider.INSTANCE.transformKey(parameters, key);
    }
}
