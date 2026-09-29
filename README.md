# MyQuotes

[![Android CI](https://github.com/robert-crump/MyQuotes/actions/workflows/android.yml/badge.svg)](https://github.com/robert-crump/MyQuotes/actions/workflows/android.yml)

Your personal quote collection, one swipe at a time.

<table>
  <tr>
    <td><img src="docs/screenshots/quotes.png" width="200" alt="Quotes tab: swiping through quote cards"></td>
    <td><img src="docs/screenshots/search.png" width="200" alt="Search tab: all quotes by Jane Austen"></td>
    <td><img src="docs/screenshots/favorites.png" width="200" alt="Favorites tab showing a favorited quote"></td>
    <td><img src="docs/screenshots/statistics.png" width="200" alt="Statistics: quotes added per month"></td>
    <td><img src="docs/screenshots/quotes-dark.png" width="200" alt="Quotes tab in dark mode"></td>
  </tr>
</table>

- **Swipe** through your quotes in shuffled order
- **Favorite** the ones that stay with you
- **Search** by text, author, source or category
- **Daily quote** notification in the afternoon
- **Statistics** on your collection
- **Back up** automatically to a local folder or Google Drive, or export and import a file
- **Light and dark** theme

<sub>Screenshots use public-domain demo quotes; regenerate with `./gradlew readmeScreenshots` (needs a running emulator).</sub>

## Build

Requires Android 14+ (API 34) and Android Studio Meerkat or later.

```bash
git clone https://github.com/robert-crump/MyQuotes.git
```

Open in Android Studio and run. The app starts empty: add quotes or import a backup in Settings.

Built with Java, AndroidX, WorkManager and Material Design 3. Developed with [Claude Code](https://claude.ai/code).
