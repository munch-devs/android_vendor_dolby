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
}  # fmt: skip

module = ExtractUtilsModule(
    'common',
    'dolby',
    blob_fixups=blob_fixups,
    device_rel_path='vendor/dolby',
)

if __name__ == '__main__':
    local_proprietary = Path(__file__).resolve().parent / 'proprietary'
    if not local_proprietary.exists():
        local_proprietary.mkdir(parents=True, exist_ok=True)

    utils = ExtractUtils.device(module)
    utils.run()
