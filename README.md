# Symbian Launcher

A Nokia Symbian–style home screen for D-pad Android phones (built for the Dumber Mini / DumberOS).

## Builds

Every push to `main` is built by GitHub Actions and published as a **Release** with
`symbian-launcher.apk` attached. Releases are signed with the same key every time, so
updates install over the previous version and keep your settings.

## Installing

**First install (USB, from a computer):**

1. On the phone: Settings → About phone → tap *Build number* seven times, then enable
   *USB debugging* in Developer options.
2. On the computer: install `adb` (`sudo apt install adb` on Mint, `sudo pacman -S android-tools` on Arch/EndeavourOS).
3. Download `symbian-launcher.apk` from the latest Release and run
   `adb install -r symbian-launcher.apk`.
4. Press Home, choose *Symbian*, and select *Always*.

**Updates (Obtainium on the phone):** add this repository in Obtainium (with a GitHub
personal access token, as the repository is private). New releases then install with one
confirmation.

## Project layout

| File | Contents |
|---|---|
| `MainActivity.kt` | Activity, theme modifiers, screen enum |
| `LauncherScreen.kt` | Root screen, navigation and key routing |
| `StatusBars.kt` | Top status bar (clock, battery, call/media layer) and soft-key bar |
| `HubView.kt`, `MenuView.kt`, `AppDrawer.kt` | Home hub, menu grid, app list |
| `ContactScreens.kt`, `T9SearchView.kt` | Contacts, contact detail, T9 search |
| `NotificationsView.kt`, `AgendaScreen.kt` | Notifications, messages, agenda |
| `WeatherForecastScreen.kt`, `UsageStatsScreen.kt`, `ManageAppDialog.kt` | Feature screens |
| `Ticker.kt`, `IconCache.kt`, `CallStateTracker.kt`, `SystemStatus.kt` | Shared efficient time, icon, call and battery/signal sources |
| `LauncherViewModel.kt`, `AppRepository.kt` | State and data loading |
