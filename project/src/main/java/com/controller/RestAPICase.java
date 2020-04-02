package com.coustomer.projs.rest.controller;

import static com.coustomer.projs.rest.controller.PrjSpaCommonRequestController.hasLoggedIn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The API is not recommended and should be avoided as much as possible. Saving front end temporary
 * data with Window.localStorage and Window.sessionStorage is always preferred to reduce server-side
 * call.
 *
 * <p>Not storage size limitation by far.
 */
@Controller
public class PrjSPFrontendSessionTemporaryStorageController {
  private static Logger log =
      LogManager.getLogger(PrjSPFrontendSessionTemporaryStorageController.class.getName());
  private static final String SP_URL = "/v1/serviceProvider/any/user/self/sessiontemporarystorage";
  private static final String KEY_PROFIX = "PROJ_ONELINE_PROVIDER_SESSION_TEMPORARY_STORAGE_";
  private static final String POST_PAYLOAD_FORMAT =
      "{'key':xx_string_xx, 'value':xx_any_valid_json_type_xx}";
  private static final String GET_PAYLOAD_FORMAT = "{'key':xx_string_xx}";
  private static final String PAYLOAD_FORMAT_K = "key";
  private static final String PAYLOAD_FORMAT_V = "value";

  private static String getSessionAttributeName(String key) {
    return KEY_PROFIX + key;
  }

  private static String inJson(String message) {
    JsonObject erroJson = new JsonObject();
    erroJson.add("echo", new JsonPrimitive(message));
    return erroJson.toString();
  }

  private boolean isValidKey(JsonElement payload) {
    if (payload.isJsonNull()
        || !payload.isJsonObject()
        || payload.getAsJsonObject().get(PAYLOAD_FORMAT_K).isJsonNull()
        || !payload.getAsJsonObject().get(PAYLOAD_FORMAT_K).isJsonPrimitive()) {
      return false;
    }
    String key = payload.getAsJsonObject().get(PAYLOAD_FORMAT_K).getAsString();
    if (key.trim().isEmpty()) {
      return false;
    }
    return true;
  }

  private boolean isValidValue(JsonElement payload) {
    if (payload.isJsonNull()
        || !payload.isJsonObject()
        || payload.getAsJsonObject().get(PAYLOAD_FORMAT_V).isJsonNull()) {
      return false;
    }
    if (payload.getAsJsonObject().get(PAYLOAD_FORMAT_V).isJsonPrimitive()) {
      String value =
          payload.getAsJsonObject().get(PAYLOAD_FORMAT_V).getAsJsonPrimitive().getAsString();
      if (value.trim().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private ResponseEntity<String> failedRespone(String error, HttpStatus status) {
    log.error(error);
    return ResponseEntity.status(status).body(inJson(error).toString());
  }

  private ResponseEntity<String> apply(
      HttpServletRequest request, Function<HttpSession, ResponseEntity<String>> applyer) {
    if (hasLoggedIn()) {
      HttpSession session = request.getSession(false);
      if ((boolean) session.getAttribute("serviceProvider")) {
        return applyer.apply(session);
      }
    }
    return failedRespone("Only online serviceProvider user can call this API!", HttpStatus.FORBIDDEN);
  }

  @PostMapping(
    value = SP_URL,
    produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
    consumes = MediaType.APPLICATION_JSON_UTF8_VALUE
  )
  public ResponseEntity<String> post(HttpEntity<String> requestEntity, HttpServletRequest request) {
    return apply(
        request,
        session -> {
          JsonElement payload = null;
          try {
            payload = new JsonParser().parse(requestEntity.getBody());
          } catch (JsonSyntaxException e) {
            return failedRespone("JSON syntax excepiton " + e.getMessage(), HttpStatus.BAD_REQUEST);
          }
          ResponseEntity<String> failedResult =
              failedRespone(
                  "payload format should be like " + POST_PAYLOAD_FORMAT, HttpStatus.BAD_REQUEST);
          if (!isValidKey(payload) || !isValidValue(payload)) {
            return failedResult;
          }
          String key = payload.getAsJsonObject().get(PAYLOAD_FORMAT_K).getAsString();
          String value = payload.getAsJsonObject().get(PAYLOAD_FORMAT_V).toString();
          if (value.trim().isEmpty()) {
            return failedResult;
          }
          session.setAttribute(getSessionAttributeName(key), value);

          JsonObject result = new JsonObject();
          result.add(PAYLOAD_FORMAT_K, new JsonPrimitive(key));
          return ResponseEntity.status(HttpStatus.CREATED).body(result.toString());
        });
  }

  @GetMapping(
    value = SP_URL + "/{key}",
    produces = MediaType.APPLICATION_JSON_UTF8_VALUE
  )
  public ResponseEntity<String> get(
      @PathVariable(name = PAYLOAD_FORMAT_K, required = false) String key,
      HttpEntity<String> requestEntity,
      HttpServletRequest request) {
    return apply(
        request,
        session -> {
          if (key.trim().isEmpty()) {
            return failedRespone("key should not be empty string", HttpStatus.BAD_REQUEST);
          }
          Object value = session.getAttribute(getSessionAttributeName(key));
          if (value == null) {
            return failedRespone(
                "Current serviceProvider user did not save related information!", HttpStatus.BAD_REQUEST);
          }
          JsonObject result = new JsonObject();
          result.add(PAYLOAD_FORMAT_K, new JsonPrimitive(key));
          result.add(PAYLOAD_FORMAT_V, new JsonParser().parse((String) value));
          return ResponseEntity.ok(result.toString());
        });
  }

  @GetMapping(
    value = SP_URL,
    produces = MediaType.APPLICATION_JSON_UTF8_VALUE,
    consumes = MediaType.APPLICATION_JSON_UTF8_VALUE
  )
  public ResponseEntity<String> getWithKeyInBody(
      HttpEntity<String> requestEntity, HttpServletRequest request) {
    return apply(
        request,
        session -> {
          JsonElement payload = null;
          try {
            payload = new JsonParser().parse(requestEntity.getBody());
          } catch (JsonSyntaxException e) {
            return failedRespone("JSON syntax excepiton " + e.getMessage(), HttpStatus.BAD_REQUEST);
          }

          if (!isValidKey(payload)) {
            return failedRespone(
                "payload format should be like " + GET_PAYLOAD_FORMAT, HttpStatus.BAD_REQUEST);
          }
          String key = payload.getAsJsonObject().get(PAYLOAD_FORMAT_K).getAsString();
          Object value = session.getAttribute(getSessionAttributeName(key));
          if (value == null) {
            return failedRespone(
                "Current serviceProvider user did not save related information!", HttpStatus.BAD_REQUEST);
          }
          payload.getAsJsonObject().add(PAYLOAD_FORMAT_V, new JsonParser().parse((String) value));
          return ResponseEntity.ok(payload.toString());
        });
  }
}
