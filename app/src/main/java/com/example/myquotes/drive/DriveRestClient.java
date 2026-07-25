package com.example.myquotes.drive;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hand-rolled client for the subset of the Drive REST API v3 the backup feature needs
 * (folder find/create, multipart upload, list, delete). With only the drive.file scope and a
 * handful of calls, raw HttpURLConnection avoids pulling in the full google-api-client stack.
 */
final class DriveRestClient {
    private static final String FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final int TIMEOUT_MILLIS = 15000;

    static final class DriveFile {
        final String id;
        final String name;

        DriveFile(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final String accessToken;

    DriveRestClient(String accessToken) {
        this.accessToken = accessToken;
    }

    /** Returns the id of {@code folderName} under My Drive, creating it if it doesn't already exist. */
    String findOrCreateFolder(String folderName) throws IOException, JSONException {
        String existing = findFolder(folderName);
        return existing != null ? existing : createFolder(folderName);
    }

    /** Uploads {@code content} as a new file inside {@code parentFolderId}. */
    DriveFile uploadFile(String parentFolderId, String filename, byte[] content, String mimeType)
            throws IOException, JSONException {
        String boundary = "myquotes-" + System.currentTimeMillis();

        JSONObject metadata = new JSONObject();
        metadata.put("name", filename);
        metadata.put("parents", new JSONArray().put(parentFolderId));

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writePart(body, boundary, "application/json; charset=UTF-8",
                metadata.toString().getBytes(StandardCharsets.UTF_8));
        writePart(body, boundary, mimeType, content);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpURLConnection conn = open(UPLOAD_URL, "POST");
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        writeBody(conn, body.toByteArray());

        JSONObject response = readJsonResponse(conn);
        return new DriveFile(response.getString("id"), response.optString("name", filename));
    }

    /** Lists the (id, name) of every non-trashed file directly inside {@code parentFolderId}. */
    List<DriveFile> listFiles(String parentFolderId) throws IOException, JSONException {
        String query = "'" + escape(parentFolderId) + "' in parents and trashed = false";
        String url = FILES_URL + "?q=" + encode(query) + "&fields=" + encode("files(id,name)")
                + "&pageSize=1000&spaces=drive";

        JSONObject response = request("GET", url, null);
        JSONArray files = response.optJSONArray("files");
        List<DriveFile> result = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                result.add(new DriveFile(file.getString("id"), file.getString("name")));
            }
        }
        return result;
    }

    void deleteFile(String fileId) throws IOException {
        HttpURLConnection conn = open(FILES_URL + "/" + encode(fileId), "DELETE");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_NO_CONTENT && code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Drive delete failed: HTTP " + code + " " + readErrorBody(conn));
        }
    }

    private String findFolder(String folderName) throws IOException, JSONException {
        String query = "name = '" + escape(folderName) + "' and mimeType = '" + FOLDER_MIME_TYPE
                + "' and trashed = false";
        String url = FILES_URL + "?q=" + encode(query) + "&fields=" + encode("files(id,name)")
                + "&spaces=drive";

        JSONObject response = request("GET", url, null);
        JSONArray files = response.optJSONArray("files");
        return files != null && files.length() > 0 ? files.getJSONObject(0).getString("id") : null;
    }

    private String createFolder(String folderName) throws IOException, JSONException {
        JSONObject metadata = new JSONObject();
        metadata.put("name", folderName);
        metadata.put("mimeType", FOLDER_MIME_TYPE);

        JSONObject response = request("POST", FILES_URL + "?fields=" + encode("id"), metadata);
        return response.getString("id");
    }

    private JSONObject request(String method, String urlString, JSONObject jsonBody)
            throws IOException, JSONException {
        HttpURLConnection conn = open(urlString, method);
        if (jsonBody != null) {
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            writeBody(conn, jsonBody.toString().getBytes(StandardCharsets.UTF_8));
        }
        return readJsonResponse(conn);
    }

    private HttpURLConnection open(String urlString, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(TIMEOUT_MILLIS);
        conn.setReadTimeout(TIMEOUT_MILLIS);
        return conn;
    }

    private void writeBody(HttpURLConnection conn, byte[] data) throws IOException {
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(data);
        }
    }

    private void writePart(ByteArrayOutputStream body, String boundary, String contentType, byte[] content)
            throws IOException {
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject readJsonResponse(HttpURLConnection conn) throws IOException, JSONException {
        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            String body = readStream(conn.getInputStream());
            return body.isEmpty() ? new JSONObject() : new JSONObject(body);
        }
        throw new IOException("Drive API request failed: HTTP " + code + " " + readErrorBody(conn));
    }

    private String readErrorBody(HttpURLConnection conn) {
        try {
            InputStream err = conn.getErrorStream();
            return err != null ? readStream(err) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        return out.toString("UTF-8");
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
