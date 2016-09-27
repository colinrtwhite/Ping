# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/Colin/Android/SDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-allowaccessmodification
-flattenpackagehierarchy
-mergeinterfacesaggressively
-optimizationpasses 100 # If a pass finishes and no optimization is made, then it skips the rest of the passes.
-overloadaggressively
