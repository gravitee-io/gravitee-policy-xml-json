package io.gravitee.policy.xml2json.utils;

import io.gravitee.common.http.MediaType;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CharsetHelperTest {

    @Test
    public void shouldReturnDefaultCharset_noContentType() {
        Charset charset = CharsetHelper.extractFromContentType(null);
        Assert.assertEquals(StandardCharsets.UTF_8, charset);
    }

    @Test
    public void shouldReturnDefaultCharset_contentType_withoutCharset() {
        Charset charset = CharsetHelper.extractFromContentType(MediaType.APPLICATION_JSON);
        Assert.assertEquals(StandardCharsets.UTF_8, charset);
    }

    @Test
    public void shouldReturnDefaultCharset_contentType_withCharset() {
        Charset charset = CharsetHelper.extractFromContentType(MediaType.APPLICATION_XML + ";charset=ISO-8859-1");
        Assert.assertEquals("ISO-8859-1", charset.name());
    }

    @Test
    public void shouldReturnDefaultCharset_contentType_withCharset_Capitalize() {
        Charset charset = CharsetHelper.extractFromContentType(MediaType.APPLICATION_XML + "; Charset=ISO-8859-1");
        Assert.assertEquals("ISO-8859-1", charset.name());
    }

    @Test
    public void shouldReturnDefaultCharset_contentType_withCharset_Capitalize_quoted() {
        Charset charset = CharsetHelper.extractFromContentType(MediaType.APPLICATION_XML + "; Charset=\"ISO-8859-1\"");
        Assert.assertEquals("ISO-8859-1", charset.name());
    }
}
