# Подключение Google/YouTube

1. Создайте проект в Google Cloud Console.
2. Включите YouTube Data API v3.
3. Создайте OAuth Client ID типа Android.
4. Укажите package name `com.example.streamline` и SHA-1 сертификата сборки.
5. Откройте `app/src/main/java/com/example/streamline/sync/GoogleOAuthManager.kt` и замените `YOUR_ANDROID_CLIENT_ID` в месте подключения менеджера на свой Client ID.
6. Для чтения и изменения собственных плейлистов используется scope `https://www.googleapis.com/auth/youtube.force-ssl`.

OAuth использует PKCE: секрет клиента в приложение не встраивается. Callback приложения: `com.example.streamline:/oauth2redirect`.
