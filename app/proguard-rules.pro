# R8 rules for the release build.
#
# This file adds no -keep rules, on purpose. Each place where Wisp's code, or a
# library, reaches something by name was checked against the shrunk APKs (both
# flavors), and all of them are already covered:
#
# - Manifest components (VeldtApp, MainActivity, MediaNotificationListener,
#   IslandForegroundService, and the github flavor's FileProvider) keep their names
#   through AGP's generated aapt_rules.txt. The system binds the listener by that
#   name.
# - Notification access is checked by package name
#   (NotificationManagerCompat.getEnabledListenerPackages), not by a hard-coded
#   class-name string, so renaming classes cannot break it. The listener's own
#   ComponentName is built from a class literal, which R8 rewrites.
# - Hilt/Dagger ship their own rules. The @HiltViewModel map key is a class-name
#   string marked @IdentifierNameString, and R8 rewrites it to the obfuscated name
#   (checked in the dex: only the renamed string is there).
# - DataStore preferences and OkHttp (pulled in by Coil) bundle consumer rules;
#   Coil 2 needs none. OkHttp's publicsuffixes.gz is still in the APK.
# - The github UpdateChecker parses JSON with org.json and fetches over
#   HttpURLConnection. Both are platform classes that R8 does not shrink, and
#   fields are read by string key, not by reflection.
# - Settings are persisted as explicit string keys ("left", "below", position
#   keys, and so on), never as an enum's .name, and nothing calls valueOf. There is
#   no Class.forName, no ::class.java.name used as a key, and no serialization
#   library.
# - R8 reported no missing classes and wrote no missing_rules.txt.
#
# If a device test finds a real reflective path, add its keep rule here, with
# the evidence for it.

# Preserve line numbers for readable release crash stack traces, while hiding
# the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
