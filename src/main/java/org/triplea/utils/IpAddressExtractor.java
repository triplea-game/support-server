package org.triplea.utils;

import io.vertx.ext.web.RoutingContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IpAddressExtractor {

  /**
   * Extracts the IP address of the remote address from the routing context.
   *
   * <p>IPv6 addresses may be surrounded by square brackets; this method strips them.
   *
   * @return IP address of the remote machine making the request
   */
  public String extractIpAddress(RoutingContext routingContext) {
    return routingContext
        .request()
        .remoteAddress()
        .host()
        .replaceAll("\\[", "")
        .replaceAll("\\]", "");
  }
}
