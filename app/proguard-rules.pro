# Запрещаем изменять ваши собственные классы и методы (чтобы работала рефлексия)
-keep class com.haval.akppmodes.** { *; }

# Защищаем автомобильные API Андроида, если они импортированы
-keep class android.car.** { *; }
-keep class android.hardware.automotive.** { *; }

# Сохраняем важную отладочную информацию для логов
-keepattributes SourceFile,LineNumberTable,Signature,EnclosingMethod,InnerClasses
