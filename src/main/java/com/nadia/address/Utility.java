package com.nadia.address;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Главный класс утилиты.
 */
public class Utility {

    // Заголовки по-умолчанию для HTTP запросов.
    private static final Map<String, List<String>> DEFAULT_HEADER_FIELDS = new HashMap<String, List<String>>() { {
        put("Content-Type", Arrays.asList("application/json", "charset=utf-8"));
    } };
    // размер буфера в байтах для загрузки файлов
    private static final int BUFFER_LENGTH_1024 = 1024;
    // HTTP код 400 для проверки ошибок
    private static final int RESPONSE_CODE_400 = 400;
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Place>>() {}.getType();

    public static class Place {
        String display_name;
        String osm_type;
        String type;

        @Override
        public String toString() {
            return "{display_name: " + display_name
                + ", osm_type: " + osm_type
                + ", type: " + type + "}";
        }
    }

    public interface DownloadListener {
        void onError();
        void onDownload(List<Place> places);
    }

    private static String generateQuery(String street) {
        try {
            return String.format("http://nominatim.openstreetmap.org/search?street=%s&format=json&city=%s",
                URLEncoder.encode(street, "UTF-8"), URLEncoder.encode("СПб", "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    // Класс хранит ответ сервера (может быть бинарный или текстовый)
    public static class FetchResponse {
        private final boolean ok;
        private final int status;
        private final java.io.ByteArrayOutputStream stream;

        public FetchResponse(final boolean ok, final int status,
            final java.io.ByteArrayOutputStream stream) {
            this.ok = ok;
            this.status = status;
            this.stream = stream;
        }

        public boolean isOk() {
            return ok;
        }

        public int getStatus() {
            return status;
        }

        public String text() {
            try {
                return stream.toString("UTF-8");
            } catch (java.io.UnsupportedEncodingException ex) {
                throw new UnsupportedOperationException(ex);
            }
        }
    }

    // метод для считывания файла
    public static FetchResponse fetch(final String url) {
        final String localMethod = "GET";
        try {
            // Получить URL объект из строки
            final java.net.URL localUrl = new java.net.URL(url);
            // Открыть соединение
            final java.net.HttpURLConnection connection = (java.net.HttpURLConnection) localUrl.openConnection();
            connection.setRequestMethod(localMethod);
            for (final Map.Entry<String, List<String>> header : DEFAULT_HEADER_FIELDS.entrySet()) {
                connection.setRequestProperty(header.getKey(), join(header.getValue(), ";"));
            }
            final int responseCode = connection.getResponseCode();
            // Прочитать содержимое файла из ответа
            final java.io.InputStream inputStream;
            if (responseCode < RESPONSE_CODE_400) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            // Буфер в памяти для сохранения содержимого файла
            final java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_LENGTH_1024];
            int length;
            // Скопировать в буфер
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            inputStream.close();
            return new FetchResponse(responseCode < RESPONSE_CODE_400, responseCode, result);
        } catch (java.io.IOException ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    // Метод для склеивания содержимого массива
    static <T> String join(final Iterable<T> iterable, final String separator) {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        for (final T item : iterable) {
            if (index > 0) {
                sb.append(separator);
            }
            sb.append(item.toString());
            index += 1;
        }
        return sb.toString();
    }

    private static String requestData(String queryUrl) {
        return fetch(queryUrl).text();
    }

    private static List<Place> extractPlaces(String jsonData) {
        Gson gson = new Gson();
        List<Place> places = gson.fromJson(jsonData, LIST_TYPE);
        return places;
    }

    private static void searchAddresses(final String query, final DownloadListener downloadListener) {
        new Thread("search-address") {
            public void run(){
                try {
                    // 1 - Генерация запроса
                    String queryUrl = generateQuery(query);
                    // 2 - Запрос к серверу
                    String jsonData = requestData(queryUrl);
                    // 3 - Подготовка к обработке ответа, Обработка ответа
                    List<Place> places = extractPlaces(jsonData);
                    downloadListener.onDownload(places);
                } catch (Exception ex) {
                    downloadListener.onError();
                }
            }
        }.start();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar utility.jar -q query_string");
        } else {
            String query = null;
            for (int index = 0; index < args.length - 1; index += 1) {
                if (args[index].equals("-q")) {
                    query = args[index + 1];
                }
            }
            DownloadListener downloadListener = new DownloadListener() {
                public void onError() {
                    System.err.println("Error happened.");
                }
                public void onDownload(List<Place> places) {
                    System.out.println("places - " + places);
                }
            };
            // Вызов метода для поиска адресов
            searchAddresses(query, downloadListener);
        }
    }
}
