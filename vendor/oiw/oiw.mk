# OIW cinema layer product makefile — inherit from the device makefile to bake the OIW apps and
# configs into a Path A system image:
#   $(call inherit-product-if-exists, vendor/oiw-rom/vendor/oiw/oiw.mk)
# (path assumes this repo is mounted at vendor/oiw-rom per manifests/oiw_nuwa.xml)

PRODUCT_PACKAGES += \
    OIWCamera \
    OIWLauncher

# Capture profiles / LUTs / button maps shipped read-only on product; the apps copy them to
# /sdcard/OIW_MEDIA on first run so users can still hand-edit their working set.
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*.json,vendor/oiw-rom/configs/capture_profiles,$(TARGET_COPY_OUT_PRODUCT)/etc/oiw/capture_profiles) \
    $(call find-copy-subdir-files,*.cube,vendor/oiw-rom/configs/lut,$(TARGET_COPY_OUT_PRODUCT)/etc/oiw/lut) \
    $(call find-copy-subdir-files,*.json,vendor/oiw-rom/configs/lut,$(TARGET_COPY_OUT_PRODUCT)/etc/oiw/lut) \
    $(call find-copy-subdir-files,*.json,vendor/oiw-rom/configs/thermal_profiles,$(TARGET_COPY_OUT_PRODUCT)/etc/oiw/thermal_profiles) \
    $(call find-copy-subdir-files,*.json,vendor/oiw-rom/configs/button_mappings,$(TARGET_COPY_OUT_PRODUCT)/etc/oiw/button_mappings)

# Cinema-first defaults (safe, reversible via Settings):
PRODUCT_PRODUCT_PROPERTIES += \
    ro.oiw.cinema=1
