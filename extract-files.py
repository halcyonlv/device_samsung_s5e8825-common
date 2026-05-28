#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

namespace_imports = [
    'device/samsung/s5e8825-common',
    'hardware/samsung',
    'hardware/samsung_slsi-linaro/exynos',
    'hardware/samsung_slsi-linaro/graphics',
    'hardware/samsung_slsi-linaro/interfaces',
]


def lib_fixup_vendor_suffix(lib: str, partition: str, *args, **kwargs):
    return f'{lib}_{partition}' if partition == 'vendor' else None


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
    'libuuid': lib_fixup_vendor_suffix,
}  # fmt: skip

blob_fixups: blob_fixups_user_type = {
    # Audio
    (
        'vendor/lib64/libaudioparamupdate.so',
        'vendor/lib64/libaboxpcmdump.so',
        'vendor/lib64/libaudioproxy2.so',
    ): blob_fixup()
        .add_needed('libaudioroute.s5e8825.so')
        .add_needed('libtinyalsa.s5e8825.so'),
    'vendor/lib64/hw/audio.primary.s5e8825.so': blob_fixup()
        .replace_needed('libaudioroute.so', 'libaudioroute.s5e8825.so')
        .replace_needed('libtinyalsa.so', 'libtinyalsa.s5e8825.so')
        # FM Radio fix
        .sig_replace('E1 00 00 54 A9 02 80 52 29 80 01 B9 08 65 40 B9',
                     '1F 20 03 D5 A9 02 80 52 29 80 01 B9 08 65 40 B9')
        .sig_replace('E0 17 9F 1A FD 7B C2 A8 C0 03 5F D6',
                     '20 00 80 52 FD 7B C2 A8 C0 03 5F D6')
        # Speech Volume Fix
        .sig_replace('E0 00 00 B4 E2 03 13 2A 61 FE FF F0',
                     'E0 00 00 B4 62 0E 00 11 61 FE FF F0'),
    # Audio - Effects
    'vendor/lib64/soundfx/libswdap.so': blob_fixup()
        .sig_replace('08 09 40 f9 00 01 3f d6',
                     '20 00 80 52 1f 20 03 d5'),
    # DRM Widevine
    'vendor/lib64/libwvaidl.so': blob_fixup()
        .replace_needed('libprotobuf-cpp-lite-3.9.1.so', 'libprotobuf-cpp-full-3.9.1.so'),
    # libssl
    'vendor/lib64/libssl-tm.so': blob_fixup()
        .replace_needed('libcrypto.so', 'libcrypto-tm.so'),
    # Keymint
    (
        'vendor/lib64/libskeymint10device.so',
        'vendor/lib64/libskeymint_cli.so',
        'vendor/lib64/vendor.samsung.hardware.keymint-V1-ndk_platform.so',
    ): blob_fixup()
        .replace_needed('android.hardware.security.keymint-V1-ndk_platform.so',
                        'android.hardware.security.keymint-V4-ndk.so')
        .replace_needed('android.hardware.security.secureclock-V1-ndk_platform.so',
                        'android.hardware.security.secureclock-V1-ndk.so')
        .replace_needed('android.hardware.security.sharedsecret-V1-ndk_platform.so',
                        'android.hardware.security.sharedsecret-V1-ndk.so')
        .add_needed('android.hardware.security.rkp-V3-ndk.so')
        .add_needed('libshim_crypto.so')
        .replace_needed('libcrypto.so', 'libcrypto-tm.so'),
    'vendor/bin/hw/android.hardware.security.keymint-service.samsung': blob_fixup()
        .replace_needed('android.hardware.security.keymint-V1-ndk_platform.so',
                        'android.hardware.security.keymint-V3-ndk.so')
        .replace_needed('android.hardware.security.keymint-V1-ndk_platform',
                        'android.hardware.security.keymint-V3-ndk')
        .replace_needed('android.hardware.security.keymint-V1-ndk',
                        'android.hardware.security.keymint-V3-ndk')
        .replace_needed('android.hardware.security.secureclock-V1-ndk_platform.so',
                        'android.hardware.security.secureclock-V1-ndk.so')
        .replace_needed('android.hardware.security.sharedsecret-V1-ndk_platform.so',
                        'android.hardware.security.sharedsecret-V1-ndk.so')
        .add_needed('android.hardware.security.rkp-V3-ndk.so')
        .replace_needed('libcrypto.so', 'libcrypto-tm.so')
        .add_needed('libshim_crypto.so'),
    # RIL
    'vendor/lib64/libsec-ril.so': blob_fixup()
        .add_needed('libprotobuf-cpp-full-21.7.so'),
    'vendor/lib64/libVendorSemTelephonyProps.so': blob_fixup()
        .binary_regex_replace(rb'persist\.ril\.supportNrModefromCp', b'vendor.ril.supportNrModefromCp\x00'),
    # Sensors
    'vendor/lib64/libsensorlistener.so': blob_fixup()
        .add_needed('libshim_sensorndkbridge.so'),
    (
        'vendor/lib64/sensors.grip.so',
        'vendor/lib64/sensors.inputvirtual.so',
        'vendor/lib64/sensors.sensorhub.so',
    ): blob_fixup()
        .add_needed('libutils-v32.so')
        .binary_regex_replace(b'_ZN7android6Thread3runEPKcim', b'_ZN7utils326Thread3runEPKcim')
        .remove_needed('libhidltransport.so'),
    # Vaultkeeper
    'vendor/lib64/libvkmanager_vendor.so': blob_fixup()
        .binary_regex_replace(rb'ro\.factory\.factory_binary', b'ro.vendor.factory_binary\x00'),
}  # fmt: skip

module = ExtractUtilsModule(
    's5e8825-common',
    'samsung',
    namespace_imports=namespace_imports,
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()
