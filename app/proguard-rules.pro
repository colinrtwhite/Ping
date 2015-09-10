# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/Colin/Android/SDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-optimizationpasses 10
-allowaccessmodification
-mergeinterfacesaggressively
-flattenpackagehierarchy ''
-overloadaggressively

# Used for Butterknife:
-keep class butterknife.** { *; }
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }
-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}
-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}