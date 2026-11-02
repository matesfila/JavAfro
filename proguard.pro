# Rename application code without removing classes or changing playback logic.
-dontshrink
-dontoptimize
-repackageclasses picosoft.javafro.internal

# Stable entry point for java -jar, existing launch configurations and jpackage.
-keep public class picosoft.javafro.AfroJavaPlayer {
    public static void main(java.lang.String[]);
}

# Third-party libraries use reflection, resource lookup and Swing bean conventions.
-keep class org.fife.** { *; }
-keep class org.yaml.snakeyaml.** { *; }

# Preserve runtime metadata; source names and local-variable debug data are omitted.
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*,Record,PermittedSubclasses

# Enum names can be used by Java runtime APIs.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
