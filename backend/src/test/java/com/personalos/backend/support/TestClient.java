package com.personalos.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A tiny browser stand-in: keeps cookies between requests and attaches the CSRF token. */
public class TestClient {

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final Map<String, Cookie> jar = new LinkedHashMap<>();
    private String csrfHeader;
    private String csrfToken;

    public TestClient(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public MvcResult get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path));
    }

    /** POST with a valid CSRF token, fetching one first if needed. */
    public MvcResult post(String path, Object body) throws Exception {
        if (csrfToken == null) fetchCsrf();
        return perform(withBody(MockMvcRequestBuilders.post(path), body).header(csrfHeader, csrfToken));
    }

    /** PATCH with a valid CSRF token, fetching one first if needed. */
    public MvcResult patch(String path, Object body) throws Exception {
        if (csrfToken == null) fetchCsrf();
        return perform(withBody(MockMvcRequestBuilders.patch(path), body).header(csrfHeader, csrfToken));
    }

    /** PATCH without any CSRF token. */
    public MvcResult patchWithoutCsrf(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.patch(path), body));
    }

    public MvcResult put(String path, Object body) throws Exception {
        return call(HttpMethod.PUT, path, body, true);
    }

    public MvcResult delete(String path) throws Exception {
        return call(HttpMethod.DELETE, path, null, true);
    }

    /** Any method, with or without the CSRF token. A {@code null} body sends none. */
    public MvcResult call(HttpMethod method, String path, Object body, boolean withCsrf) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (body != null) builder = withBody(builder, body);
        if (withCsrf) {
            if (csrfToken == null) fetchCsrf();
            builder = builder.header(csrfHeader, csrfToken);
        }
        return perform(builder);
    }

    /** POST without any CSRF token. */
    public MvcResult postWithoutCsrf(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.post(path), body));
    }

    public void fetchCsrf() throws Exception {
        MvcResult result = get("/api/v1/auth/csrf");
        JsonNode node = mapper.readTree(result.getResponse().getContentAsString());
        csrfHeader = node.get("headerName").asText();
        csrfToken = node.get("token").asText();
    }

    public JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    private MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
        if (!jar.isEmpty()) builder.cookie(jar.values().toArray(new Cookie[0]));
        MvcResult result = mvc.perform(builder).andReturn();
        for (Cookie c : result.getResponse().getCookies()) {
            if (c.getMaxAge() == 0) jar.remove(c.getName()); else jar.put(c.getName(), c);
        }
        return result;
    }
}
