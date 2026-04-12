package com.example.myquotes;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CsvLoader {
    private static final String TAG = "CsvLoader";

    public static List<Quote> loadQuotesFromRaw(Context context, int rawResourceId) {
        List<Quote> quotes = new ArrayList<>();

        try {
            InputStream inputStream = context.getResources().openRawResource(rawResourceId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            boolean firstLine = true;
            int lineNumber = 0;
            int quoteId = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip header
                if (firstLine) {
                    firstLine = false;
                    Log.d(TAG, "CSV Header: " + line);
                    continue;
                }

                try {
                    Quote quote = parseCsvLine(line, quoteId);
                    if (quote != null) {
                        quotes.add(quote);
                        quoteId++;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing line " + lineNumber + ": " + e.getMessage());
                }
            }

            reader.close();
            inputStream.close();

            Log.d(TAG, "Successfully loaded " + quotes.size() + " quotes from CSV");

        } catch (IOException e) {
            Log.e(TAG, "Error reading CSV file", e);
        }

        return quotes;
    }

    private static Quote parseCsvLine(String line, int id) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Check for escaped quote ("")
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++; // skip the second quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        // Add the final field
        fields.add(currentField.toString());

        // Debug-Logging
        if (fields.size() != 4) {
            Log.w(TAG, "Line #" + id + " has " + fields.size() + " fields (expected 4)");
        }

        if (fields.size() >= 3) {
            Quote quote = new Quote();
            quote.setId(id);
            quote.setAuthor(cleanCsvField(fields.get(0)));
            quote.setSource(cleanCsvField(fields.get(1)));
            quote.setQuoteText(cleanCsvField(fields.get(2)));

            // Category is optional (column 4)
            if (fields.size() >= 4) {
                String category = cleanCsvField(fields.get(3));
                quote.setCategory(category);

                if (!category.isEmpty()) {
                    Log.d(TAG, "Quote #" + id + " - Category: '" + category + "'");
                }
            } else {
                quote.setCategory("");
            }

            return quote;
        } else {
            Log.w(TAG, "Invalid CSV line (expected at least 3 fields, got " + fields.size() + ")");
            return null;
        }
    }

    private static String cleanCsvField(String field) {
        if (field == null) return "";

        field = field.trim();

        // Remove surrounding quotes
        if (field.startsWith("\"") && field.endsWith("\"") && field.length() >= 2) {
            field = field.substring(1, field.length() - 1);
        }

        // Unescape doubled quotes ("" → ")
        field = field.replace("\"\"", "\"");

        field = field.trim();

        if (field.isEmpty()) {
            return "";
        }

        return field;
    }
}