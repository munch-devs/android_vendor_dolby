#
# Automatically generated file. DO NOT MODIFY
#

PRODUCT_SOONG_NAMESPACES += \
    vendor/dolby/common

PRODUCT_COPY_FILES += \
    vendor/dolby/common/proprietary/vendor/etc/init/vendor.dolby.hardware.dms@2.0-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.dolby.hardware.dms@2.0-service.rc \
    vendor/dolby/common/proprietary/vendor/etc/init/vendor.dolby.media.c2@1.0-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.dolby.media.c2@1.0-service.rc

PRODUCT_PACKAGES += \
    libdapparamstorage \
    libdlbpreg \
    libdlbvol \
    libswdap \
    libswgamedap \
    libswvqe \
    vendor.dolby.hardware.dms@2.0 \
    libcodec2_soft_ac4dec \
    libcodec2_soft_ddpdec \
    libcodec2_soft_dolby \
    libcodec2_store_dolby \
    libdeccfg \
    libdlbdsservice \
    liboem_specific \
    vendor.dolby.hardware.dms@2.0-impl \
    vendor.dolby.hardware.dms@2.0-service \
    vendor.dolby.media.c2@1.0-service

PRODUCT_COPY_FILES += \
    vendor/dolby/common/proprietary/vendor/etc/init/vendor.dolby.media.dvs-service-vision.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.dolby.media.dvs-service-vision.rc \
    vendor/dolby/common/proprietary/vendor/etc/init/vendor.dolbyvision.media.c2@1.0-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.dolbyvision.media.c2@1.0-service.rc

PRODUCT_PACKAGES += \
    c2.dolby.avc.dec \
    c2.dolby.avc.sec.dec \
    c2.dolby.client \
    c2.dolby.egl \
    c2.dolby.hevc.dec \
    c2.dolby.hevc.enc \
    c2.dolby.hevc.sec.dec \
    c2.dolby.store \
    libdolbyottcameracontrol \
    libdolbyvision \
    vendor.dolby.dvs@1.0 \
    dolbycodec2 \
    dvs-hal-service
