#!/system/bin/sh
# Runtime presentation only: TARGET_BUILD_VARIANT and ANDROID_DEBUGGABLE stay
# userdebug. Do not change verified-boot claims, fingerprints, or ADB state here.

dumberos_set_build_property() {
    if [ "$(getprop "$1")" != "$2" ]; then
        resetprop_phh "$1" "$2"
    fi
}

dumberos_set_build_property ro.build.type user

for dumberos_property in ro.build.display.id ro.build.flavor; do
    dumberos_value="$(getprop "$dumberos_property")"
    # Keep the build identifier and product variant; change only ROM branding
    # and the presentation of the compilation variant.
    dumberos_value="$(printf '%s' "$dumberos_value" | sed \
        -e 's/[Ll][Ii][Nn][Ee][Aa][Gg][Ee][Oo][Ss]/DumberOS/g' \
        -e 's/[Ll][Ii][Nn][Ee][Aa][Gg][Ee]/dumberos/g' \
        -e 's/-userdebug$/-user/' -e 's/-eng$/-user/')"
    if [ -n "$dumberos_value" ]; then
        dumberos_set_build_property "$dumberos_property" "$dumberos_value"
    fi
done
unset dumberos_property dumberos_value
