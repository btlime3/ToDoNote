# Add project specific ProGuard rules here.
# Keep app component names and members stable, including Worker names already
# persisted by WorkManager. Unused dependency code/resources can still shrink.
# 앱 코드는 리플렉션을 쓰지 않으므로 별도 유지 규칙이 필요 없다.
# 매니페스트, 레이아웃, 라이브러리에 필요한 클래스는 빌드 도구가 자동으로 유지한다.

# 오류 로그의 줄 번호를 남긴다. 원래 이름은 mapping.txt로 찾는다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
