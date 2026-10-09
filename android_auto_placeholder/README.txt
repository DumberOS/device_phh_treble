Android Auto placeholder (Apache-2.0)

This manifest-only APK reserves com.google.android.projection.gearhead as a
privileged product app. It contains no DEX, native libraries, Google assets,
activities, services or receivers. It does not implement Android Auto or fetch
anything. Users install the real app separately.

The microG product includes AndroidAutoPlaceholder, MicroGAndroidAutoOverlay,
and the adjacent privileged-permission allowlist. The GApps product retains its
existing Google prebuilt. Do not include both stubs in the same image.

Signing and updates

The placeholder uses the product's normal app signing key. The existing release
signing workflow can re-sign it; no private key or fixed local certificate digest
is added here. Frameworks/base's AndroidAutoPlaceholder helper reads the local
certificate from the factory package's trusted PackageSetting.

The framework only permits a handoff to the pinned Google AA certificates or a
verified rotation lineage granting installed-data capability. Package and shared
UID names must match, the factory APK must occupy the expected privileged product
directory, and no other package may belong to the UID. The real APK keeps its
actual signer, allowing microG to recognize it. Ordinary Google-to-Google updates
still use normal signature checks.

Restoring the local signer is only allowed when PackageManager scans the
manifest-only version-1 placeholder from the system image. An APK submitted by
an installer cannot request this reverse handoff. Reconciliation defers shared
UID signature changes until commit, and boot refreshes the disabled factory
package's certificate from its APK.

Version code 1 and the AndroidAutoPlaceholder install directory are part of this
framework contract. Change them only together with the helper and its tests.
The stub intentionally does not declare Google's custom signature permissions:
the real app declares those when installed.

The manifest must request both the privileged permissions and all permissions
granted by PermissionController's SYSTEM_AUTOMOTIVE_PROJECTION role (including
its permission sets). For this system-only role, PermissionController intersects
an updated app's requests with the factory APK's requests before granting them.
Omitting the role permissions leaves AA holding the role without its permissions;
vehicle association then throws SecurityException for
REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION. Reapplying the role cannot repair
an incomplete factory manifest. Role-only permissions are not privapp allowlist
entries. Once the corrected factory APK is installed in the ROM, the role must
be reevaluated for existing users as well.

Validation

Build in the configured ROM environment:
  DISABLE_STUB_VALIDATION=true m AndroidAutoPlaceholder services PackageManagerServiceServerTests

Run the signing-policy tests on a connected test device:
  atest PackageManagerServiceServerTests:AndroidAutoPlaceholderTest

Before release, test on a device running the rebuilt framework and placeholder:
  - Fresh boot with only the placeholder, then install the genuine AA APK/splits.
  - Verify privileged permissions and microG certificate recognition; connect DHU.
  - Reboot with real AA installed and update it to another trusted version.
  - Uninstall updates, reboot, and reinstall AA.
  - Upgrade a ROM with the previous Google stub, with and without an AA update.
  - Reject an unrelated signer, another package, and a locally signed /data APK
    submitted over Google AA (the reverse handoff is system-scan-only).

This does not install AA dependencies, change Play Store intents, modify microG,
seed Phenotype data, or grant runtime permissions and special access for users.
