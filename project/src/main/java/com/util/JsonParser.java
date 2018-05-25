package com.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;

public class JsonParser {

  // In Java 8 use Function interface
  public interface Function<T> {
    T apply(T t);
  }

  /**
   * <pre>
   * Parses the specified Json string into a parse tree. In the process any String value containing
   * '<script' will be escaped using HTML entities.
   * Not thread safe.
   * Comparing with Jackson, GSON parser has better performance for small Json string/file and the code is simple and concise to use.
   * That is why using GSON.
   * reference  https://blog.takipi.com/the-ultimate-json-library-json-simple-vs-gson-vs-jackson-vs-json/
   * @param jsonStr Json string. If the jsonStr is not valid Json string, throw JsonSyntaxException
   */
  public static JsonElement parse(String jsonStr, final FPCFunction<String> function) {
    boolean isEmpty = true;
    JsonElement tree;
    try (JsonReader reader = new JsonReader(new StringReader(jsonStr))) {
      reader.setLenient(true);
      reader.peek();
      isEmpty = false;
      tree =
          new TypeAdapter<JsonElement>() {
            @Override
            public JsonElement read(JsonReader in) throws IOException {
              JsonToken token = in.peek();
              if (token.equals(JsonToken.STRING)) {
                return new JsonPrimitive(function.apply(in.nextString()));
              }
              if (token.equals(JsonToken.BEGIN_OBJECT)) {
                JsonObject object = new JsonObject();
                in.beginObject();
                while (in.hasNext()) {
                  object.add(in.nextName(), read(in));
                }
                in.endObject();
                return object;
              }
              if (token.equals(JsonToken.BEGIN_ARRAY)) {
                JsonArray array = new JsonArray();
                in.beginArray();
                while (in.hasNext()) {
                  array.add(read(in));
                }
                in.endArray();
                return array;
              }
              return TypeAdapters.JSON_ELEMENT.read(in);
            }

            @Override
            public void write(JsonWriter out, JsonElement value) throws IOException {
              throw new UnsupportedOperationException();
            }
          }.read(reader);
      if (!tree.isJsonNull() && reader.peek() != JsonToken.END_DOCUMENT) {
        throw new JsonSyntaxException("Did not consume the entire document.");
      }
      return tree;
    } catch (EOFException e) {
      if (isEmpty) {
        return JsonNull.INSTANCE;
      }
      throw new JsonSyntaxException(e);
    } catch (MalformedJsonException | NumberFormatException e) {
      throw new JsonSyntaxException(e);
    } catch (IOException e) {
      throw new JsonIOException(e);
    } catch (StackOverflowError | OutOfMemoryError e) {
      throw new JsonParseException("Failed parsing JSON source: " + jsonStr + " to Json", e);
    }
  }
}

