# WorkManager crea su base de datos Room y los workers por reflexión;
# sin estas reglas R8 los borra y los recordatorios no llegan en la release.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class dev.charly.mantenimiento.notify.** { *; }
