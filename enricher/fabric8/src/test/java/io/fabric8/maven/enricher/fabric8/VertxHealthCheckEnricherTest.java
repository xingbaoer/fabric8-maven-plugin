/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.enricher.fabric8;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.maven.enricher.api.EnricherContext;
import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(JMockit.class)
public class VertxHealthCheckEnricherTest {


    @Mocked
    private EnricherContext context;

    @Test
    public void testDefaultConfiguration() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testDefaultConfiguration_Enabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "/ping");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/ping");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/ping");
    }

    @Test
    public void testDifferentPathForLivenessAndReadiness() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "/ping");
        props.put("vertx.health.readiness.path", "/ready");

        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/ping");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTP");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8080);
        assertEquals(probe.getHttpGet().getPath(), "/ready");
    }


    private MavenProject createFakeProject(String config) {
        Model model = new Model();
        model.setArtifactId("some-artifact-id");
        model.setGroupId("some-group-id");
        model.setVersion("1.0");
        Build build = new Build();
        build.setOutputDirectory("target/classes");
        build.setDirectory("target");
        Plugin plugin = new Plugin();
        plugin.setArtifactId("fabric8-maven-plugin");
        plugin.setGroupId("io.fabric8");
        String content = "<configuration><enricher><config><f8-healthcheck-vertx>"
                + config
                + "</f8-healthcheck-vertx></config></enricher></configuration>";
        Xpp3Dom dom;
        try {
            dom = Xpp3DomBuilder.build(new StringReader(content));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        plugin.setConfiguration(dom);
        build.addPlugin(plugin);

        Plugin vmp = new Plugin();
        vmp.setGroupId("io.fabric8");
        vmp.setArtifactId("vertx-maven-plugin");
        build.addPlugin(vmp);

        model.setBuild(build);

        return new MavenProject(model);
    }

    @Test
    public void testWithCustomConfigurationComingFromConf() throws IOException, XmlPullParserException {
        final MavenProject project = createFakeProject("<path>health</path><port>1234</port><scheme>https</scheme>");
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/health");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/health");
    }

    @Test
    public void testWithCustomConfigurationForLivenessAndReadinessComingFromConf() {
        final MavenProject project = createFakeProject(
                "<path>health</path>" +
                        "<port>1234</port>" +
                        "<scheme>https</scheme>" +
                        "<readiness><path>/ready</path></readiness>");
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/health");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getScheme(), "HTTPS");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 1234);
        assertEquals(probe.getHttpGet().getPath(), "/ready");
    }

    @Test
    public void testCustomConfiguration() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "/health");
        props.put("vertx.health.port", " 8081 ");
        props.put("vertx.health.scheme", " https");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNull(probe.getHttpGet().getHost());
        assertThat(probe.getHttpGet().getScheme()).isEqualToIgnoringCase("https");
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8081);
        assertEquals(probe.getHttpGet().getPath(), "/health");

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertThat(probe.getHttpGet().getScheme()).isEqualToIgnoringCase("https");
        assertNull(probe.getHttpGet().getHost());
        assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8081);
        assertEquals(probe.getHttpGet().getPath(), "/health");
    }

    @Test
    public void testWithHttpHeaders() throws IOException, XmlPullParserException {
        final MavenProject project = createFakeProject("<path>health</path>" +
                "<headers><X-Header>X</X-Header><Y-Header>Y</Y-Header></headers>");
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/health");
        assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(probe.getHttpGet().getHttpHeaders()).hasSize(2)
                .contains(new HTTPHeader("X-Header", "X"), new HTTPHeader("Y-Header", "Y"));

        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/health");
        assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(probe.getHttpGet().getHttpHeaders()).hasSize(2)
                .contains(new HTTPHeader("X-Header", "X"), new HTTPHeader("Y-Header", "Y"));
    }

    @Test
    public void testDisabledUsingEmptyPath() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.path", "");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testDisabledUsingNegativePort() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.port", " -1 ");
        props.put("vertx.health.path", " /ping ");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testDisabledUsingInvalidPort() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.port", "not an integer");
        props.put("vertx.health.path", " /ping ");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        try {
            enricher.getLivenessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }

        try {
            enricher.getReadinessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }
    }

    @Test
    public void testDisabledUsingPortName() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.port-name", " health ");
        props.put("vertx.health.path", " /ping ");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertThat(probe.getHttpGet().getPort().getStrVal()).isEqualToIgnoringCase("health");
        probe = enricher.getReadinessProbe();
        assertThat(probe.getHttpGet().getPort().getStrVal()).isEqualToIgnoringCase("health");
    }

    @Test
    public void testDisabledUsingNegativePortUsingConfiguration() {
        final MavenProject project = createFakeProject(
                "<path>/ping</path>" +
                        "<port>-1</port>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testReadinessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.readiness.path", "");
        props.put("vertx.health.path", "/ping");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/ping");
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testReadinessDisabledUsingConfig() {

        final MavenProject project = createFakeProject(
                "<readiness><path></path></readiness>" +
                        "<path>/ping</path>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/ping");
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testLivenessDisabledAndReadinessEnabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.readiness.path", "/ping");
        props.put("vertx.health.path", "");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/ping");

    }

    @Test
    public void testLivenessDisabledAndReadinessEnabledUsingConfig() {
        final MavenProject project = createFakeProject(
                "<readiness><path>/ping</path></readiness>" +
                        "<path></path>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getHttpGet().getPath(), "/ping");
    }

    @Test
    public void testTCPSocketUsingUserProperties() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        props.put("vertx.health.port", "1234");
        props.put("vertx.health.readiness.port", "1235");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1234);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
    }

    @Test
    public void testTCPSocketUsingConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>" +
                        "<liveness><port>1234</port></liveness>" +
                        "<readiness><port>1235</port></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1234);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
    }

    @Test
    public void testTCPSocketUsingUserPropertiesAndPortName() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        props.put("vertx.health.port-name", "health");
        props.put("vertx.health.readiness.port-name", "ready");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getStrVal(), "health");
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getStrVal(), "ready");
    }

    @Test
    public void testTCPSocketUsingConfigAndPortName() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>" +
                        "<liveness><port-name>health</port-name></liveness>" +
                        "<readiness><port-name>ready</port-name></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getStrVal(), "health");
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getStrVal(), "ready");
    }

    @Test
    public void testTCPSocketUsingUserPropertiesLivenessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        props.put("vertx.health.readiness.port", "1235");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
    }

    @Test
    public void testTCPSocketUsingConfigLivenessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>" +
                        "<readiness><port>1235</port></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
    }

    @Test
    public void testTCPSocketUsingUserPropertiesReadinessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        props.put("vertx.health.port", "1235");
        props.put("vertx.health.readiness.port", "0");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
        assertNotNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testTCPSocketUsingConfigReadinessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>" +
                        "<liveness><port>1235</port></liveness>" +
                        "<readiness><port>-1</port></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 1235);
        assertNotNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTCPSocketUsingUserPropertiesIllegal() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        props.put("vertx.health.port", "1235");
        props.put("vertx.health.port-name", "health");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        enricher.getLivenessProbe();
    }

    @Test
    public void testTCPSocketUsingConfigIllegal() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>" +
                        "<liveness><port>1234</port><port-name>foo</port-name></liveness>" +
                        "<readiness><port>1235</port><port-name>foo</port-name></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        try {
            enricher.getLivenessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }

        try {
            enricher.getReadinessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }
    }


    @Test
    public void testTCPSocketUsingUserPropertiesDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "tcp");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testTCPSocketUsingConfigDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>tcp</type>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testExecUsingConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type>" +
                        "<command>" +
                        "<arg>/bin/sh</arg>" +
                        "<arg>-c</arg>" +
                        "<arg>touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600</arg>" +
                        "</command>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertThat(probe.getExec().getCommand()).hasSize(3);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertThat(probe.getExec().getCommand()).hasSize(3);
    }

    @Test
    public void testExecUsingConfigLivenessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type>" +
                        "<readiness><command>" +
                        "<arg>/bin/sh</arg>" +
                        "<arg>-c</arg>" +
                        "<arg>touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600</arg>" +
                        "</command></readiness>" +
                        "<liveness><command/></liveness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertThat(probe.getExec().getCommand()).hasSize(3);
    }

    @Test
    public void testExecUsingConfigReadinessDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type>" +
                        "<liveness><command>" +
                        "<arg>/bin/sh</arg>" +
                        "<arg>-c</arg>" +
                        "<arg>touch /tmp/healthy; sleep 30; rm -rf /tmp/healthy; sleep 600</arg>" +
                        "</command></liveness>" +
                        "<readiness><command/></readiness>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertThat(probe.getExec().getCommand()).hasSize(3);
        assertNotNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testExecUsingUserPropertiesDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "exec");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testExecUsingConfigDisabled() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testUnknownTypeUsingUserProperties() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final Properties props = new Properties();
        props.put("vertx.health.type", "not a valid type");
        new Expectations() {{
            context.getProject().getProperties();
            result = props;
        }};

        try {
            enricher.getLivenessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }

        try {
            enricher.getReadinessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }
    }


    @Test
    public void testUnknownTypeUsingConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>not a valid type</type>");

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        try {
            enricher.getLivenessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }

        try {
            enricher.getReadinessProbe();
            fail("Illegal configuration not detected");
        } catch (Exception e) {
            // OK.
        }
    }

    @Test
    public void testNotApplicableProject() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type><command><arg>ls</arg></command>");
        Build build = project.getBuild();
        Plugin plugin = build.getPluginsAsMap().get("io.fabric8:vertx-maven-plugin");
        build.removePlugin(plugin);
        build.getPluginsAsMap().remove("io.fabric8:vertx-maven-plugin");
        project.setBuild(build);

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNull(probe);
        probe = enricher.getReadinessProbe();
        assertNull(probe);
    }

    @Test
    public void testThatWeCanUSeDifferentTypesForLivenessAndReadiness() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<liveness>" +
                        "   <type>exec</type>" +
                        "   <command><arg>ls</arg></command>" +
                        "</liveness>" +
                        "<readiness>" +
                        "   <path>/ping</path>" +
                        "</readiness>");
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getLivenessProbe();
        assertNotNull(probe);
        assertNotNull(probe.getExec());
        probe = enricher.getReadinessProbe();
        assertNotNull(probe);
        assertNotNull(probe.getHttpGet());
    }

    @Test
    public void testThatGenericUserPropertiesOverrideSpecificConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<liveness>" +
                        "   <type>exec</type>" +
                        "   <command><arg>ls</arg></command>" +
                        "</liveness>" +
                        "<readiness>" +
                        "   <path>/ping</path>" +
                        "</readiness>");
        Properties properties = new Properties();
        properties.put("vertx.health.type", "tcp");
        properties.put("vertx.health.port", "1234");
        project.getModel().setProperties(properties);

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getHttpGet()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getExec()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);
    }

    @Test
    public void testThatGenericUserPropertiesOverrideGenericConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<type>exec</type>" +
                        "   <command><arg>ls</arg></command>"
        );
        Properties properties = new Properties();
        properties.put("vertx.health.type", "tcp");
        properties.put("vertx.health.port", "1234");
        project.getModel().setProperties(properties);

        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getExec()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getExec()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);
    }

    @Test
    public void testThatSpecificUserPropertiesOverrideSpecificConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<liveness>" +
                        "   <path>/ping</path>" +
                        "</liveness>" +
                        "<readiness>" +
                        "   <path>/ping</path>" +
                        "</readiness>");
        Properties properties = new Properties();
        properties.put("vertx.health.readiness.type", "tcp");
        properties.put("vertx.health.readiness.port", "1234");
        properties.put("vertx.health.port", "1235");
        project.getModel().setProperties(properties);
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getHttpGet()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getHttpGet()).isNotNull();
        assertThat(probe.getTcpSocket()).isNull();
        assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(1235);
    }

    @Test
    public void testThatSpecificUserPropertiesOverrideGenericConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<path>/ping</path><type>http</type>");
        Properties properties = new Properties();
        properties.put("vertx.health.readiness.type", "tcp");
        properties.put("vertx.health.readiness.port", "1234");
        properties.put("vertx.health.port", "1235");
        project.getModel().setProperties(properties);
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getHttpGet()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getHttpGet()).isNotNull();
        assertThat(probe.getTcpSocket()).isNull();
        assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(1235);
    }

    @Test
    public void testThatSpecificConfigOverrideGenericConfig() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<liveness>" +
                        "   <path>/live</path>" +
                        "</liveness>" +
                        "<readiness>" +
                        "   <path>/ping</path>" +
                        "   <port-name>ready</port-name>" +
                        "</readiness>" +
                        "<path>/health</path>" +
                        "<port-name>health</port-name>");
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getHttpGet()).isNotNull();
        assertThat(probe.getHttpGet().getPort().getStrVal()).isEqualTo("ready");
        assertThat(probe.getHttpGet().getPath()).isEqualTo("/ping");

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getHttpGet()).isNotNull();
        assertThat(probe.getHttpGet().getPort().getStrVal()).isEqualTo("health");
        assertThat(probe.getHttpGet().getPath()).isEqualTo("/live");
    }

    @Test
    public void testThatSpecificUserPropertiesOverrideGenericUserProperties() {
        VertxHealthCheckEnricher enricher = new VertxHealthCheckEnricher(context);

        final MavenProject project = createFakeProject(
                "<path>/ping</path><type>http</type>");
        Properties properties = new Properties();
        properties.put("vertx.health.readiness.type", "tcp");
        properties.put("vertx.health.readiness.port", "1234");
        properties.put("vertx.health.port", "1235");
        properties.put("vertx.health.liveness.type", "tcp");
        properties.put("vertx.health.liveness.port", "1236");
        project.getModel().setProperties(properties);
        new Expectations() {{
            context.getProject();
            result = project;
        }};

        Probe probe = enricher.getReadinessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getHttpGet()).isNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1234);

        probe = enricher.getLivenessProbe();
        assertThat(probe).isNotNull();
        assertThat(probe.getHttpGet()).isNull();
        assertThat(probe.getTcpSocket()).isNotNull();
        assertThat(probe.getTcpSocket().getPort().getIntVal()).isEqualTo(1236);
    }


}