# Nexo release rules. Room, Hilt and kotlinx.serialization ship their own consumer rules.
-keepattributes *Annotation*, InnerClasses

# ML Kit names its component registrars in manifest meta-data and creates them by reflection when the app starts.
# R8 full mode keeps those class names but drops their no-argument constructors, so no registrar loads and
# TextRecognition.getClient() fails in release builds only (NoSuchMethodException, logged by ComponentDiscovery).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
