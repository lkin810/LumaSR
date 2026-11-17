# Prebuilt Mihon Upscale engine: JNI is registered statically against these
# exact class names, so R8 must not rename or strip them.
-keep class eu.kanade.tachiyomi.util.waifu2x.Waifu2x { *; }
-keep class eu.kanade.tachiyomi.util.qnn.QnnModelContexts { *; }
-keep class eu.kanade.tachiyomi.util.qnn.QualcommHtp { *; }
-keep class eu.kanade.tachiyomi.util.mediatek.MediaTekNpu { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    public void onProgress(int, int);
}
