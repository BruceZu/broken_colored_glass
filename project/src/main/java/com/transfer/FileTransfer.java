package com.coustomer.projs.web.rest.controller.serviceProvider.transfer;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartHttpServletRequest;

interface FileTransfer {
  JsonObject transfer(MultipartHttpServletRequest multiRequest, HttpServletResponse response)
      throws IllegalStateException, IOException, ServletException, TimeoutException,
          InterruptedException;
}
