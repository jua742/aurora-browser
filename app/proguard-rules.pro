# Aurora Browser — ProGuard / R8 keep rules (spec B-20, Phase 10)
#
# Release builds use R8 full mode + shrinkResources. These rules protect the
# reflection-heavy libraries (Room, DataStore/protobuf, Coil) and the WebView
# client classes from being renamed or stripped.

# --- Room (compile-time generated code + entities) ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.DatabaseView class *
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.Embedded <fields>;
    @androidx.room.Relation <fields>;
}
-dontwarn androidx.room.**

# --- WebView clients (referenced by name from the framework) ---
-keepclassmembers class * extends android.webkit.WebViewClient { *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { *; }

# --- DataStore Preferences (protobuf internals) ---
-keep class androidx.datastore.** { *; }
-dontwarn com.google.protobuf.**

# --- Coil (favicons) ---
-keep class coil.** { *; }
-dontwarn coil.**

# --- Parcelable / Serializable (future-proofing; v1 has few) ---
-keepclassmembers class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- OSS licenses ---
-keep class com.google.android.gms.oss.licenses.** { *; }
