#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2025 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os
from pathlib import Path
from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)

from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)

blob_fixups: blob_fixups_user_type = {
    (
        'vendor/lib/libdlbpreg.so',
        'vendor/lib/soundfx/libdlbvol.so',
        'vendor/lib64/libcodec2_soft_ac4dec.so',
        'vendor/lib64/libcodec2_soft_ddpdec.so',
        'vendor/lib64/libcodec2_soft_dolby.so',
        'vendor/lib64/libdlbdsservice.so',
        'vendor/lib64/libdlbpreg.so',
        'vendor/lib64/soundfx/libdlbvol.so',
    ): blob_fixup()
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),
    'vendor/lib/soundfx/libswdap.so': blob_fixup()
        .binary_regex_replace(rb'\x28\xee\x05\x46\x20\x46\xf8\xf0\x2c\xef\xb5\xfa\x85\xf0\x47\x09',
                                 b'\x28\xee\x05\x46\x20\x46\xf8\xf0\x2c\xef\xb5\xfa\x85\xf0\x01\x27')
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),
    'vendor/lib64/soundfx/libswdap.so': blob_fixup()
        .binary_regex_replace(b'\x1f\x00\x00\x71\xe0\x03\x00\x91\xf3\x17\x9f\x1a\x41\x62\x04\x94',
                              b'\x1f\x00\x00\x71\xe0\x03\x00\x91\x13\x00\x80\x52\x41\x62\x04\x94')
        .binary_regex_replace(rb'\x09\x00\x00\x12\x89\x02\x09\x0b\x3f\x01\x08\x6b\xca\x01\x00\x54',
                              b'\x09\x00\x00\x12\x89\x02\x09\x0b\x3f\x01\x08\x6b\x0e\x00\x00\x14')
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),
    (
        'vendor/lib64/c2.dolby.avc.dec.so',
        'vendor/lib64/c2.dolby.avc.sec.dec.so',
        'vendor/lib64/c2.dolby.egl.so',
        'vendor/lib64/c2.dolby.hevc.dec.so',
        'vendor/lib64/c2.dolby.hevc.enc.so',
        'vendor/lib64/c2.dolby.hevc.sec.dec.so',
        'vendor/lib64/c2.dolby.store.so',
        'vendor/lib64/libdolbyvision.so'
    ): blob_fixup()
        .replace_needed('libstagefright_foundation.so', 'libstagefright_foundation-v33.so'),
    (
        'vendor/lib64/c2.dolby.avc.dec.so',
        'vendor/lib64/c2.dolby.avc.sec.dec.so',
        'vendor/lib64/c2.dolby.client.so',
        'vendor/lib64/c2.dolby.egl.so',
        'vendor/lib64/c2.dolby.hevc.dec.so',
        'vendor/lib64/c2.dolby.hevc.enc.so',
        'vendor/lib64/c2.dolby.hevc.sec.dec.so',
        'vendor/lib64/c2.dolby.store.so'
    ): blob_fixup()
        .add_needed('libcodec2_shim.so'),
    ('vendor/lib64/c2.dolby.client.so'): blob_fixup()
        .add_needed('libcodec2_hidl_shim.so'),
}  # fmt: skip

module = ExtractUtilsModule(
    'common',
    'dolby',
    blob_fixups=blob_fixups,
    device_rel_path='vendor/dolby',
)

module.add_proprietary_file('proprietary-files_vision.txt')

if __name__ == '__main__':
    local_proprietary = Path(__file__).resolve().parent / 'proprietary'
    if not local_proprietary.exists():
        local_proprietary.mkdir(parents=True, exist_ok=True)

    utils = ExtractUtils.device(module)
    utils.run()
