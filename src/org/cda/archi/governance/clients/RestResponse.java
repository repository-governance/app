package org.cda.archi.governance.clients;

import java.net.http.HttpHeaders;

public record RestResponse(int statusCode, String body, HttpHeaders headers) { }
