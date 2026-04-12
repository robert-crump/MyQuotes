# MyQuotes

A personal Android app for managing and enjoying your quote collection.

## About

MyQuotes lets you store, browse, and organize quotes. Swipe through your collection, mark favorites, search by keyword or category, and receive a daily quote notification to start your day with inspiration.

## Features

- Browse quotes with swipe gestures (ViewPager2)
- Add, edit, and delete quotes
- Mark quotes as favorites
- Filter quotes by category
- Full-text search
- Daily quote notification (scheduled around 4 PM)
- Usage statistics
- Import quotes from CSV

## Requirements

- Android 14 or higher (API 34+)
- Android Studio Meerkat or later (to build from source)

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/robert-crump/MyQuotes.git
   ```
2. Open the project in Android Studio.
3. Build and run on a device or emulator running Android 14+.

> **Note:** The app starts with an empty quote collection. Add quotes manually or import a CSV file via the app.

## Built With

- Java
- AndroidX (AppCompat, ViewPager2, RecyclerView, CardView, ConstraintLayout)
- Lifecycle ViewModel + LiveData
- WorkManager (daily notifications)
- Material Design 3

## Development

This project was developed with assistance from [Claude Code](https://claude.ai/code) by Anthropic.
